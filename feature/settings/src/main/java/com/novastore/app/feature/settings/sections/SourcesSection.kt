package com.novastore.app.feature.settings.sections

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.novastore.app.core.model.ProviderType
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.model.SourceUrls
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.components.SourceBadge
import com.novastore.app.domain.source.SourcePreview
import com.novastore.app.feature.settings.SettingsUiState
import com.novastore.app.feature.settings.SettingsViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Sources — the Nova anonymous access tiers. The F-Droid repositories
 * manager (add/edit/remove/reorder/back-up) expands in place.
 */
@Composable
internal fun SourcesSection(
    state: SettingsUiState,
    show: (String) -> Boolean,
    viewModel: SettingsViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (RepositoryConfig) -> Unit,
) {
    var fdroidExpanded by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    SettingsSection(title = stringResource(UiR.string.settings_sources)) {
        if (show("add_repository")) {
            SourceNavRow(
                icon = Icons.Filled.Add,
                label = stringResource(UiR.string.settings_add_repository),
                description = stringResource(UiR.string.settings_add_repository_desc),
                onClick = onAdd,
            )
            RowDivider()
        }
        if (show("anonymous_play")) {
            SwitchRow(description = stringResource(UiR.string.sources_play_anonymous_desc),
                icon = Icons.Filled.CloudDownload,
                label = stringResource(UiR.string.sources_play_anonymous),
                checked = state.anonymousPlayEnabled,
                onChecked = viewModel::setAnonymousPlayEnabled,
            )
        }
        if (show("web_catalog")) {
            SwitchRow(description = stringResource(UiR.string.sources_web_catalog_desc),
                icon = Icons.Filled.Language,
                label = stringResource(UiR.string.sources_web_catalog),
                checked = state.playWebCatalogEnabled,
                onChecked = viewModel::setPlayWebCatalogEnabled,
            )
        }
        if (show("github")) {
            SwitchRow(description = stringResource(UiR.string.sources_github_desc),
                icon = Icons.Filled.Code,
                label = stringResource(UiR.string.sources_github),
                checked = state.githubCatalogEnabled,
                onChecked = viewModel::setGithubCatalogEnabled,
            )
        }
        if (show("gitlab")) {
            SwitchRow(description = stringResource(UiR.string.sources_gitlab_desc),
                icon = Icons.Filled.Code,
                label = stringResource(UiR.string.sources_gitlab),
                checked = state.gitlabCatalogEnabled,
                onChecked = viewModel::setGitlabCatalogEnabled,
            )
        }
        if (show("fdroid_repos")) {
            RowDivider()
            SourceNavRow(
                icon = Icons.Filled.Android,
                label = stringResource(UiR.string.sources_fdroid_repos),
                description = stringResource(UiR.string.sources_fdroid_repos_desc),
                expanded = fdroidExpanded,
                onClick = { fdroidExpanded = !fdroidExpanded },
            )
            if (fdroidExpanded) {
                RepositoriesManager(
                    state = state,
                    onToggle = { id, enabled -> viewModel.setRepositoryEnabled(id, enabled) },
                    onRefresh = { viewModel.refreshRepository(it) },
                    onRemove = { viewModel.removeRepository(it) },
                    onEdit = onEdit,
                    onReorder = { viewModel.reorderRepositories(it) },
                    onAdd = onAdd,
                    onRefreshAll = { viewModel.refreshRepositories() },
                    onExport = onExport,
                    onImport = onImport,
                )
            }
        }
        if (show("open_links")) {
            RowDivider()
            // Store links (Play, market://, F-Droid) → Nova Store by default.
            SourceNavRow(
                icon = Icons.Filled.Link,
                label = stringResource(UiR.string.settings_open_links),
                description = stringResource(UiR.string.settings_open_links_desc),
                expanded = false,
                onClick = {
                    val pkg = Uri.parse("package:" + context.packageName)
                    val byDefault = if (android.os.Build.VERSION.SDK_INT >= 31) {
                        Intent(android.provider.Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS, pkg)
                    } else {
                        Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg)
                    }
                    runCatching { context.startActivity(byDefault) }.onFailure {
                        runCatching {
                            context.startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
                        }
                    }
                },
            )
        }
    }
}

// ----------------------------------------------------------------------
// Repositories manager (inside the Sources section)
// ----------------------------------------------------------------------

