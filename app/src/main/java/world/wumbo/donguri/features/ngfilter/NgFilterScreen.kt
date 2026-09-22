package world.wumbo.donguri.features.ngfilter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import world.wumbo.donguri.R
import world.wumbo.donguri.bbs.model.NgKind
import world.wumbo.donguri.ui.Chip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NgFilterScreen(
    onBack: () -> Unit,
    viewModel: NgFilterViewModel = hiltViewModel(),
) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var kind by remember { mutableStateOf(NgKind.WORD) }
    var pattern by remember { mutableStateOf("") }

    fun submit() {
        viewModel.add(kind, pattern)
        pattern = ""
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ng_filter)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NgKind.entries.forEach { entry ->
                    FilterChip(
                        selected = kind == entry,
                        onClick = { kind = entry },
                        label = { Text(stringResource(entry.labelRes())) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = pattern,
                    onValueChange = { pattern = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ng_pattern_placeholder)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { submit() }, enabled = pattern.isNotBlank()) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ng_add))
                }
            }

            HorizontalDivider(Modifier.padding(top = 8.dp))

            LazyColumn(Modifier.fillMaxSize()) {
                items(rules, key = { it.id }) { rule ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Chip(text = stringResource(rule.kind.labelRes()), filled = false)
                        Text(
                            text = rule.pattern,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.remove(rule) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.dictionary_delete))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

private fun NgKind.labelRes(): Int = when (this) {
    NgKind.WORD -> R.string.ng_kind_word
    NgKind.ID -> R.string.ng_kind_id
    NgKind.NAME -> R.string.ng_kind_name
}
