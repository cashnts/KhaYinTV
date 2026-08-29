package dev.khayin.app.core.di

import dev.khayin.app.core.sync.library.LibrarySyncLocalStore
import dev.khayin.app.core.sync.library.LibrarySyncRemoteDataSource
import dev.khayin.app.data.local.LibraryPreferences
import dev.khayin.app.data.remote.supabase.SupabaseLibrarySyncRemoteDataSource
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibrarySyncModule {
    @Binds
    @Singleton
    abstract fun bindLibrarySyncLocalStore(
        implementation: LibraryPreferences
    ): LibrarySyncLocalStore

    @Binds
    @Singleton
    abstract fun bindLibrarySyncRemoteDataSource(
        implementation: SupabaseLibrarySyncRemoteDataSource
    ): LibrarySyncRemoteDataSource
}
