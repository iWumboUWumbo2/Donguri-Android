package world.wumbo.donguri.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import world.wumbo.donguri.dictionary.DictionaryNativeBridge
import world.wumbo.donguri.dictionary.DictionaryRemoteDataSource
import world.wumbo.donguri.dictionary.HoshiDictionaryNativeBridge
import world.wumbo.donguri.dictionary.UrlDictionaryRemoteDataSource
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DictionaryProvidesModule {
    @Provides
    @Singleton
    @FilesDir
    fun provideFilesDir(@ApplicationContext context: Context): File = context.filesDir
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryBindingsModule {
    @Binds
    @Singleton
    abstract fun bindNativeBridge(impl: HoshiDictionaryNativeBridge): DictionaryNativeBridge

    @Binds
    @Singleton
    abstract fun bindRemoteDataSource(impl: UrlDictionaryRemoteDataSource): DictionaryRemoteDataSource
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AnkiBindingsModule {
    @Binds
    @Singleton
    abstract fun bindAnkiContentApi(
        impl: world.wumbo.donguri.features.anki.AndroidAnkiContentApi,
    ): world.wumbo.donguri.features.anki.AnkiContentApi
}
