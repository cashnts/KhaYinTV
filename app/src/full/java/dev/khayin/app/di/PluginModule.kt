package dev.khayin.app.di

import dev.khayin.app.core.auth.AuthManager
import dev.khayin.app.core.plugin.PluginManager
import dev.khayin.app.core.plugin.PluginRuntime
import dev.khayin.app.core.plugin.cloudstream.ExternalExtensionLoader
import dev.khayin.app.core.plugin.cloudstream.ExternalExtensionRunner
import dev.khayin.app.core.plugin.cloudstream.ExternalRepoParser
import dev.khayin.app.core.sync.PluginSyncService
import dev.khayin.app.data.local.PluginDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun providePluginRuntime(): PluginRuntime {
        return PluginRuntime()
    }

    @Provides
    @Singleton
    fun providePluginManager(
        dataStore: PluginDataStore,
        runtime: PluginRuntime,
        pluginSyncService: PluginSyncService,
        authManager: AuthManager,
        externalRepoParser: ExternalRepoParser,
        externalExtensionLoader: ExternalExtensionLoader,
        externalExtensionRunner: ExternalExtensionRunner
    ): PluginManager {
        return PluginManager(
            dataStore, runtime, pluginSyncService, authManager,
            externalRepoParser, externalExtensionLoader, externalExtensionRunner
        )
    }
}
