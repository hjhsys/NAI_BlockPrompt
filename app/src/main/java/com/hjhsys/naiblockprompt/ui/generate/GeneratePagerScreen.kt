package com.hjhsys.naiblockprompt.ui.generate

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.hjhsys.naiblockprompt.ui.GenerationUiState
import com.hjhsys.naiblockprompt.ui.MainViewModel
import com.hjhsys.naiblockprompt.ui.library.HistoryScreen
import kotlinx.coroutines.launch

internal enum class SecondaryTarget { RESULT, HISTORY }

internal object GenerateNavigationPolicy {
    const val SETTINGS_PAGE = 0
    const val GENERATE_PAGE = 1
    const val SECONDARY_PAGE = 2
    fun afterGenerationSuccess() = SecondaryTarget.RESULT
    fun afterHistoryReturn() = SecondaryTarget.HISTORY
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
    onOpenGenerationSettings: () -> Unit,
    returnToPromptSignal: Int,
    onOpenTagDatabase: () -> Unit,
) {
    val pagerState = rememberPagerState(initialPage = GenerateNavigationPolicy.GENERATE_PAGE) { 3 }
    val scope = rememberCoroutineScope()
    val autocomplete by viewModel.autocomplete.collectAsStateWithLifecycle()
    val generationState by viewModel.generationState.collectAsStateWithLifecycle()
    var lastAutoOpenedImage by rememberSaveable {
        mutableStateOf((generationState as? GenerationUiState.Success)?.record?.imagePath)
    }
    var secondaryTarget by rememberSaveable { mutableStateOf(SecondaryTarget.RESULT) }
    var showExitDialog by rememberSaveable { mutableStateOf(false) }
    val activity = LocalContext.current as? Activity

    LaunchedEffect(generationState) {
        val success = generationState as? GenerationUiState.Success
        val imagePath = success?.record?.imagePath
        if (GenerateNavigationPolicy.shouldAutoOpenResult(imagePath, lastAutoOpenedImage, success?.autoOpenResult == true)) {
            lastAutoOpenedImage = imagePath
            secondaryTarget = GenerateNavigationPolicy.afterGenerationSuccess()
            pagerState.animateScrollToPage(GenerateNavigationPolicy.SECONDARY_PAGE)
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

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = autocomplete.blockId == null,
        ) { page ->
        when (page) {
            GenerateNavigationPolicy.SETTINGS_PAGE -> GenerationSettingsScreen(
                session = session,
                viewModel = viewModel,
                onBack = { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                onOpenSettings = onOpenSettings,
            )
            GenerateNavigationPolicy.GENERATE_PAGE -> GenerateScreen(session, appSettings, viewModel, onOpenSettings, onOpenGenerationSettings, onOpenTagDatabase)
            else -> when (secondaryTarget) {
                SecondaryTarget.RESULT -> ResultScreen(viewModel) { secondaryTarget = SecondaryTarget.HISTORY }
                SecondaryTarget.HISTORY -> HistoryScreen(
                    viewModel = viewModel,
                    onOpenSettings = onOpenSettings,
                    isActive = pagerState.currentPage == GenerateNavigationPolicy.SECONDARY_PAGE,
                    onOpenResult = { secondaryTarget = SecondaryTarget.RESULT },
                    onRestored = { secondaryTarget = GenerateNavigationPolicy.afterHistoryReturn(); scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                )
            }
        }
        }
        if (pagerState.currentPage != GenerateNavigationPolicy.GENERATE_PAGE) {
            val onLeftPage = pagerState.currentPage == GenerateNavigationPolicy.SETTINGS_PAGE
            Surface(
                modifier = Modifier
                    .align(if (onLeftPage) Alignment.CenterEnd else Alignment.CenterStart)
                    .padding(vertical = 72.dp)
                    .clickable { scope.launch { pagerState.animateScrollToPage(GenerateNavigationPolicy.GENERATE_PAGE) } },
                shape = if (onLeftPage) MaterialTheme.shapes.small else MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f),
                shadowElevation = 3.dp,
            ) {
                Icon(
                    if (onLeftPage) Icons.Default.ChevronRight else Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.back_to_generate),
                    modifier = Modifier.padding(vertical = 18.dp, horizontal = 3.dp).size(22.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}
