package com.hjhsys.naiblockprompt.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hjhsys.naiblockprompt.AppContainer
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.BuildConfig
import com.hjhsys.naiblockprompt.ui.generate.GeneratePagerScreen
import com.hjhsys.naiblockprompt.ui.generate.GenerationSettingsScreen
import com.hjhsys.naiblockprompt.ui.library.SavedScreen
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.ui.components.NumericSlider
import com.hjhsys.naiblockprompt.ui.components.NumericSliderSpec
import com.hjhsys.naiblockprompt.ui.components.NumericValueEditor
import com.hjhsys.naiblockprompt.ui.components.HelpAffordance
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import com.hjhsys.naiblockprompt.ui.database.TagDatabaseScreen
import com.hjhsys.naiblockprompt.ui.help.HelpScreen
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import com.hjhsys.naiblockprompt.data.diagnostics.CrashLogStore
import com.hjhsys.naiblockprompt.data.library.HistoryStorageEstimator
import com.hjhsys.naiblockprompt.data.library.HistoryStorageSample

private enum class MainDestination(
    val route: String,
    @param:StringRes val label: Int,
    val icon: ImageVector,
) {
    Database("database", R.string.nav_db, Icons.Default.Storage),
    Generate("generate", R.string.nav_generate, Icons.Default.AutoAwesome),
    Saved("saved", R.string.nav_saved, Icons.Default.Bookmarks),
    Settings("settings", R.string.nav_settings, Icons.Default.Settings),
    GenerationSettings("generation-settings", R.string.generation_settings, Icons.Default.Tune),
    TagPicker("tag-picker", R.string.tag_picker_title, Icons.Default.Storage),
    Help("help", R.string.help_title, Icons.AutoMirrored.Filled.HelpOutline),
}

