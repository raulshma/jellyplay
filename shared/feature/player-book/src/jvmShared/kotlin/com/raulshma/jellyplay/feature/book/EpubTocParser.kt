package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.BookTocEntry
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * EPUB table-of-contents extraction, straight from the container (no
 * epub.js/WebView involved — this is the detail screen's never-opened-book
 * fallback and the reader's own TOC never routes through here).
 *
 * Resolution order mirrors the EPUB spec's own:
 * 1. `META-INF/container.xml` → the default rootfile's OPF package document;
 * 2. the manifest item with `properties` containing `nav` (EPUB 3) — a
 *    XHTML `nav epub:type="toc"` document — else the NCX identified by
 *    `spine[@toc]` / the `application/x-dtbncx+xml` media-type (EPUB 2);
 * 3. a flat [BookTocEntry] list, depth-first, with [BookTocEntry.level]
 *    recording nesting so the UI can indent.
 *
 * Every failure mode (missing container, corrupt XML, no ncx/nav) maps to an
 * empty list — never a throw: a book without a readable TOC simply has no
 * Contents, matching the PDF outline parser's fail-empty stance.
 *
 * XML parsing hardens the factory against DTDs and external entities: EPUBs
 * arrive from user servers, and the parser only ever reads labels/hrefs.
 */
internal object EpubTocParser {

    /** Depth-first flatten of a parsed nesting into [BookTocEntry] rows. */
    internal data class TocTree(val label: String, val href: String, val children: List<TocTree>)

    fun parse(file: java.io.File): List<BookTocEntry> {
        return runCatching {
            java.util.zip.ZipFile(file).use { zip ->
                val opfPath = containerRootFile(zip) ?: return@use emptyList()
                val opf = zip.getInputStream(zip.getEntry(opfPath) ?: return@use emptyList())
                    .bufferedReader(Charsets.UTF_8).readText()
                val document = parseXml(opf) ?: return@use emptyList()

                val manifestItems = manifestItems(document)
                val opfDir = opfPath.substringBeforeLast('/', "")

                // Both lookups yield the manifest item's HREF (the map value)
                // — the key is only the manifest id.
                val navHref = manifestItems.entries
                    .firstOrNull { it.value.properties.contains("nav") }?.value?.href
                val ncxHref = spineTocId(document, manifestItems)
                    ?: manifestItems.entries
                        .firstOrNull { it.value.mediaType == "application/x-dtbncx+xml" }?.value?.href

                val trees = when {
                    navHref != null -> parseNav(zip, resolveAgainst(opfDir, navHref))
                    ncxHref != null -> parseNcx(zip, resolveAgainst(opfDir, ncxHref))
                    else -> emptyList()
                }
                trees.flatMap { it.flatten(level = 0) }
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Depth-first flatten; every node IS an entry (self included — the
     * navMap's navPoints are themselves the chapters), nesting as level.
     */
    private fun TocTree.flatten(level: Int): List<BookTocEntry> = buildList {
        add(BookTocEntry(label = label, href = href, page = null, level = level))
        for (child in children) {
            addAll(child.flatten(level + 1))
        }
    }

    private fun containerRootFile(zip: java.util.zip.ZipFile): String? {
        val entry = zip.getEntry("META-INF/container.xml") ?: return null
        val document = parseXml(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
            ?: return null
        val rootfiles = document.getElementsByTagName("rootfile")
        for (i in 0 until rootfiles.length) {
            val element = rootfiles.item(i) as? Element ?: continue
            if (element.getAttribute("full-path").isNotBlank()) {
                return element.getAttribute("full-path")
            }
        }
        return null
    }

    /** Manifest items by id: href + media-type + space-separated `properties`. */
    private fun manifestItems(opf: org.w3c.dom.Document): Map<String, ManifestItem> {
        val items = mutableMapOf<String, ManifestItem>()
        val manifests = opf.getElementsByTagName("manifest")
        for (m in 0 until manifests.length) {
            val manifest = manifests.item(m) as? Element ?: continue
            val children = manifest.getElementsByTagName("item")
            for (i in 0 until children.length) {
                val item = children.item(i) as? Element ?: continue
                val id = item.getAttribute("id")
                if (id.isBlank()) continue
                items[id] = ManifestItem(
                    href = item.getAttribute("href"),
                    mediaType = item.getAttribute("media-type"),
                    properties = item.getAttribute("properties").split(Regex("\\s+")).filter { it.isNotBlank() }.toSet(),
                )
            }
        }
        return items
    }

    private data class ManifestItem(val href: String, val mediaType: String, val properties: Set<String>)

    /** `spine[@toc]` → the manifest href of the NCX (EPUB 2 convention). */
    private fun spineTocId(opf: org.w3c.dom.Document, items: Map<String, ManifestItem>): String? {
        val spines = opf.getElementsByTagName("spine")
        val spine = spines.item(0) as? Element ?: return null
        val tocId = spine.getAttribute("toc").takeIf { it.isNotBlank() } ?: return null
        return items[tocId]?.href
    }

    /** EPUB 3: the `nav` document's `nav[epub\\:type=toc]` ordered list. */
    private fun parseNav(zip: java.util.zip.ZipFile, navPath: String): List<TocTree> {
        val entry = zip.getEntry(navPath) ?: return emptyList()
        val document = parseXml(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
            ?: return emptyList()
        val baseDir = navPath.substringBeforeLast('/', "")
        val navs = document.getElementsByTagName("nav")
        for (i in 0 until navs.length) {
            val nav = navs.item(i) as? Element ?: continue
            val type = nav.getAttribute("epub:type").ifBlank {
                nav.getAttributeNS("http://www.idpf.org/2007/ops", "type")
            }
            if (type.isNotBlank() && type != "toc") continue
            val firstOl = nav.getElementsByTagName("ol").item(0) as? Element ?: continue
            return parseOl(firstOl, baseDir)
        }
        return emptyList()
    }

    /** One `<ol>` → children `<li>`s; an `<li>`'s label is its direct `<a>`, its children any nested `<ol>`. */
    private fun parseOl(ol: Element, baseDir: String): List<TocTree> {
        // Direct children only — getElementsByTagName is recursive and nested
        // <li>s belong to their own <ol> below.
        val listItems = directChildrenByTagName(ol, "li")
        val trees = mutableListOf<TocTree>()
        for (li in listItems) {
            val anchor = directChildByTagName(li, "a") ?: continue
            val label = anchor.textContent.trim()
            val href = anchor.getAttribute("href")
            val nested = directChildByTagName(li, "ol")?.let { parseOl(it, baseDir) } ?: emptyList()
            if (label.isBlank() && nested.isEmpty()) continue
            trees.add(
                TocTree(
                    label = label,
                    href = if (href.isBlank()) nested.firstOrNull()?.href ?: "" else resolveAgainst(baseDir, href),
                    children = nested,
                ),
            )
        }
        return trees
    }

    /** EPUB 2: NCX `navMap` → nested `navPoint`s (`navLabel/text` + `content@src`). */
    private fun parseNcx(zip: java.util.zip.ZipFile, ncxPath: String): List<TocTree> {
        val entry = zip.getEntry(ncxPath) ?: return emptyList()
        val document = parseXml(zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).readText())
            ?: return emptyList()
        val baseDir = ncxPath.substringBeforeLast('/', "")
        val navMaps = document.getElementsByTagName("navMap")
        val navMap = navMaps.item(0) as? Element ?: return emptyList()
        val points = directChildrenByTagName(navMap, "navPoint")
        return points.mapNotNull { parseNavPoint(it, baseDir) }
    }

    private fun parseNavPoint(point: Element, baseDir: String): TocTree? {
        val label = (directChildByTagName(point, "navLabel")?.let { navLabel ->
            directChildByTagName(navLabel, "text")?.textContent
        })?.trim().orEmpty()
        val src = directChildByTagName(point, "content")?.getAttribute("src").orEmpty()
        val children = directChildrenByTagName(point, "navPoint").mapNotNull { parseNavPoint(it, baseDir) }
        if (label.isBlank() && children.isEmpty()) return null
        return TocTree(
            label = label,
            href = if (src.isBlank()) children.firstOrNull()?.href ?: "" else resolveAgainst(baseDir, src),
            children = children,
        )
    }

    /** `getElementsByTagName` is document-wide recursive; this filters to direct element children. */
    private fun directChildrenByTagName(parent: Element, tagName: String): List<Element> {
        val out = mutableListOf<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName == tagName) out.add(child)
        }
        return out
    }

    private fun directChildByTagName(parent: Element, tagName: String): Element? =
        directChildrenByTagName(parent, tagName).firstOrNull()

    /** Resolves a manifest-relative href ("text/ch1.xhtml#frag") against the container doc's dir. */
    internal fun resolveAgainst(baseDir: String, href: String): String = when {
        baseDir.isEmpty() -> href
        href.contains("://") || href.startsWith("/") -> href
        else -> "$baseDir/$href"
    }

    private fun parseXml(text: String): org.w3c.dom.Document? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        factory.newDocumentBuilder().parse(text.byteInputStream())
    }.getOrNull()
}
