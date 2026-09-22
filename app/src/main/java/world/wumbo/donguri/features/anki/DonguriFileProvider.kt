package world.wumbo.donguri.features.anki

import androidx.core.content.FileProvider
import world.wumbo.donguri.R

/// Media handed to AnkiDroid is passed by content URI, which needs a provider
/// of our own rather than the shared androidx one.
class DonguriFileProvider : FileProvider(R.xml.file_paths)
