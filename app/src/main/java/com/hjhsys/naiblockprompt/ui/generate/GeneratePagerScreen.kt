package com.hjhsys.naiblockprompt.ui.generate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.app.Activity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.model.AppSettings
import com.hjhsys.naiblockprompt.domain.model.Session
import com.hjhsys.naiblockprompt.domain.model.ImageInputMode
import com.hjhsys.naiblockprompt.domain.generation.VibeTransferRequestMapper
import com.hjhsys.naiblockprompt.ui.GenerationUiState
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.library.HistoryScreen
import kotlinx.coroutines.launch

internal object GenerateNavigationPolicy {
    const val SETTINGS_PAGE = 0
    const val GENERATE_PAGE = 1
    const val RESULT_PAGE = 2
    const val HISTORY_PAGE = 3
    const val PAGE_COUNT = 4
    fun afterGenerationSuccess() = RESULT_PAGE
    fun shouldAutoOpenResult(imagePath: String?, lastOpenedImagePath: String?, autoOpenResult: Boolean = true) =
        autoOpenResult && imagePath != null && imagePath != lastOpenedImagePath
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GeneratePagerScreen(
    session: Session?,
    appSettings: AppSettings,
    viewModel: MainViewModel,
    onOpenSettings: () -> Unit,
    returnToPromptSignal: Int,
    onOpenTagDatabase: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = GenerateNavigationPolicy.GENERATE_PAGE) { GenerateNavigationPolicy.PAGE_COUNT }
    LaunchedEffect(viewModel) {
        viewModel.imageInputApplied.collect { pagerState.animateScrollToPage(GenerateNavigationPolicy.SETTINGS_PAGE) }
    }
    val scope = rememberCoroutineScope()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    val generationInProgress by viewModel.generationInProgress.collectAsStateWithLifecycle()
    val wildcardItems by viewModel.wildcards.collectAsStateWithLifecycle()
    val wildcardValues = remember(wildcardItems) {
        wildcardItems.associate { it.name to it.valuesText.lines().filter(String::isNotBlank) }
    }
    val headerTokenRange = remember(session, wildcardValues, appSettings.normalizeWeightClosings, appSettings.useTextRendering) {
        session?.let { estimateSessionTokenRange(it, appSettings, wildcardValues) }
    }
    val showHeaderTokens = session?.generationSettings?.modelId?.contains("diffusion-4") == true && appSettings.showTokenEstimates
    val headerTokenSummary = headerTokenRange?.takeIf { showHeaderTokens }?.let { range ->
        if (range.hasRange) stringResource(R.string.estimated_tokens_range, range.minimum, range.maximum)
        else stringResource(R.string.estimated_tokens_single, range.minimum)
    }
    var lastAutoOpenedImage by rememberSaveable {
        mutableStateOf((generationState as? GenerationUiState.Success)?.record?.imagePath)
    }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity
    val imageInput = session?.generationSettings?.imageInput
    val vibeIgnored = imageInput?.mode == ImageInputMode.VIBE_TRANSFER &&
        session.generationSettings.modelId?.let { !VibeTransferRequestMapper.isSupported(it) } == true

    LaunchedEffect(generationState) {
        val success = generationState as? GenerationUiState.Success
        val imagePath = success?.record?.imagePath
        if (GenerateNavigationPolicy.shouldAutoOpenResult(imagePath, lastAutoOpenedImage, success?.autoOpenResult == true)) {
            lastAutoOpenedImage = imagePath
            pagerState.animateScrollToPage(GenerateNavigationPolicy.afterGenerationSuccess())
        }
    }
    LaunchedEffect(returnToPromptSignal) {
        if (returnToPromptSignal > 0) pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE)
    }
    BackHandler {
        if (pagerState.currentPage != GenerateNavigationPolicy.GENERATE_PAGE) scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) }
        else showExitDialog = true
    }
    if (showExitDialog) AlertDialog(
        onDismissRequest = { showExitDialog = false },
        title = { Text(stringResource(R.string.exit_app_title)) },
        confirmButton = { TextButton(onClick = { activity?.finish() }) { Text(stringResource(R.string.exit)) } },
        dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            Surface(shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    if (vibeIgnored) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        ) {
                            Text(
                                stringResource(R.string.vibe_v5_ignored_warning),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            )
                        }
                    }
                    Button(
                        onClick = viewModel::generate,
                        enabled = session != null && !generationInProgress,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    ) {
                        if (generationInProgress) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(generateButtonLabel(generationState, imageInput.takeUnless { vibeIgnored }, generationInProgress))
                    }
                }
            }
        },
    ) { actionPadding -> Box(Modifier.fillMaxSize().padding(actionPadding).consumeWindowInsets(actionPadding)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            // Autocomplete state may outlive TextField focus. It must never globally disable paging.
            userScrollEnabled = true,
        ) { page ->
        key(page) {
        when {
            page == GenerateNavigationPolicy.SETTINGS_PAGE -> GenerationSettingsScreen(
                session = session,
                viewModel = viewModel,
                onBack = { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                onOpenSettings = onOpenSettings,
                headerTokenSummary = headerTokenSummary,
            )
            page == GenerateNavigationPolicy.GENERATE_PAGE -> GenerateScreen(
                session,
                appSettings,
                viewModel,
                onOpenSettings,
                { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.SETTINGS_PAGE) } },
                onOpenTagDatabase,
                headerTokenSummary,
            )
            page == GenerateNavigationPolicy.RESULT_PAGE -> ResultScreen(
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                onRestored = { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                headerTokenSummary = headerTokenSummary,
            )
            else -> HistoryScreen(
                viewModel = viewModel,
                onOpenSettings = onOpenSettings,
                isActive = pagerState.currentPage == GenerateNavigationPolicy.HISTORY_PAGE,
                onRestored = { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                headerTokenSummary = headerTokenSummary,
            )
        }
        }
        }
        if (pagerState.currentPage > GenerateNavigationPolicy.SETTINGS_PAGE) {
            val previousLabel = pageLabel(pagerState.currentPage - 1)
            EdgePageHint(Alignment.CenterStart, Icons.Default.ChevronLeft, previousLabel) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
            }
        }
        if (pagerState.currentPage < GenerateNavigationPolicy.PAGE_COUNT - 1) {
            val nextLabel = pageLabel(pagerState.currentPage + 1)
            EdgePageHint(Alignment.CenterEnd, Icons.Default.ChevronRight, nextLabel) {
                scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            }
        }
    } }
}

private fun pageLabel(page: Int): Int = when (page) {
    GenerateNavigationPolicy.SETTINGS_PAGE -> R.string.generation_settings
    GenerateNavigationPolicy.GENERATE_PAGE -> R.string.nav_generate
    GenerateNavigationPolicy.RESULT_PAGE -> R.string.workspace_result
    else -> R.string.history_title
}

@Composable
private fun BoxScope.EdgePageHint(alignment: Alignment, icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier.align(alignment).padding(vertical = 72.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) 0.38f else 0.24f))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) {
        Icon(
            icon,
            stringResource(label),
            Modifier.padding(vertical = 18.dp, horizontal = 5.dp).size(20.dp),
            tint = MaterialTheme.colorScheme.onSurface,
        )
    }
}
