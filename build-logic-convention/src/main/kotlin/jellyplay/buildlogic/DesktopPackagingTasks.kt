package jellyplay.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest

// (GeneratePackagingIconsTask — the one task that drew the committed
// packaging/icons/* placeholder assets, with its java.awt/ImageIO imports —
// lived in the module script beside these until it was deleted: its outputs
// are committed real files, ordinary builds never regenerate them, and
// PackagingIconAssetsTest validates the bytes.)

/**
 * Fetches the bundled Windows libmpv (out-of-the-box desktop playback) from
 * the pinned mpv-winbuild release, verifies sha256, and mirrors the dlls +
 * license notice into the app-resources packaging dir. Moved verbatim from
 * :apps:desktop's build script into this convention build; task NAME and
 * behavior are unchanged (CI release lanes and the README reference
 * `fetchBundledLibmpv` by name).
 *
 * Desktop video/audio playback goes through libmpv via JNA, and requiring
 * every user to install mpv by hand (or set MPV_LIBRARY) is a non-starter —
 * a missing dll used to surface as a silent black player screen. The
 * Windows dll is fetched at build time and shipped inside the packaged app
 * image (the resourcesDir output is the compose plugin's
 * appResourcesRootDir input), so playback works with zero user setup.
 *
 * Three consumers share the one copy in the gitignored tools/mpv/:
 *  * the `run` task points jna.library.path there for dev runs,
 *  * the `test` task does the same for the real-engine suites,
 *  * the packaging tasks copy it (plus the third-party notice) into the
 *    app-resources dir that jpackage installs next to the jars — Main.kt
 *    reads compose.application.resources.dir and re-points JNA at it.
 *
 * Extraction needs a 7-Zip binary: every distributor ships the dev package
 * as .7z (LZMA2+BCJ2, beyond pure-Java readers), while Windows dev boxes and
 * GitHub's windows runners all carry one. Machines without it keep the
 * pre-existing manual escape hatch — drop libmpv-2.dll into tools/mpv/.
 */
abstract class FetchBundledLibmpvTask : DefaultTask() {
    @get:Input abstract val devArchiveUrl: Property<String>
    @get:Input abstract val devArchiveSha256: Property<String>
    @get:Input abstract val playerArchiveUrl: Property<String>
    @get:Input abstract val playerArchiveSha256: Property<String>
    @get:InputFile abstract val noticeFile: RegularFileProperty
    @get:OutputFile abstract val dllFile: RegularFileProperty
    @get:OutputDirectory abstract val resourcesDir: DirectoryProperty

    @TaskAction
    fun fetch() {
        val dll = dllFile.get().asFile
        if (dll.isFile && dll.length() > 0L) {
            // Manual drop-in (the pre-bundling workflow) still wins — never
            // overwrite what a developer placed there on purpose.
            syncResources(dll)
            return
        }
        if (!System.getProperty("os.name", "").lowercase().startsWith("windows")) {
            // The bundle only carries the Windows dll; Linux/macOS desktop
            // builds load the system libmpv.so/.dylib through JNA's normal
            // search path and a PE dll could never load there anyway.
            logger.lifecycle("Bundled libmpv is Windows-only; nothing to fetch on this OS")
            return
        }

        val sevenZip = findSevenZip() ?: throw GradleException(
            "7-Zip not found (libmpv ships as .7z). Install 7-Zip from https://7-zip.org, " +
                "or manually place libmpv-2.dll into ${dll.parentFile} (see README §Desktop playback).",
        )

        // Download+verify as ONE retryable unit: this machine's network stack
        // intermittently delivers corrupt bytes through both the JVM client
        // and curl (observed as a different wrong sha256 per attempt), so a
        // single-shot download that passes a length check cannot be trusted —
        // only the pinned hash can.
        fun fetchArchive(url: String, expectedSha256: String, targetName: String): File {
            val archive = File(temporaryDir, targetName)
            for (attempt in 1..3) {
                archive.delete()
                archive.parentFile.mkdirs()
                logger.lifecycle("Downloading {} (attempt {})", url, attempt)
                download(archive, url)
                if (archive.isFile && archive.length() > 0L && sha256Hex(archive) == expectedSha256) {
                    return archive
                }
                logger.lifecycle("Download attempt {} produced bad bytes; retrying", attempt)
            }
            archive.delete()
            throw GradleException(
                "Could not obtain $url after 3 attempts (expected sha256 $expectedSha256) " +
                    "— flaky network or a changed upstream artifact",
            )
        }

        fun extract(archive: File, vararg entryNames: String): List<File> {
            val extractDir = File(temporaryDir, "extracted-${archive.nameWithoutExtension}")
            extractDir.deleteRecursively()
            extractDir.mkdirs()
            val extraction = ProcessBuilder(
                sevenZip.absolutePath, "e", "-y",
                "-o${extractDir.absolutePath}", archive.absolutePath, *entryNames,
            ).redirectErrorStream(true).start()
            val extractionLog = extraction.inputStream.bufferedReader().readText()
            val extracted = entryNames.map { File(extractDir, it) }
            if (extraction.waitFor() != 0 || extracted.any { !it.isFile }) {
                throw GradleException(
                    "7-Zip could not extract ${entryNames.toList()} from ${archive.name} " +
                        "(exit ${extraction.exitValue()}):\n$extractionLog",
                )
            }
            return extracted
        }

        val devArchive = fetchArchive(devArchiveUrl.get(), devArchiveSha256.get(), "mpv-dev.7z")
        val playerArchive = fetchArchive(playerArchiveUrl.get(), playerArchiveSha256.get(), "mpv-player.7z")
        val (libmpvDll) = extract(devArchive, "libmpv-2.dll")
        val (luaDll) = extract(playerArchive, "lua51.dll")

        dll.parentFile.mkdirs()
        libmpvDll.copyTo(dll, overwrite = true)
        luaDll.copyTo(File(dll.parentFile, "lua51.dll"), overwrite = true)
        logger.lifecycle(
            "Bundled libmpv-2.dll ({} MiB) + lua51.dll ready at {}",
            dll.length() / (1024 * 1024),
            dll.parentFile,
        )
        syncResources(dll)
    }

