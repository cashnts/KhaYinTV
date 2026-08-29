package dev.khayin.app.core.sync.library

import dev.khayin.app.domain.model.LibraryDeltaApplyResult
import dev.khayin.app.domain.model.LibraryDeltaEvent
import dev.khayin.app.domain.model.LibrarySnapshotApplyResult
import dev.khayin.app.domain.model.LibrarySyncState
import dev.khayin.app.domain.model.SavedLibraryItem

interface LibrarySyncLocalStore {
    suspend fun getSyncState(profileId: Int): LibrarySyncState

    suspend fun applyRemoteSnapshot(
        profileId: Int,
        remoteItems: Collection<SavedLibraryItem>,
        cursorEventId: Long
    ): LibrarySnapshotApplyResult

    suspend fun applyRemoteDelta(
        profileId: Int,
        events: Collection<LibraryDeltaEvent>
    ): LibraryDeltaApplyResult

    suspend fun queueAllItemsForPush(profileId: Int): LibrarySyncState

    suspend fun acknowledgePush(
        profileId: Int,
        expectedMutationRevision: Long
    ): Boolean
}
