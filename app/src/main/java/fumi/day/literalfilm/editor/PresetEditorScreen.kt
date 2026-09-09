package fumi.day.literalfilm.editor

import android.graphics.Bitmap
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import fumi.day.literalfilm.domain.model.FilmPreset

@Composable
fun PresetEditorScreen(
    previewBitmap: Bitmap,
    editingPreset: FilmPreset,
    isProcessing: Boolean,
    onUpdate: ((FilmPreset) -> FilmPreset) -> Unit,
    onUpdateTemperature: (Float) -> Unit,
    onBack: () -> Unit,
    onSavePhoto: () -> Unit,
    onExportPhoto: () -> Unit,
    onSavePreset: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showNameDialog by remember { mutableStateOf(false) }
    var temperature by remember { mutableStateOf(0f) }
    var isComparingOriginal by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る") }
            Row {
                IconButton(onClick = onSavePhoto, enabled = !isProcessing) { Icon(Icons.Default.Save, contentDescription = "Save") }
                IconButton(onClick = onExportPhoto, enabled = !isProcessing) { Icon(Icons.Default.Share, contentDescription = "Export") }
                IconButton(onClick = { showNameDialog = true }) { Icon(Icons.Default.Settings, contentDescription = "プリセット保存") }
            }
        }

        GLPreviewView(
            bitmap = previewBitmap,
            preset = if (isComparingOriginal) null else editingPreset,
            modifier =
                Modifier.weight(0.55f).fillMaxWidth().pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            isComparingOriginal = true
                            tryAwaitRelease()
                            isComparingOriginal = false
                        }
                    )
                },
        )

        // Both this and the preview above must be weighted — an un-weighted scrollable
        // Column in a Column is measured with unbounded height, so it just grows to fit
        // all its content (pushing the weighted preview down to a sliver) instead of
        // being capped to its share of the screen and scrolling internally.
        Column(
            modifier =
                Modifier.weight(0.45f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            EditorSlider("Brightness", editingPreset.brightness, -1f..1f) { v -> onUpdate { it.copy(brightness = v) } }
            EditorSlider("Contrast", editingPreset.contrast, -1f..1f) { v -> onUpdate { it.copy(contrast = v) } }
            EditorSlider("Saturation", editingPreset.saturation, 0f..2f) { v -> onUpdate { it.copy(saturation = v) } }
            EditorSlider("Shadow Lift", editingPreset.shadowLift, -1f..1f) { v -> onUpdate { it.copy(shadowLift = v) } }
            EditorSlider("Highlight Rolloff", editingPreset.highlightRolloff, -1f..1f) { v -> onUpdate { it.copy(highlightRolloff = v) } }
            EditorSlider("Structure", editingPreset.structure, 0f..1f) { v -> onUpdate { it.copy(structure = v) } }
            EditorSlider("Sharpness", editingPreset.sharpness, 0f..1f) { v -> onUpdate { it.copy(sharpness = v) } }
            EditorSlider("Grain", editingPreset.grainIntensity, 0f..1f) { v -> onUpdate { it.copy(grainIntensity = v) } }
            EditorSlider("Vignette", editingPreset.vignetteIntensity, 0f..1f) { v -> onUpdate { it.copy(vignetteIntensity = v) } }
            EditorSlider("Temperature", temperature, -1f..1f) { v ->
                temperature = v
                onUpdateTemperature(v)
            }

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Monochrome", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = editingPreset.monochrome, onCheckedChange = { checked -> onUpdate { it.copy(monochrome = checked) } })
            }
        }
    }

    if (showNameDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("プリセット名") },
            text = { TextField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) onSavePreset(name) }, enabled = name.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("キャンセル") } },
        )
    }
}

@Composable
private fun EditorSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}
