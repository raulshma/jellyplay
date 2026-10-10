package com.raulshma.jellyplay.core.data.repository

/**
 * The book-reader bookmark face of the jellyfin-plugin-jellyplay companion
 * plugin (ADR 0010; wire spec: the plugin repo's docs/CONTRACT.md "User data"
 * section — `bookmarks` feature). The local `book_bookmarks` Room store stays
 * the source of truth (ADR 0003); this repository is the ONE place local
 * ticks translate to the plugin's opaque seconds, and the merge rules live
 * here rather than in the reader:
 *
 *  - [pushBookmark] — after a LOCAL create, mirror the row to the server
 *    (`JellyPlayUserDataRoutes.upsertBookmark`).
 *    A server row already sitting at the same position is overwritten in
 *    place only when the local row is NEWER (local `createdAt` vs server
 *    `updatedAt`); an equal-or-newer server row wins and the push is skipped.
 *  - [pushBookmarkDeleted] — after a LOCAL delete, remove the server twin
 *    (matched by position, the same join key as every other operation).
 *  - [pullBookmarks] — once per book open: server rows merge into the local
 *    store. Unknown positions are adopted as new local rows (SERVER-WINS for
 *    adoption); a known position is replaced only when the server row is
 *    strictly newer (`updatedAt` > local `createdAt`), so a just-created
 *    offline mark is never clobbered by a stale mirror of itself.
 *
 * Every operation gates on the plugin being AVAILABLE with the `bookmarks`
 * feature, and every failure is a silent skip — the local marks keep working
 * standalone (ADR 0003's local-first contract is untouched; sync is additive
 * light-up per ADR 0010). None of these functions throw except cancellation.
 */
interface BookmarksSyncRepository {

    /**
     * Merges the plugin's bookmarks for [itemId] into the local Room store.
     * Returns without any call when the plugin gate is closed.
     */
    suspend fun pullBookmarks(itemId: String)

    /**
     * Mirrors the locally created bookmark at [positionTicks] (with its
     * [chapterLabel]) to the plugin. Callers fire this after the local write
     * succeeded; a row that vanished locally before this ran is simply not
     * pushed.
     */
    suspend fun pushBookmark(itemId: String, positionTicks: Long, chapterLabel: String)

    /**
     * Propagates a LOCAL delete: removes the server row at [positionTicks],
     * if one exists (it may belong to another device — the delete wins).
     */
    suspend fun pushBookmarkDeleted(itemId: String, positionTicks: Long)
}

/**
 * Null-object implementation: the book reader ViewModel's constructor default
 * so tests and graphs without the plugin cluster compile unchanged (the
 * [NoopBookTocCacheRepository] pattern).
 */
class NoopBookmarksSyncRepository : BookmarksSyncRepository {
    override suspend fun pullBookmarks(itemId: String) = Unit
    override suspend fun pushBookmark(itemId: String, positionTicks: Long, chapterLabel: String) = Unit
    override suspend fun pushBookmarkDeleted(itemId: String, positionTicks: Long) = Unit
}
