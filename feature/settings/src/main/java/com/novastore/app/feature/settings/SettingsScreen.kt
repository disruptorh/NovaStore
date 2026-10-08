package com.novastore.app.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.novastore.app.core.common.AppResult
import com.novastore.app.core.model.RepositoryConfig
import com.novastore.app.core.ui.R as UiR
import com.novastore.app.core.ui.components.EmptyState
import com.novastore.app.core.ui.components.InfoBanner
import com.novastore.app.core.ui.components.InfoBannerKind
import com.novastore.app.feature.settings.sections.AboutSection
import com.novastore.app.feature.settings.sections.AppearanceSection
import com.novastore.app.feature.settings.sections.BackupSection
import com.novastore.app.feature.settings.sections.DownloadsSection
import com.novastore.app.feature.settings.sections.PrivacySection
import com.novastore.app.feature.settings.sections.RepositoryEditorDialog
import com.novastore.app.feature.settings.sections.SessionProviderDialog
import com.novastore.app.feature.settings.sections.SourcesSection
import com.novastore.app.feature.settings.sections.StorageSection
import com.novastore.app.feature.settings.sections.UpdatesSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
// P09: shell only — the 8 searchable sections live in the section files.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenAccount: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var search by rememberSaveable { mutableStateOf("") }
    var showAddRepository by remember { mutableStateOf(false) }
    var editRepository by remember { mutableStateOf<RepositoryConfig?>(null) }
    var showSessionProvider by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val resolver = context.contentResolver
    val exportSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) scope.launch {
            when (val result = viewModel.exportSources()) {
                is AppResult.Failure -> viewModel.noticeFailure(result.error.userMessage)
                is AppResult.Success -> {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            resolver.openOutputStream(uri, "wt")?.use { it.write(result.value.toByteArray()) }
                        }.getOrElse { it.printStackTrace() }
                    }
                    viewModel.onSourcesExported()
                }
}
        }
    }
    val importSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { resolver.openInputStream(uri)?.use { it.bufferedReader().readText() } }.getOrNull()
            }
            if (text == null) {
                viewModel.noticeFailure("Could not read the backup file.")
            } else {
                when (val result = viewModel.importSources(text)) {
                    is AppResult.Failure -> viewModel.noticeFailure(result.error.userMessage)
                    is AppResult.Success -> viewModel.onSourcesImported(result.value)
                }
            }
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.updateSettings { it.copy(notificationsEnabled = granted) } }
    fun onNotificationsToggled(enabled: Boolean) {
        val needsPermission = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        val granted = needsPermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (needsPermission && !granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.updateSettings { it.copy(notificationsEnabled = enabled) }
        }
    }
    val items = settingsItems()
    val query = search.trim()
    fun show(id: String): Boolean = query.isEmpty() || items.any { it.id == id && settingsItemMatches(it, query) }
    fun has(section: SettingsSectionKey): Boolean =
        query.isEmpty() || items.any { it.section == section && settingsItemMatches(it, query) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(UiR.string.settings_title)) },
                actions = {
                    IconButton(onClick = { viewModel.refreshRepositories() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(UiR.string.settings_refresh_all))
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(UiR.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.error != null) {
                InfoBanner(text = state.error ?: "", kind = InfoBannerKind.ERROR, onDismiss = viewModel::dismissError)
            }
            if (state.notice != null) {
                InfoBanner(text = state.notice ?: "", onDismiss = viewModel::dismissNotice)
            }
            SettingsSearchField(
                value = search,
                onValueChange = { search = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (query.isNotEmpty() && items.none { settingsItemMatches(it, query) }) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = stringResource(UiR.string.search_no_results, query),
                    description = stringResource(UiR.string.settings_search_empty_desc),
                )
            } else {
                if (has(SettingsSectionKey.SOURCES)) {
                    SourcesSection(
                        state = state,
                        show = ::show,
                        viewModel = viewModel,
                        onExport = { exportSourcesLauncher.launch("nova-store-sources.json") },
                        onImport = { importSourcesLauncher.launch(arrayOf("application/json", "text/*")) },
                        onAdd = { showAddRepository = true },
                        onEdit = { editRepository = it },
                    )
                }
                if (has(SettingsSectionKey.UPDATES)) {
                    UpdatesSection(state.settings, ::show, viewModel::updateSettings)
                }
                if (has(SettingsSectionKey.DOWNLOADS_AND_INSTALL)) {
                    DownloadsSection(state, ::show, viewModel)
                }
                if (has(SettingsSectionKey.APPEARANCE)) {
                    AppearanceSection(::show, state, viewModel)
                }
                if (has(SettingsSectionKey.STORAGE)) {
                    StorageSection(::show, viewModel)
                }
                if (has(SettingsSectionKey.PRIVACY)) {
                    PrivacySection(
                        ::show, state, viewModel, onOpenAccount,
                        { showSessionProvider = true }, ::onNotificationsToggled,
                    )
                }
                if (has(SettingsSectionKey.BACKUP)) {
                    BackupSection(
                        ::show,
                        { exportSourcesLauncher.launch("nova-store-sources.json") },
                        { importSourcesLauncher.launch(arrayOf("application/json", "text/*")) },
                    )
                }
                if (has(SettingsSectionKey.ABOUT)) {
                    AboutSection(::show, viewModel::resetSettings)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showAddRepository) {
        RepositoryEditorDialog(null,
            state.sourcePreview, state.sourcePreviewError, state.sourcePreviewLoading,
            onDismiss = { showAddRepository = false },
            onTest = viewModel::testSource,
            onConfirm = { name, url, type, apkUrlRegex ->
                viewModel.addCustomRepository(name, url, type, apkUrlRegex)
                showAddRepository = false
            },
)
    }
    editRepository?.let { repository ->
        RepositoryEditorDialog(repository,
            state.sourcePreview, state.sourcePreviewError, state.sourcePreviewLoading,
            onDismiss = { editRepository = null },
            onTest = viewModel::testSource,
            onConfirm = { name, url, type, apkUrlRegex ->
                viewModel.saveRepository(repository.repositoryId, name, url, type, apkUrlRegex)
                editRepository = null
            },
        )
    }
    if (showSessionProvider) {
        SessionProviderDialog(state.tokenDispenserUrl,
            onDismiss = { showSessionProvider = false },
            onSave = { url ->
                viewModel.setTokenDispenserUrl(url)
                showSessionProvider = false
            },
        )
    }
}