@Composable
internal fun RepositoriesManager(
    state: SettingsUiState,
    onToggle: (String, Boolean) -> Unit,
    onRefresh: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (RepositoryConfig) -> Unit,
    onReorder: (List<String>) -> Unit,
    onAdd: () -> Unit,
    onRefreshAll: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
) {
    var pendingRemove by remember { mutableStateOf<RepositoryConfig?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val enabledCount = state.repositories.count { it.enabled }
        val totalApps = state.repositories.filter { it.enabled }.sumOf { state.appCounts[it.repositoryId] ?: 0 }
        Text(
            stringResource(UiR.string.settings_repos_enabled, enabledCount, state.repositories.size, totalApps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        state.repositories.forEach { repository ->
            val repositories = state.repositories
            val index = repositories.indexOfFirst { it.repositoryId == repository.repositoryId }
            RepositoryRow(
                repository = repository,
                appCount = state.appCounts[repository.repositoryId] ?: 0,
                loading = repository.repositoryId in state.refreshingIds,
                canMoveUp = index > 0,
                canMoveDown = index < repositories.lastIndex,
                onToggle = { enabled -> onToggle(repository.repositoryId, enabled) },
                onRefresh = { onRefresh(repository.repositoryId) },
                onMoveUp = {
                    val ids = repositories.map { it.repositoryId }.toMutableList()
                    ids.removeAt(index).also { ids.add(index - 1, it) }
                    onReorder(ids)
                },
                onMoveDown = {
                    val ids = repositories.map { it.repositoryId }.toMutableList()
                    ids.removeAt(index).also { ids.add(index + 1, it) }
                    onReorder(ids)
                },
                onRemove = { pendingRemove = repository },
                onEdit = { onEdit(repository) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onAdd, enabled = !state.busy) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(UiR.string.settings_add_repository))
            }
            TextButton(onClick = onRefreshAll, enabled = !state.busy) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(UiR.string.settings_refresh_all))
            }
            TextButton(onClick = onExport, enabled = !state.busy) {
                Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(UiR.string.settings_repos_export))
            }
            TextButton(onClick = onImport, enabled = !state.busy) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(UiR.string.settings_repos_import))
            }
        }
    }

    pendingRemove?.let { repository ->
        val appCount = state.appCounts[repository.repositoryId] ?: 0
        ConfirmActionDialog(
            title = repository.name,
            message = stringResource(
                if (repository.isBuiltIn) UiR.string.settings_repo_delete_builtin else UiR.string.settings_repo_delete_confirm,
                appCount,
            ),
            confirmLabel = stringResource(UiR.string.settings_repo_remove),
            onConfirm = {
                onRemove(repository.repositoryId)
                pendingRemove = null
            },
            onDismiss = { pendingRemove = null },
        )
    }
}

