package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.HomeSectionsResult

/**
 * Cheap structural fingerprint of a [HomeSectionsResult] for
 * [HomeSectionsSnapshotStore.persist]'s dedup window:
 * ONE string per fetch (indexed loops, no per-item object allocations),
 * covering what a foreground home refresh can change — failed section types,
 * per-section header identity (id/title/type/libraryId/collectionType/seed
 * id) and, per item, the id plus the user-data fields the home rows render
 * (playback position, played + favorite flags). Deliberately NOT the full
 * payload: metadata-only changes (overview text, ratings, …) do not alter
 * it, which is exactly the "equal fingerprint does not imply a
 * byte-identical encode" caveat documented at the call site. Hashed to a
 * single Int for retention in the in-memory dedup record. Order-sensitive:
 * a re-ordered section or item list produces a different fingerprint, so
 * re-orderings still take the exact encode+compare path. Pinned by
 * `HomeSnapshotFingerprintTest` (jvmTest) — the field set here IS the
 * dedup-window contract; extend it when the home rows start rendering
 * another user-data field.
 *
 * Plugin rows (PLUGIN_ROW) fingerprint their [com.raulshma.jellyplay.core.model.JellyPlayRowEntry]
 * list the same way — header payload aside, the entries render exactly the
 * fields the media items do (title+year tiles and resolved cards with
 * position/played/favorite), so an un fingerprinted change would let a
 * watched flip or a re-curated list sit stale for a full dedup window.
 */
internal object HomeSnapshotFingerprint {

    fun of(result: HomeSectionsResult): Int {
        val sb = StringBuilder()
        for (failedType in result.failedSectionTypes) {
            sb.append(failedType.name)
            sb.append('|')
        }
        sb.append('#')
        val sections = result.sections
        for (s in sections.indices) {
            val section = sections[s]
            sb.append(section.id)
            sb.append('|')
            sb.append(section.title)
            sb.append('|')
            sb.append(section.type.name)
            sb.append('|')
            sb.append(section.libraryId)
            sb.append('|')
            sb.append(section.collectionType)
            sb.append('|')
            sb.append(section.seedItem?.id)
            sb.append('|')
            val sectionItems = section.items
            for (i in sectionItems.indices) {
                val item = sectionItems[i]
                sb.append(item.id)
                sb.append('|')
                sb.append(item.playbackPositionTicks)
                sb.append('|')
                sb.append(item.isPlayed)
                sb.append('|')
                sb.append(item.isFavorite)
                sb.append(';')
            }
            // Plugin rows: the curated entry list IS the row's content —
            // order, fallback-tile text and the resolved cards' user-data
            // fields all render, so all fingerprint.
            val rowEntries = section.jellyPlayRowEntries
            for (i in rowEntries.indices) {
                val entry = rowEntries[i]
                sb.append(entry.title)
                sb.append('|')
                sb.append(entry.year)
                sb.append('|')
                val local = entry.localItem
                sb.append(local?.id)
                sb.append('|')
                sb.append(local?.playbackPositionTicks)
                sb.append('|')
                sb.append(local?.isPlayed)
                sb.append('|')
                sb.append(local?.isFavorite)
                sb.append(';')
            }
            sb.append('#')
        }
        // toString() first: StringBuilder's own hashCode is identity-based.
        return sb.toString().hashCode()
    }
}
