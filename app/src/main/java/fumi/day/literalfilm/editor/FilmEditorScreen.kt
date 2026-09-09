package fumi.day.literalfilm.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fumi.day.literalfilm.domain.model.FilmPreset

@Composable
fun FilmEditorScreen(viewModel: FilmEditorViewModel, modifier: Modifier = Modifier) {
    val uiState by viewModel.uiState.collectAsState()
    val previewBitmap = uiState.previewBitmap
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is FilmEditorEvent.Saved -> Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
                is FilmEditorEvent.ExportReady -> context.startActivity(event.shareIntent)
                is FilmEditorEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    if (uiState.isEditorOpen && previewBitmap != null) {
        PresetEditorScreen(
            previewBitmap = previewBitmap,
            editingPreset = uiState.editingPreset,
            isProcessing = uiState.isProcessing,
            onUpdate = viewModel::updateEditingPreset,
            onUpdateTemperature = viewModel::updateEditingTemperature,
            onBack = viewModel::closeEditor,
            onSavePhoto = { viewModel.save(context, uiState.editingPreset) },
            onExportPhoto = { viewModel.export(context, uiState.editingPreset) },
            onSavePreset = viewModel::saveEditingPresetAs,
            modifier = modifier,
        )
        return
    }

    var isComparingOriginal by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            previewBitmap != null ->
                GLPreviewView(
                    bitmap = previewBitmap,
                    preset = if (isComparingOriginal) null else uiState.selectedPreset,
                    modifier =
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    isComparingOriginal = true
                                    tryAwaitRelease()
                                    isComparingOriginal = false
                                }
                            )
                        },
                )
            uiState.isDecoding -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            else ->
                Text(
                    text = "写真アプリの共有メニューから開いてください",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
        }

        if (previewBitmap != null) {
            TopActionRow(
                isProcessing = uiState.isProcessing,
                onSave = { viewModel.save(context) },
                onExport = { viewModel.export(context) },
                modifier = Modifier.align(Alignment.TopEnd).safeDrawingPadding(),
            )

            BottomPresetBar(
                presets = uiState.presets,
                thumbnails = uiState.presetThumbnails,
                selectedPresetId = uiState.selectedPreset?.id,
                onWrenchTap = viewModel::openEditor,
                onSelect = viewModel::selectPreset,
                onDelete = viewModel::deletePreset,
                modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding(),
            )
        }

        if (uiState.isProcessing) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Composable
private fun TopActionRow(isProcessing: Boolean, onSave: () -> Unit, onExport: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.padding(4.dp)) {
        IconButton(onClick = onSave, enabled = !isProcessing) { Icon(Icons.Default.Save, contentDescription = "Save") }
        IconButton(onClick = onExport, enabled = !isProcessing) { Icon(Icons.Default.Share, contentDescription = "Export") }
    }
}

@Composable
private fun BottomPresetBar(
    presets: List<FilmPreset>,
    thumbnails: Map<String, Bitmap>,
    selectedPresetId: String?,
    onWrenchTap: () -> Unit,
    onSelect: (FilmPreset) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<FilmPreset?>(null) }

    Row(
        modifier = modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onWrenchTap) { Icon(Icons.Default.Build, contentDescription = "手動調整") }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets, key = { it.id }) { preset ->
                PresetThumbnail(
                    preset = preset,
                    bitmap = thumbnails[preset.id],
                    isSelected = preset.id == selectedPresetId,
                    onClick = { onSelect(preset) },
                    onLongClick = { pendingDelete = preset },
                )
            }
        }
    }

    pendingDelete?.let { preset ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("削除しますか?") },
            text = { Text(preset.displayName) },
            confirmButton = {
                TextButton(onClick = { onDelete(preset.id); pendingDelete = null }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun PresetThumbnail(preset: FilmPreset, bitmap: Bitmap?, isSelected: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier =
            Modifier.size(64.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.BottomCenter,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap.asImageBitmap(), contentDescription = preset.displayName, modifier = Modifier.fillMaxSize())
        }
        Text(
            text = preset.displayName,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)).fillMaxWidth(),
        )
    }
}