@Composable
fun NaiBlockPromptApp(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val transferFailed by viewModel.transferFailed.collectAsStateWithLifecycle()
    val wildcardSaveFailed by viewModel.wildcardSaveFailed.collectAsStateWithLifecycle()
    if (wildcardSaveFailed) AlertDialog(
        onDismissRequest = viewModel::dismissWildcardSaveFailure,
        text = { Text(stringResource(R.string.wildcard_save_failed)) },
        confirmButton = { TextButton(onClick = viewModel::dismissWildcardSaveFailure) { Text(stringResource(R.string.close)) } },
    )
    if (transferFailed) AlertDialog(
        onDismissRequest = viewModel::dismissTransferFailure,
        title = { Text(stringResource(R.string.transfer_failed_title)) },
        text = { Text(stringResource(R.string.transfer_failed_message)) },
        confirmButton = { TextButton(onClick = viewModel::dismissTransferFailure) { Text(stringResource(R.string.close)) } },
    )
    val session by viewModel.session.collectAsStateWithLifecycle()
    val tokenConfigured by viewModel.tokenConfigured.collectAsStateWithLifecycle()
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val savedWorkflow by viewModel.savedWorkflow.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var generateReturnSignal by rememberSaveable { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.flushAutosave()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(savedWorkflow) {
        if (savedWorkflow != null && currentRoute != MainDestination.Saved.route) {
            navController.navigate(MainDestination.Saved.route) { launchSingleTop = true }
        }
    }

    Scaffold(
        bottomBar = {
            if (currentRoute != MainDestination.TagPicker.route &&
                currentRoute != MainDestination.Help.route
            ) NavigationBar(
                // Keep the compact 72dp bar above the system navigation/gesture inset.
                // Applying height first would include the inset inside that fixed height
                // and squeeze/crop the labels on devices with a tall bottom inset.
                modifier = Modifier.navigationBarsPadding().height(72.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                listOf(MainDestination.Database, MainDestination.Generate, MainDestination.Saved).forEach { destination ->
                    NavigationBarItem(
                        selected = currentRoute == destination.route,
                        onClick = {
                            if (destination == MainDestination.Generate) generateReturnSignal++
                            navController.navigate(destination.route) {
                                popUpTo(MainDestination.Generate.route)
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier.height(72.dp),
                        icon = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall, maxLines = 1) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Generate.route,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(MainDestination.Generate.route) {
                GeneratePagerScreen(
                    session = session,
                    appSettings = settings,
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(MainDestination.Settings.route) { launchSingleTop = true } },
                    returnToPromptSignal = generateReturnSignal,
                    onOpenTagDatabase = { navController.navigate(MainDestination.TagPicker.route) },
                )
            }
            composable(MainDestination.Database.route) {
                TagDatabaseScreen(
                    viewModel,
                    onOpenSettings = { navController.navigate(MainDestination.Settings.route) { launchSingleTop = true } },
                )
            }
            composable(MainDestination.TagPicker.route) {
                com.hjhsys.naiblockprompt.ui.database.TagPickerScreen(
                    viewModel = viewModel,
                    onDismiss = { viewModel.cancelTagInsert(); navController.popBackStack() },
                    onInserted = {
                        generateReturnSignal++
                        navController.popBackStack()
                    },
                )
            }
            composable(MainDestination.Saved.route) {
                SavedScreen(viewModel, onOpenSettings = {
                    navController.navigate(MainDestination.Settings.route) { launchSingleTop = true }
                }) {
                    navController.navigate(MainDestination.Generate.route) { launchSingleTop = true }
                }
            }
            composable(MainDestination.Settings.route) {
                SettingsScreen(
                    showFormatter = settings.showFormatterActions,
                    normalizeWeights = settings.normalizeWeightClosings,
                    showTokenEstimates = settings.showTokenEstimates,
                    useTextRendering = settings.useTextRendering,
                    colorHelperMode = settings.colorHelperMode,
                    quickEditWeightStep = settings.quickEditWeightStep,
                    showExclusionConfirmationHelp = settings.showExclusionConfirmationHelp,
                    historyLimit = settings.historyLimit,
                    historyStorageSample = viewModel.historyStorageSample,
                    autocompleteSource = settings.autocompleteSource,
                    appearanceMode = settings.appearanceMode,
                    imageSaveTreeUri = settings.imageSaveTreeUri,
                    tokenConfigured = tokenConfigured,
                    connectionState = connectionState,
                    onShowFormatterChange = viewModel::setShowFormatter,
                    onNormalizeWeightsChange = viewModel::setNormalizeWeights,
                    onShowTokenEstimatesChange = viewModel::setShowTokenEstimates,
                    onUseTextRenderingChange = viewModel::setUseTextRendering,
                    onColorHelperModeChange = viewModel::setColorHelperMode,
                    onQuickEditWeightStepChange = viewModel::setQuickEditWeightStep,
                    onShowExclusionConfirmationHelpChange = viewModel::setShowExclusionConfirmationHelp,
                    onHistoryLimitChange = viewModel::setHistoryLimit,
                    onAutocompleteSourceChange = viewModel::setAutocompleteSource,
                    onAppearanceModeChange = viewModel::setAppearanceMode,
                    onImageSaveTreeUriChange = viewModel::setImageSaveTreeUri,
                    onSaveToken = viewModel::saveToken,
                    onClearToken = viewModel::clearToken,
                    onTestConnection = viewModel::testConnection,
                    onResetTagDatabase = viewModel::resetTagDatabaseToBundled,
                    onExportBackup = viewModel::exportAppBackup,
                    onImportBackup = viewModel::importAppBackup,
                    transferFiles = viewModel.transferExport,
                    onOpenHelp = { navController.navigate(MainDestination.Help.route) },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(MainDestination.Help.route) {
                HelpScreen(onBack = { navController.popBackStack() })
            }
            composable(MainDestination.GenerationSettings.route) {
                GenerationSettingsScreen(
                    session = session,
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onOpenSettings = { navController.navigate(MainDestination.Settings.route) { launchSingleTop = true } },
                )
            }
        }
    }
}

@Composable
private fun PlaceholderScreen(@StringRes title: Int, @StringRes message: Int, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        AppTitleBar(title, menuItems = listOf(com.hjhsys.naiblockprompt.ui.components.AppTitleMenuItem(R.string.nav_settings, Icons.Default.Settings, onClick = onOpenSettings)))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.not_official_notice), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SettingsScreen(
    showFormatter: Boolean,
    normalizeWeights: Boolean,
    showTokenEstimates: Boolean,
    useTextRendering: Boolean,
    colorHelperMode: com.hjhsys.naiblockprompt.domain.model.ColorHelperMode,
    quickEditWeightStep: String,
    showExclusionConfirmationHelp: Boolean,
    historyLimit: Int,
    historyStorageSample: kotlinx.coroutines.flow.StateFlow<HistoryStorageSample>,
    autocompleteSource: AutocompleteSource,
    appearanceMode: com.hjhsys.naiblockprompt.domain.model.AppearanceMode,
    imageSaveTreeUri: String?,
    tokenConfigured: Boolean,
    connectionState: ConnectionUiState,
    onShowFormatterChange: (Boolean) -> Unit,
    onNormalizeWeightsChange: (Boolean) -> Unit,
    onShowTokenEstimatesChange: (Boolean) -> Unit,
    onUseTextRenderingChange: (Boolean) -> Unit,
    onColorHelperModeChange: (com.hjhsys.naiblockprompt.domain.model.ColorHelperMode) -> Unit,
    onQuickEditWeightStepChange: (String) -> Unit,
    onShowExclusionConfirmationHelpChange: (Boolean) -> Unit,
    onHistoryLimitChange: (Int) -> Unit,
    onAutocompleteSourceChange: (AutocompleteSource) -> Unit,
    onAppearanceModeChange: (com.hjhsys.naiblockprompt.domain.model.AppearanceMode) -> Unit,
    onImageSaveTreeUriChange: (String?) -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onTestConnection: () -> Unit,
    onResetTagDatabase: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: (ByteArray) -> Unit,
    transferFiles: kotlinx.coroutines.flow.Flow<MainViewModel.TransferFile>,
    onOpenHelp: () -> Unit,
    onBack: () -> Unit,
) {
    val storageSample by historyStorageSample.collectAsStateWithLifecycle()
    var token by rememberSaveable { mutableStateOf("") }
    var showTagReset by rememberSaveable { mutableStateOf(false) }
    var tagResetAcknowledged by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val crashLogStore = remember(context) { CrashLogStore(context.applicationContext) }
    var crashLogAvailable by remember { mutableStateOf(crashLogStore.read() != null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onImageSaveTreeUriChange(it.toString())
        }
    }
    var backupExport by remember { mutableStateOf<MainViewModel.TransferFile?>(null) }
    var pendingBackupImport by remember { mutableStateOf<ByteArray?>(null) }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val file = backupExport ?: return@rememberLauncherForActivityResult
        uri?.let {
            runCatching {
                requireNotNull(context.contentResolver.openOutputStream(it)).use { output -> output.write(file.bytes) }
            }.onSuccess {
                android.widget.Toast.makeText(context, context.getString(R.string.backup_export_succeeded), android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, context.getString(R.string.backup_export_failed), android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { context.contentResolver.openInputStream(it)?.use { input -> pendingBackupImport = input.readBytes() } }
    }
    LaunchedEffect(transferFiles) {
        transferFiles.collect { file ->
            if (file.name == "nai_blockprompt_backup.zip") { backupExport = file; backupExportLauncher.launch(file.name) }
        }
    }
    pendingBackupImport?.let { bytes -> AlertDialog(
        onDismissRequest = { pendingBackupImport = null },
        title = { Text(stringResource(R.string.import_backup_title)) },
        text = { Text(stringResource(R.string.import_backup_message)) },
        confirmButton = { TextButton(onClick = { onImportBackup(bytes); pendingBackupImport = null }) { Text(stringResource(R.string.import_backup)) } },
        dismissButton = { TextButton(onClick = { pendingBackupImport = null }) { Text(stringResource(R.string.cancel)) } },
    ) }
    if (showTagReset) AlertDialog(
        onDismissRequest = { showTagReset = false; tagResetAcknowledged = false },
        title = { Text(stringResource(R.string.reset_tag_database_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.reset_tag_database_message))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = tagResetAcknowledged, onCheckedChange = { tagResetAcknowledged = it })
                    Text(stringResource(R.string.reset_tag_database_acknowledge))
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = tagResetAcknowledged,
                onClick = { onResetTagDatabase(); showTagReset = false; tagResetAcknowledged = false },
            ) { Text(stringResource(R.string.reset), color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { showTagReset = false; tagResetAcknowledged = false }) { Text(stringResource(R.string.cancel)) } },
    )
    Column(modifier = Modifier.fillMaxSize()) {
        AppTitleBar(R.string.settings_title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SettingsSection(R.string.settings_section_prompt_generation)
        SettingSwitch(R.string.settings_formatter, showFormatter, onShowFormatterChange)
        SettingSwitch(R.string.settings_weight_normalization, normalizeWeights, onNormalizeWeightsChange, R.string.help_weight_normalization_body)
        SettingSwitch(R.string.settings_token_estimates, showTokenEstimates, onShowTokenEstimatesChange, R.string.help_token_estimates_body)
        SettingSwitch(R.string.settings_use_text_rendering, useTextRendering, onUseTextRenderingChange, R.string.help_text_rendering_body)
        var quickStepDraft by rememberSaveable(quickEditWeightStep) { mutableStateOf(quickEditWeightStep) }
        OutlinedTextField(
            value = quickStepDraft,
            onValueChange = { value ->
                quickStepDraft = value
                if (value.toBigDecimalOrNull()?.let { it > java.math.BigDecimal.ZERO } == true) onQuickEditWeightStepChange(value)
            },
            label = { Text(stringResource(R.string.settings_quick_edit_weight_step)) },
            supportingText = { Text(stringResource(R.string.settings_quick_edit_weight_step_hint)) },
            isError = quickStepDraft.toBigDecimalOrNull()?.let { it <= java.math.BigDecimal.ZERO } ?: true,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.color_helper), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.hjhsys.naiblockprompt.domain.model.ColorHelperMode.entries.forEach { mode ->
                FilterChip(
                    selected = colorHelperMode == mode,
                    onClick = { onColorHelperModeChange(mode) },
                    label = { Text(stringResource(when (mode) {
                        com.hjhsys.naiblockprompt.domain.model.ColorHelperMode.ALWAYS -> R.string.color_helper_always
                        com.hjhsys.naiblockprompt.domain.model.ColorHelperMode.WHILE_EDITING -> R.string.color_helper_editing
                        com.hjhsys.naiblockprompt.domain.model.ColorHelperMode.OFF -> R.string.off
                    })) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(stringResource(R.string.color_helper_setting_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSection(R.string.settings_section_appearance)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.hjhsys.naiblockprompt.domain.model.AppearanceMode.entries.forEach { mode ->
                FilterChip(
                    selected = appearanceMode == mode,
                    onClick = { onAppearanceModeChange(mode) },
                    label = { Text(stringResource(mode.labelResource)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        SettingsSection(R.string.settings_section_storage_history)
        Text(stringResource(R.string.image_save_location), style = MaterialTheme.typography.titleMedium)
        Text(
            if (imageSaveTreeUri == null) stringResource(R.string.default_image_save_path)
            else stringResource(R.string.custom_image_save_path),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { folderPicker.launch(null) }) { Text(stringResource(R.string.choose_save_folder)) }
            TextButton(onClick = { onImageSaveTreeUriChange(null) }, enabled = imageSaveTreeUri != null) { Text(stringResource(R.string.use_default_folder)) }
        }
        Text(stringResource(R.string.save_folder_history_warning), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.settings_history_limit_value, historyLimit))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumericSlider(
                value = historyLimit.toDouble(),
                spec = HISTORY_LIMIT_SLIDER,
                onValueChange = { onHistoryLimitChange(it.toInt()) },
                modifier = Modifier.weight(1f),
            )
            NumericValueEditor(
                value = historyLimit.toDouble(),
                spec = HISTORY_LIMIT_SLIDER,
                label = stringResource(R.string.settings_history_limit),
                onValueChange = { onHistoryLimitChange(it.toInt()) },
            )
        }
        val estimatedHistoryBytes = HistoryStorageEstimator.estimatedBytes(storageSample, historyLimit)
        Text(
            if (estimatedHistoryBytes == null) stringResource(R.string.settings_history_storage_unavailable)
            else stringResource(
                R.string.settings_history_storage_estimate,
                estimatedHistoryBytes.toDouble() / (1024.0 * 1024.0),
                storageSample.sampleCount,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SettingsSection(R.string.settings_section_autocomplete_tags)
        Text(stringResource(R.string.settings_autocomplete), style = MaterialTheme.typography.titleMedium)
        AutocompleteSource.entries.forEach { source ->
            FilterChip(
                selected = autocompleteSource == source,
                onClick = { onAutocompleteSourceChange(source) },
                label = { Text(stringResource(source.labelResource)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(stringResource(R.string.tag_database_maintenance), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.tag_database_maintenance_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingSwitch(
            R.string.settings_exclusion_confirmation_help,
            showExclusionConfirmationHelp,
            onShowExclusionConfirmationHelpChange,
            R.string.settings_exclusion_confirmation_help_hint,
        )
        OutlinedButton(onClick = { showTagReset = true }) { Text(stringResource(R.string.reset_to_bundled_tags)) }
        SettingsSection(R.string.backup_and_restore)
        Text(stringResource(R.string.backup_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExportBackup, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.export_backup)) }
            OutlinedButton(onClick = { backupImportLauncher.launch(arrayOf("application/zip")) }, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.import_backup)) }
        }
        SettingsSection(R.string.settings_section_novelai_connection)
        Text(stringResource(R.string.token_security_hint), style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text(stringResource(R.string.persistent_token)) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSaveToken(token); token = "" }, enabled = token.isNotBlank()) {
                Text(stringResource(R.string.save_token))
            }
            OutlinedButton(onClick = onClearToken, enabled = tokenConfigured) { Text(stringResource(R.string.clear_token)) }
        }
        Text(stringResource(if (tokenConfigured) R.string.token_configured else R.string.token_not_configured))
        OutlinedButton(onClick = onTestConnection, enabled = connectionState !is ConnectionUiState.Testing) {
            if (connectionState is ConnectionUiState.Testing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.test_connection))
        }
        ConnectionStatus(connectionState)
        SettingsSection(R.string.settings_section_help_about)
        OutlinedButton(onClick = onOpenHelp, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.HelpOutline, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.help_user_guide))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = crashLogAvailable,
                onClick = {
                    val report = crashLogStore.read() ?: return@OutlinedButton
                    context.startActivity(Intent.createChooser(
                        Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.crash_log_share_subject))
                            putExtra(Intent.EXTRA_TEXT, report)
                        },
                        context.getString(R.string.crash_log_share),
                    ))
                },
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.crash_log_share)) }
            TextButton(
                enabled = crashLogAvailable,
                onClick = {
                    crashLogStore.clear()
                    crashLogAvailable = crashLogStore.read() != null
                },
            ) { Text(stringResource(R.string.crash_log_delete)) }
        }
        Text(
            stringResource(if (crashLogAvailable) R.string.crash_log_available else R.string.crash_log_empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(stringResource(R.string.app_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private val HISTORY_LIMIT_SLIDER = NumericSliderSpec(min = 1.0, max = 100.0, step = 1.0, displayDecimals = 0)

@Composable
private fun SettingsSection(@StringRes title: Int) {
    HorizontalDivider(Modifier.padding(top = 8.dp))
    Text(stringResource(title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
}

private val AutocompleteSource.labelResource: Int get() = when (this) {
    AutocompleteSource.NOVEL_AI -> R.string.autocomplete_nai_only
    AutocompleteSource.DANBOORU -> R.string.autocomplete_danbooru_only
    AutocompleteSource.BOTH -> R.string.autocomplete_both
}

private val com.hjhsys.naiblockprompt.domain.model.AppearanceMode.labelResource: Int get() = when (this) {
    com.hjhsys.naiblockprompt.domain.model.AppearanceMode.SYSTEM -> R.string.appearance_system
    com.hjhsys.naiblockprompt.domain.model.AppearanceMode.LIGHT -> R.string.appearance_light
    com.hjhsys.naiblockprompt.domain.model.AppearanceMode.DARK -> R.string.appearance_dark
}

@Composable
private fun ConnectionStatus(state: ConnectionUiState) {
    val message = when (state) {
        ConnectionUiState.Idle, ConnectionUiState.Testing -> null
        ConnectionUiState.Success -> R.string.connection_success
        ConnectionUiState.Failed -> R.string.connection_failed
        ConnectionUiState.AuthenticationFailed -> R.string.connection_auth_failed
        ConnectionUiState.NetworkFailed -> R.string.connection_network_failed
        is ConnectionUiState.ApiFailed -> null
        ConnectionUiState.MissingToken -> R.string.generation_error_token
    }
    if (message != null) Text(stringResource(message))
    if (state is ConnectionUiState.ApiFailed) {
        Text(
            if (state.statusCode == null) stringResource(R.string.connection_api_failed)
            else stringResource(R.string.connection_api_failed_status, state.statusCode),
        )
    }
}

@Composable
private fun SettingSwitch(
    @StringRes label: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    @StringRes helpBody: Int? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        helpBody?.let { HelpAffordance(title = label, body = it) }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
