package world.wumbo.donguri.bbs.store

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import world.wumbo.donguri.bbs.model.NgKind
import world.wumbo.donguri.bbs.model.NgRule
import world.wumbo.donguri.bbs.model.Post
import world.wumbo.donguri.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

/// Stores the user's NG (あぼーん) rules and decides which posts they hide.
///
/// Filtered posts are never removed from the thread's post list — `>>N` links
/// and `Post.replies` are both plain indices into it, so dropping elements
/// would silently misnumber every later reply. The thread screen renders a
/// placeholder row in place of a hidden post instead.
@Singleton
class NgFilterStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _rules = MutableStateFlow<List<NgRule>>(emptyList())
    val rules: StateFlow<List<NgRule>> = _rules.asStateFlow()

    /// Reads are served from memory, so the stored rules have to be in memory
    /// before a write can be based on them — otherwise a rule added during the
    /// first moments after launch would persist a list with only itself in it,
    /// dropping everything the user had saved.
    private val loaded = CompletableDeferred<Unit>()

    init {
        scope.launch {
            val stored = dataStore.data.first()[KEY]
            if (stored != null) {
                _rules.value = runCatching { json.decodeFromString<List<NgRule>>(stored) }
                    .getOrDefault(emptyList())
            }
            loaded.complete(Unit)
        }
    }

    fun add(kind: NgKind, pattern: String) {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return
        mutate { rules ->
            if (rules.any { it.kind == kind && it.pattern == trimmed }) {
                rules
            } else {
                rules + NgRule(kind = kind, pattern = trimmed)
            }
        }
    }

    fun remove(rule: NgRule) = mutate { rules -> rules.filterNot { it.id == rule.id } }

    fun remove(kind: NgKind, pattern: String) =
        mutate { rules -> rules.filterNot { it.kind == kind && it.pattern == pattern } }

    fun contains(kind: NgKind, pattern: String): Boolean =
        _rules.value.any { it.kind == kind && it.pattern == pattern }

    private fun mutate(transform: (List<NgRule>) -> List<NgRule>) {
        scope.launch {
            loaded.await()
            val updated = transform(_rules.value)
            if (updated == _rules.value) return@launch
            _rules.value = updated
            dataStore.edit { it[KEY] = json.encodeToString(updated) }
        }
    }

    companion object {
        private val KEY = stringPreferencesKey("donguri.ngRules")
        private val json = Json { ignoreUnknownKeys = true }

        /// Case-insensitive substring match, which is what 5ch clients do by default.
        fun hides(post: Post, rules: List<NgRule>): Boolean {
            if (rules.isEmpty()) return false
            return rules.any { rule ->
                when (rule.kind) {
                    NgKind.WORD -> post.text.contains(rule.pattern, ignoreCase = true)
                    NgKind.ID -> post.id?.contains(rule.pattern, ignoreCase = true) == true
                    NgKind.NAME ->
                        post.name.contains(rule.pattern, ignoreCase = true) ||
                            post.trip?.contains(rule.pattern, ignoreCase = true) == true
                }
            }
        }
    }
}
