package world.wumbo.donguri.features.popupsettings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import world.wumbo.donguri.config.UserConfig
import world.wumbo.donguri.config.UserConfigRepository
import javax.inject.Inject

@HiltViewModel
class PopupSettingsViewModel @Inject constructor(
    private val repository: UserConfigRepository,
) : ViewModel() {
    val config = repository.config

    fun update(transform: (UserConfig) -> UserConfig) = repository.update(transform)
}
