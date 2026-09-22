package world.wumbo.donguri.features.ngfilter

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import world.wumbo.donguri.bbs.model.NgKind
import world.wumbo.donguri.bbs.model.NgRule
import world.wumbo.donguri.bbs.store.NgFilterStore
import javax.inject.Inject

@HiltViewModel
class NgFilterViewModel @Inject constructor(
    private val store: NgFilterStore,
) : ViewModel() {
    val rules = store.rules

    fun add(kind: NgKind, pattern: String) = store.add(kind, pattern)

    fun remove(rule: NgRule) = store.remove(rule)
}
