package world.wumbo.donguri.di

import javax.inject.Qualifier

/// A coroutine scope that lives as long as the process — for stores that must
/// finish writing even after the screen that triggered the write is gone.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/// The app's private files directory.
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class FilesDir