@Composable
internal fun RepositoryRow(
    repository: RepositoryConfig,
    appCount: Int,
    loading: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        repository.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = repository.baseUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.5.dp)
                } else if (repository.enabled) {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(UiR.string.cd_refresh))
                    }
                }
                Switch(checked = repository.enabled, onCheckedChange = onToggle, enabled = !loading)
                Column {
                    IconButton(onClick = onMoveUp, enabled = canMoveUp && !loading) {
                        Icon(
                            Icons.Filled.ArrowUpward,
                            contentDescription = stringResource(UiR.string.cd_reorder_up),
                        )
                    }
                    IconButton(onClick = onMoveDown, enabled = canMoveDown && !loading) {
                        Icon(
                            Icons.Filled.ArrowDownward,
                            contentDescription = stringResource(UiR.string.cd_reorder_down),
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(UiR.string.settings_repo_remove))
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val status = when {
                    loading -> stringResource(UiR.string.settings_repo_loading)
                    !repository.enabled -> stringResource(UiR.string.settings_repo_disabled)
                    repository.lastRefreshError != null -> repository.lastRefreshError!!
                    repository.lastRefreshAt == null -> stringResource(UiR.string.settings_repo_not_loaded)
                    else -> stringResource(UiR.string.settings_repo_apps, appCount)
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (repository.enabled && repository.lastRefreshError != null && !loading) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                repository.lastRefreshAt?.let {
                    Text(
                        text = "· " + stringResource(
                            UiR.string.settings_repo_synced,
                            DateFormat.getDateInstance().format(Date(it)),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (repository.enabled) {
                    SourceBadge(source = repository.repositoryId, compact = true)
                }
            }
        }
    }
}

/**
 * Add or edit a source. Add: full form (name, provider type, URL, extra).
 * Edit of a built-in: only the local name — the URL and type are locked.
 * The source type is detected from the URL until the user picks a chip.
 */
@Composable
internal fun RepositoryEditorDialog(
    initial: RepositoryConfig?,
    preview: SourcePreview?,
    previewError: String?,
    previewLoading: Boolean,
    onDismiss: () -> Unit,
    onTest: (name: String, url: String, type: ProviderType, extraJson: String?) -> Unit,
    onConfirm: (name: String, url: String, type: ProviderType, extraJson: String?) -> Unit,
) {
    val editing = initial != null
    val urlLocked = initial?.isBuiltIn == true
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var type by remember { mutableStateOf(initial?.providerType ?: ProviderType.FDROID_INDEX) }
    var url by remember { mutableStateOf(initial?.baseUrl.orEmpty()) }
    var typeTouched by rememberSaveable { mutableStateOf(editing) }
    val storedJson = remember(initial) {
        initial?.extraJson?.takeIf { it.isNotBlank() }?.let {
            runCatching { org.json.JSONObject(it) }.getOrNull()
        }
    }
    var apkUrlRegex by remember {
        mutableStateOf(storedJson?.optString("apkUrlRegex").orEmpty())
    }
    var apkFilterRegex by remember {
        mutableStateOf(storedJson?.optString("apkFilterRegex").orEmpty())
    }
    var includePrereleases by remember {
        mutableStateOf(storedJson?.optBoolean("includePrereleases") ?: false)
    }
    val extras = buildExtras(type, apkUrlRegex, apkFilterRegex, includePrereleases)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (editing) UiR.string.settings_edit_repository else UiR.string.settings_add_repository))
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(UiR.string.settings_repo_name)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                if (!urlLocked) {
                    LabelRow(stringResource(UiR.string.settings_repo_type))
                    ChoiceChipGrid(
                        items = ProviderType.entries.map { provider ->
                            provider to stringResource(
                                when (provider) {
                                    ProviderType.FDROID_INDEX -> UiR.string.provider_fdroid_index
                                    ProviderType.GITHUB -> UiR.string.provider_github
                                    ProviderType.GITEA -> UiR.string.provider_gitea
                                    ProviderType.GITLAB -> UiR.string.provider_gitlab
                                    ProviderType.HTML_REGEX -> UiR.string.provider_html_regex
                                },
                            )
                        },
                        selected = type,
                        onSelect = { selected ->
                            type = selected
                            typeTouched = true
                        },
                    )
                }
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        if (!urlLocked) {
                            url = it
                            if (!typeTouched) {
                                SourceUrls.detectProviderType(it)?.let { detected -> type = detected }
                            }
                        }
                    },
                    label = { Text(stringResource(UiR.string.settings_repo_url)) },
                    placeholder = {
                        Text(
                            when (type) {
                                ProviderType.FDROID_INDEX -> "https://example.com/fdroid/repo"
                                ProviderType.GITHUB -> "https://github.com/owner/repo"
                                ProviderType.GITEA, ProviderType.GITLAB -> "https://codeberg.org/owner/repo"
                                ProviderType.HTML_REGEX -> "https://example.com/downloads"
                            },
                        )
                    },
                    supportingText = { Text(stringResource(UiR.string.settings_repo_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    enabled = !urlLocked,
                )
                when {
                    type == ProviderType.HTML_REGEX -> OutlinedTextField(
                        value = apkUrlRegex,
                        onValueChange = { apkUrlRegex = it },
                        label = { Text(stringResource(UiR.string.settings_repo_apk_url_regex)) },
                        supportingText = { Text(stringResource(UiR.string.settings_repo_apk_url_regex_hint)) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                    )
                    type == ProviderType.GITHUB ||
                        type == ProviderType.GITEA ||
                        type == ProviderType.GITLAB -> {
                        SwitchRow(
                            label = stringResource(UiR.string.settings_repo_include_prereleases),
                            description = stringResource(UiR.string.settings_repo_include_prereleases_hint),
                            checked = includePrereleases,
                            onChecked = { includePrereleases = it },
                        )
                        OutlinedTextField(
                            value = apkFilterRegex,
                            onValueChange = { apkFilterRegex = it },
                            label = { Text(stringResource(UiR.string.settings_repo_apk_filter_regex)) },
                            supportingText = { Text(stringResource(UiR.string.settings_repo_apk_filter_regex_hint)) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        onClick = { onTest(name, url, type, extras) },
                        enabled = url.isNotBlank() && !previewLoading,
                    ) {
                        if (previewLoading) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        } else {
                            Icon(
                                Icons.Filled.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(stringResource(UiR.string.settings_repo_test))
                    }
                }
                when {
                    previewError != null -> Text(
                        text = previewError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    preview != null -> {
                        val count = preview.appCountHint
                        if (count != null) {
                            Text(
                                text = stringResource(UiR.string.settings_repo_preview_found, count),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (preview.sampleNames.isNotEmpty()) {
                            preview.sampleNames.forEach { name ->
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        preview.warning?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = stringResource(if (urlLocked) UiR.string.settings_repo_name_only else UiR.string.settings_repo_trust),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, url, type, extras) },
                enabled = url.isNotBlank() &&
                    name.isNotBlank() &&
                    (type != ProviderType.HTML_REGEX || apkUrlRegex.isNotBlank()),
            ) {
                Text(stringResource(if (editing) UiR.string.action_save else UiR.string.settings_add_repository))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(UiR.string.action_cancel)) }
        },
    )
}

/** Per-provider options, stored as JSON in RepositoryConfig.extraJson. */
private fun buildExtras(
    type: ProviderType,
    apkUrlRegex: String,
    apkFilterRegex: String,
    includePrereleases: Boolean,
): String? {
    if (type == ProviderType.HTML_REGEX) {
        return if (apkUrlRegex.isNotBlank()) {
            org.json.JSONObject().put("apkUrlRegex", apkUrlRegex).toString()
        } else {
            null
        }
    }
    if (type != ProviderType.GITHUB && type != ProviderType.GITEA && type != ProviderType.GITLAB) {
        return null
    }
    val json = org.json.JSONObject()
    if (includePrereleases) json.put("includePrereleases", true)
    if (apkFilterRegex.isNotBlank()) json.put("apkFilterRegex", apkFilterRegex)
    return if (json.length() == 0) null else json.toString()
}