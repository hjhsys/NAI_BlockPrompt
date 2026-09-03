package com.hjhsys.naiblockprompt.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.hjhsys.naiblockprompt.ui.generate.GeneratePagerScreen
import com.hjhsys.naiblockprompt.ui.generate.GenerationSettingsScreen
import com.hjhsys.naiblockprompt.ui.library.SavedScreen
import com.hjhsys.naiblockprompt.ui.components.AppTitleBar
import com.hjhsys.naiblockprompt.domain.model.AutocompleteSource
import com.hjhsys.naiblockprompt.ui.database.TagDatabaseScreen
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import android.content.Intent

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
}

@Composable
fun NaiBlockPromptApp(container: AppContainer) {
    val navController = rememberNavController()
    val viewModel: MainViewModel = viewModel(factory = MainViewModel.factory(container))
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
            if (!(currentRoute == MainDestination.Saved.route && savedWorkflow != null) && currentRoute != MainDestination.TagPicker.route) NavigationBar {
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
                        icon = { Icon(destination.icon, contentDescription = null, modifier = Modifier.size(20.dp)) },
                        label = { Text(stringResource(destination.label), style = MaterialTheme.typography.labelSmall) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = MainDestination.Generate.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(MainDestination.Generate.route) {
                GeneratePagerScreen(
                    session = session,
                    appSettings = settings,
                    viewModel = viewModel,
                    onOpenSettings = { navController.navigate(MainDestination.Settings.route) { launchSingleTop = true } },
                    onOpenGenerationSettings = { navController.navigate(MainDestination.GenerationSettings.route) { launchSingleTop = true } },
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
                    historyLimit = settings.historyLimit,
                    autocompleteSource = settings.autocompleteSource,
                    appearanceMode = settings.appearanceMode,
                    imageSaveTreeUri = settings.imageSaveTreeUri,
                    tokenConfigured = tokenConfigured,
                    connectionState = connectionState,
                    onShowFormatterChange = viewModel::setShowFormatter,
                    onNormalizeWeightsChange = viewModel::setNormalizeWeights,
                    onHistoryLimitChange = viewModel::setHistoryLimit,
                    onAutocompleteSourceChange = viewModel::setAutocompleteSource,
                    onAppearanceModeChange = viewModel::setAppearanceMode,
                    onImageSaveTreeUriChange = viewModel::setImageSaveTreeUri,
                    onSaveToken = viewModel::saveToken,
                    onClearToken = viewModel::clearToken,
                    onTestConnection = viewModel::testConnection,
                    onBack = { navController.popBackStack() },
                )
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
    historyLimit: Int,
    autocompleteSource: AutocompleteSource,
    appearanceMode: com.hjhsys.naiblockprompt.domain.model.AppearanceMode,
    imageSaveTreeUri: String?,
    tokenConfigured: Boolean,
    connectionState: ConnectionUiState,
    onShowFormatterChange: (Boolean) -> Unit,
    onNormalizeWeightsChange: (Boolean) -> Unit,
    onHistoryLimitChange: (Int) -> Unit,
    onAutocompleteSourceChange: (AutocompleteSource) -> Unit,
    onAppearanceModeChange: (com.hjhsys.naiblockprompt.domain.model.AppearanceMode) -> Unit,
    onImageSaveTreeUriChange: (String?) -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onTestConnection: () -> Unit,
    onBack: () -> Unit,
) {
    var token by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
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
    Column(modifier = Modifier.fillMaxSize()) {
        AppTitleBar(R.string.settings_title, onBack = onBack)
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        SettingSwitch(R.string.settings_formatter, showFormatter, onShowFormatterChange)
        SettingSwitch(R.string.settings_weight_normalization, normalizeWeights, onNormalizeWeightsChange)
        Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleMedium)
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
        Slider(value = historyLimit.toFloat(), onValueChange = { onHistoryLimitChange(it.toInt()) }, valueRange = 1f..100f, steps = 98)
        Text(stringResource(R.string.settings_autocomplete), style = MaterialTheme.typography.titleMedium)
        AutocompleteSource.entries.forEach { source ->
            FilterChip(
                selected = autocompleteSource == source,
                onClick = { onAutocompleteSourceChange(source) },
                label = { Text(stringResource(source.labelResource)) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        HorizontalDivider()
        Text(stringResource(R.string.novelai_credentials), style = MaterialTheme.typography.titleLarge)
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
        Text(stringResource(R.string.settings_saved), style = MaterialTheme.typography.bodySmall)
        }
    }
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
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(label), modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
