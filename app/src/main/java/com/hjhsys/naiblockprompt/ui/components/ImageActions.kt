package com.hjhsys.naiblockprompt.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hjhsys.naiblockprompt.R
import com.hjhsys.naiblockprompt.domain.model.*
import com.hjhsys.naiblockprompt.ui.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

internal data class ImageActionAvailability(
    val imageToImage: Boolean,
    val vibeTransfer: Boolean,
    val preciseReference: Boolean,
    val inpaint: Boolean,
)

internal object ImageActionPolicy {
    fun availability(originalAvailable: Boolean, modelId: String?): ImageActionAvailability =
        ImageActionAvailability(
            imageToImage = originalAvailable,
            vibeTransfer = originalAvailable,
            preciseReference = originalAvailable && modelId?.startsWith("nai-diffusion-4-5-") == true,
            inpaint = originalAvailable && com.hjhsys.naiblockprompt.domain.generation.InpaintRequestMapper.supports(modelId),
        )
}

@Composable
fun rememberOriginalImageAvailable(reference: String): Boolean {
    val context = LocalContext.current
    val uri = remember(reference) { imageReferenceUri(reference) }
    return produceState(false, reference) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(uri)?.use { it.read() >= 0 } == true }
                .getOrDefault(false)
        }
    }.value
}

private fun imageReferenceUri(reference: String): Uri =
    if (Uri.parse(reference).scheme == null) Uri.fromFile(File(reference)) else Uri.parse(reference)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageActionsDialog(reference: String, viewModel: MainViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.image_actions), style = MaterialTheme.typography.headlineSmall)
            ImageActionChoices(reference, viewModel, onDismiss)
        }
    }
}