    /** Mirrors the dlls + license notice into the app-resources packaging dir. */
    private fun syncResources(dll: File) {
        val resources = resourcesDir.get().asFile.apply { mkdirs() }
        dll.copyTo(File(resources, "libmpv-2.dll"), overwrite = true)
        File(dll.parentFile, "lua51.dll").takeIf { it.isFile }
            ?.copyTo(File(resources, "lua51.dll"), overwrite = true)
        noticeFile.get().asFile.copyTo(File(resources, "THIRD-PARTY-NOTICE-mpv.txt"), overwrite = true)
    }

    /**
     * Two-step download. The JVM's HttpClient first (with redirects — GitHub
     * release assets 302 to a CDN host, and the JVM default of NEVER saves
     * the empty 3xx body); when the JVM stack is blocked outright —
     * per-process firewalls that exempt native binaries only, TLS
     * interception scoped the same way — curl takes over. curl.exe ships
     * with Windows 10+ and every CI runner, so the fallback is dependable.
     */
    private fun download(target: File, url: String) {
        try {
            val response = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()
                .send(
                    HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofFile(target.toPath()),
                )
            // ofFile writes the body whatever the status is — an HTTP error
            // page would otherwise pass a non-empty check and fail later as
            // a sha256 mismatch with no hint of the real cause.
            if (response.statusCode() == 200 && target.isFile && target.length() > 0L) return
            logger.lifecycle(
                "JVM HttpClient got status {} ({} bytes); falling back to curl",
                response.statusCode(),
                target.length(),
            )
        } catch (e: Exception) {
            logger.lifecycle("JVM HttpClient failed ({}); falling back to curl", e.message)
        }
        target.delete()
        val curl = findOnPath("curl")
            ?: throw GradleException("Neither the JVM HttpClient nor curl could fetch $url")
        logger.lifecycle("Downloading via curl at {}", curl.absolutePath)
        val fetch = ProcessBuilder(
            curl.absolutePath, "-fsSL", "-o", target.absolutePath, url,
        ).redirectErrorStream(true).start()
        val fetchLog = fetch.inputStream.bufferedReader().readText()
        if (fetch.waitFor() != 0) {
            target.delete()
            throw GradleException("curl could not fetch $url (exit ${fetch.exitValue()}):\n$fetchLog")
        }
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** First executable named [names] on PATH, or null. */
    private fun findOnPath(vararg names: String): File? {
        for (name in names) {
            val lookup = ProcessBuilder("where", name).redirectErrorStream(true).start()
            val hits = lookup.inputStream.bufferedReader().readText()
            if (lookup.waitFor() == 0) {
                hits.lineSequence()
                    .mapNotNull { it.trim().takeIf { it.isNotEmpty() } }
                    .map(::File)
                    .firstOrNull { it.isFile }
                    ?.let { return it }
            }
        }
        return null
    }

    private fun findSevenZip(): File? =
        findOnPath("7z", "7za")
            ?: listOf(
                File("C:\\Program Files\\7-Zip\\7z.exe"),
                File("C:\\Program Files (x86)\\7-Zip\\7z.exe"),
            ).firstOrNull { it.isFile }
}

/**
 * Writes the classpath desktop-build.properties (versionName + release
 * channel) the About screen's DesktopAppMetaProvider reads. Moved verbatim
 * from :apps:desktop's build script; task NAME and output path are
 * unchanged (the release.yml / kmp-release.yml comments reference the task
 * by name, and the srcDir wiring in [DesktopPackagingPlugin] feeds the same
 * build/generated/build-info directory into processResources).
 */
abstract class WriteDesktopBuildInfoTask : DefaultTask() {
    @get:Input abstract val versionName: Property<String>
    @get:Input abstract val channel: Property<String>
    @get:OutputFile abstract val outputFile: RegularFileProperty

    @TaskAction
    fun write() {
        val file = outputFile.get().asFile
        file.parentFile.mkdirs()
        file.writeText(
            """
            # Generated by WriteDesktopBuildInfoTask; do not edit. Release lanes
            # pass -PjellyplayVersionName (e.g. 0.11.0-alpha.1).
            versionName=${versionName.get()}
            # "release" when -PjellyplayVersion was passed (CI lane), else "dev".
            channel=${channel.get()}
            """.trimIndent() + "\n",
        )
    }
}
