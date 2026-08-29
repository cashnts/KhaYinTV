package dev.khayin.app.core.di

import dev.khayin.app.data.simkl.AndroidSimklAuthStorage
import dev.khayin.app.data.simkl.AndroidSimklSyncStorage
import dev.khayin.app.data.simkl.SimklAuthStorage
import dev.khayin.app.data.simkl.SimklApiSyncRemote
import dev.khayin.app.data.simkl.SimklSyncRemote
import dev.khayin.app.data.simkl.SimklSyncStorage
import dev.khayin.app.core.tracking.TrackingLibraryProvider
import dev.khayin.app.core.tracking.TrackingProvider
import dev.khayin.app.data.repository.TraktTrackingLibraryProvider
import dev.khayin.app.data.repository.TraktTrackingProvider
import dev.khayin.app.data.simkl.SimklLibraryService
import dev.khayin.app.core.tracking.TrackingHistoryWriter
import dev.khayin.app.core.tracking.TrackingProgressProvider
import dev.khayin.app.data.repository.TraktTrackingHistoryWriter
import dev.khayin.app.data.repository.TraktTrackingProgressProvider
import dev.khayin.app.data.simkl.SimklTrackingHistoryWriter
import dev.khayin.app.data.simkl.SimklTrackingProgressProvider
import dev.khayin.app.data.simkl.SimklTrackingProvider
import dev.khayin.app.core.profile.ProfileScopedCredentialStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {
    @Binds
    @Singleton
    abstract fun bindSimklAuthStorage(storage: AndroidSimklAuthStorage): SimklAuthStorage

    @Binds
    @IntoSet
    abstract fun bindSimklProfileScopedCredentialStore(
        storage: AndroidSimklAuthStorage
    ): ProfileScopedCredentialStore

    @Binds
    @Singleton
    abstract fun bindSimklSyncStorage(storage: AndroidSimklSyncStorage): SimklSyncStorage

    @Binds
    @Singleton
    abstract fun bindSimklSyncRemote(remote: SimklApiSyncRemote): SimklSyncRemote

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingLibraryProvider(
        provider: TraktTrackingLibraryProvider
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingLibraryProvider(
        provider: SimklLibraryService
    ): TrackingLibraryProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProgressProvider(
        provider: TraktTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProgressProvider(
        provider: SimklTrackingProgressProvider
    ): TrackingProgressProvider

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingHistoryWriter(
        writer: TraktTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingHistoryWriter(
        writer: SimklTrackingHistoryWriter
    ): TrackingHistoryWriter

    @Binds
    @IntoSet
    abstract fun bindTraktTrackingProvider(
        provider: TraktTrackingProvider
    ): TrackingProvider

    @Binds
    @IntoSet
    abstract fun bindSimklTrackingProvider(
        provider: SimklTrackingProvider
    ): TrackingProvider
}