@Composable
fun ImageCardActions(
    imageActionsEnabled: Boolean,
    informationEnabled: Boolean,
    onImageActions: () -> Unit,
    onImportInformation: () -> Unit,
    modifier: Modifier = Modifier,
    seedEnabled: Boolean = false,
    onApplySeed: (() -> Unit)? = null,
    promptSelectionEnabled: Boolean = false,
    onImportPromptSelection: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            onApplySeed?.let { applySeed ->
                IconButton(
                    onClick = applySeed,
                    enabled = seedEnabled,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Default.Eco, stringResource(R.string.apply_history_seed), Modifier.size(18.dp))
                }
            }
            FilledTonalButton(
                onClick = onImageActions,
                enabled = imageActionsEnabled,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Default.MoreHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(3.dp))
                Text(stringResource(R.string.image_actions), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            OutlinedButton(
                onClick = onImportPromptSelection ?: onImportInformation,
                enabled = if (onImportPromptSelection != null) promptSelectionEnabled else informationEnabled,
                modifier = Modifier.weight(1f).height(40.dp),
                contentPadding = PaddingValues(horizontal = 5.dp, vertical = 0.dp),
            ) {
                Icon(
                    if (onImportPromptSelection != null) Icons.AutoMirrored.Filled.PlaylistAdd else Icons.Default.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    stringResource(if (onImportPromptSelection != null) R.string.cherry_pick_prompts else R.string.import_information),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (onImportPromptSelection != null) {
            OutlinedButton(
                onClick = onImportInformation,
                enabled = informationEnabled,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
            ) {
                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.import_information), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}

/** Shared by Gallery, Result and History; never falls back to thumbnails. */
@Composable
fun ImageActionChoices(reference: String, viewModel: MainViewModel, onApplied: () -> Unit) {
    val session by viewModel.session.collectAsState()
    val uri = remember(reference) { imageReferenceUri(reference) }
    val available = rememberOriginalImageAvailable(reference)
    val actions = ImageActionPolicy.availability(available, session?.generationSettings?.modelId)
    var pending by remember(reference) { mutableStateOf<ImageInputState?>(null) }
    var chooseType by remember(reference) { mutableStateOf(false) }
    var inpaint by remember(reference) { mutableStateOf(false) }
    var confirmInpaint by remember(reference) { mutableStateOf(false) }
    if (confirmInpaint) AlertDialog(onDismissRequest = { confirmInpaint = false },
        title = { Text(stringResource(R.string.inpaint_title)) },
        text = { Text(stringResource(R.string.image_action_replace)) },
        confirmButton = { TextButton(onClick = { confirmInpaint = false; inpaint = true }) { Text(stringResource(R.string.load)) } },
        dismissButton = { TextButton(onClick = { confirmInpaint = false }) { Text(stringResource(R.string.cancel)) } })
    if (inpaint) InpaintEditor(uri.toString(), onDismiss = { inpaint = false }) { input, generate ->
        viewModel.useImageInput(input)
        inpaint = false
        onApplied()
        if (generate) viewModel.generate()
    }
    fun select(input: ImageInputState) {
        if (session?.generationSettings?.imageInput != null) pending = input
        else { viewModel.useImageInput(input); onApplied() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!available) Text(stringResource(R.string.original_missing))
        ImageActionButton(stringResource(R.string.image_to_image), Icons.Default.Image, actions.imageToImage) {
            select(ImageInputState(uri.toString()))
        }
        ImageActionButton(stringResource(R.string.vibe_transfer), Icons.Default.Waves, actions.vibeTransfer) {
            select(ImageInputState(uri.toString(), mode = ImageInputMode.VIBE_TRANSFER))
        }
        ImageActionButton(
            label = stringResource(R.string.precise_reference),
            icon = Icons.Default.CenterFocusStrong,
            enabled = actions.preciseReference,
            onClick = { chooseType = true },
        )
        ImageActionButton(
            label = stringResource(R.string.inpaint_title),
            icon = Icons.Default.Brush,
            enabled = actions.inpaint,
            onClick = { if (session?.generationSettings?.imageInput != null) confirmInpaint = true else inpaint = true },
        )
        Text(stringResource(R.string.inpaint_models), style = MaterialTheme.typography.bodySmall)
    }
    if (chooseType) AlertDialog(onDismissRequest = { chooseType = false },
        title = { Text(stringResource(R.string.precise_reference)) },
        text = { Column { PreciseReferenceType.entries.forEach { type ->
            TextButton(onClick = { chooseType = false; select(ImageInputState(uri.toString(), mode = ImageInputMode.PRECISE_REFERENCE, strength = 1f, preciseType = type)) }) {
                Text(stringResource(when (type) {
                    PreciseReferenceType.CHARACTER_AND_STYLE -> R.string.image_action_character_style
                    PreciseReferenceType.CHARACTER -> R.string.image_action_character
                    PreciseReferenceType.STYLE -> R.string.image_action_style
                }))
            }
        } } }, confirmButton = {})
    pending?.let { input -> AlertDialog(onDismissRequest = { pending = null },
        title = { Text(stringResource(R.string.image_actions)) },
        text = { Text(stringResource(R.string.image_action_replace)) },
        confirmButton = { TextButton(onClick = { pending = null; viewModel.useImageInput(input); onApplied() }) { Text(stringResource(R.string.load)) } },
        dismissButton = { TextButton(onClick = { pending = null }) { Text(stringResource(R.string.cancel)) } }) }
}

@Composable
private fun ImageActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        enabled = enabled,
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

@Preview(name = "Image actions · small phone", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Composable
private fun ImageActionButtonsSmallPhonePreview() {
    MaterialTheme {
        Surface {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ImageActionButton("Image2Image", Icons.Default.Image) {}
                ImageActionButton("Vibe Transfer", Icons.Default.Waves) {}
                ImageActionButton("Precise Reference", Icons.Default.CenterFocusStrong) {}
                ImageActionButton("인페인트", Icons.Default.Brush) {}
            }
        }
    }
}

@Preview(name = "Image card actions · small phone", widthDp = 320, fontScale = 1.3f, showBackground = true)
@Composable
private fun ImageCardActionsSmallPhonePreview() {
    MaterialTheme {
        Surface(Modifier.padding(12.dp)) {
            ImageCardActions(true, true, {}, {})
        }
    }
}
