package dev.khayin.app.core.di

import dev.khayin.app.data.repository.AddonRepositoryImpl
import dev.khayin.app.data.repository.CatalogRepositoryImpl
import dev.khayin.app.data.repository.LibraryRepositoryImpl
import dev.khayin.app.data.repository.MetaRepositoryImpl
import dev.khayin.app.data.repository.StreamRepositoryImpl
import dev.khayin.app.data.repository.SubtitleRepositoryImpl
import dev.khayin.app.data.repository.SyncRepositoryImpl
import dev.khayin.app.data.repository.WatchProgressRepositoryImpl
import dev.khayin.app.domain.repository.AddonRepository
import dev.khayin.app.domain.repository.CatalogRepository
import dev.khayin.app.domain.repository.LibraryRepository
import dev.khayin.app.domain.repository.MetaRepository
import dev.khayin.app.domain.repository.StreamRepository
import dev.khayin.app.domain.repository.SubtitleRepository
import dev.khayin.app.domain.repository.SyncRepository
import dev.khayin.app.domain.repository.WatchProgressRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAddonRepository(impl: AddonRepositoryImpl): AddonRepository

    @Binds
    @Singleton
    abstract fun bindCatalogRepository(impl: CatalogRepositoryImpl): CatalogRepository

    @Binds
    @Singleton
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    @Singleton
    abstract fun bindMetaRepository(impl: MetaRepositoryImpl): MetaRepository

    @Binds
    @Singleton
    abstract fun bindStreamRepository(impl: StreamRepositoryImpl): StreamRepository

    @Binds
    @Singleton
    abstract fun bindSubtitleRepository(impl: SubtitleRepositoryImpl): SubtitleRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: SyncRepositoryImpl): SyncRepository

    @Binds
    @Singleton
    abstract fun bindWatchProgressRepository(impl: WatchProgressRepositoryImpl): WatchProgressRepository
}
