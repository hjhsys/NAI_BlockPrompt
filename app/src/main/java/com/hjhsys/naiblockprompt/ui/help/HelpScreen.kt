package com.hjhsys.naiblockprompt.ui.help

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.BuildConfig
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar

private data class HelpTopic(@param:StringRes val title: Int, @param:StringRes val body: Int, val quick: Boolean = false)

@Composable
fun HelpScreen(onBack: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val topics = listOf(
        HelpTopic(R.string.help_getting_started, R.string.help_getting_started_body),
        HelpTopic(R.string.help_previous_work, R.string.help_previous_work_body, true),
        HelpTopic(R.string.help_novelai_setup, R.string.help_novelai_setup_body, true),
        HelpTopic(R.string.help_prompt_formatter, R.string.help_prompt_formatter_body),
        HelpTopic(R.string.help_color_helper, R.string.help_color_helper_body),
        HelpTopic(R.string.help_generation, R.string.help_generation_body),
        HelpTopic(R.string.help_library_history, R.string.help_library_history_body),
        HelpTopic(R.string.help_saving_images, R.string.help_saving_images_body, true),
        HelpTopic(R.string.backup_and_restore, R.string.help_backup_body, true),
        HelpTopic(R.string.help_troubleshooting, R.string.help_troubleshooting_body),
    )
    val localized = topics.map { it to (stringResource(it.title) + " " + stringResource(it.body)) }
    val filtered = localized.filter { query.isBlank() || it.second.contains(query, ignoreCase = true) }.map { it.first }
    Column(Modifier.fillMaxSize()) {
        AppTitleBar(R.string.help_title, onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            placeholder = { Text(stringResource(R.string.search_help)) },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
        )
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (query.isBlank()) {
                item { Text(stringResource(R.string.quick_help), style = MaterialTheme.typography.titleMedium) }
                items(topics.filter { it.quick }) { HelpTopicRow(it) }
                item { Text(stringResource(R.string.help_topics), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(topics.filterNot { it.quick }) { HelpTopicRow(it) }
            } else {
                items(filtered) { HelpTopicRow(it) }
                if (filtered.isEmpty()) item { Text(stringResource(R.string.help_no_results)) }
            }
            item {
                HorizontalDivider(Modifier.padding(top = 8.dp))
                Text(stringResource(R.string.help_about), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME))
                Text(stringResource(R.string.whats_new_placeholder), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun HelpTopicRow(topic: HelpTopic) {
    var expanded by rememberSaveable(topic.title) { mutableStateOf(false) }
    ElevatedCard(Modifier.fillMaxWidth().clickable { expanded = !expanded }) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(topic.title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null)
            }
            if (expanded) Text(stringResource(topic.body), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
