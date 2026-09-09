package fumi.day.literalfilm.editor

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fumi.day.literalfilm.data.export.ExportFileManager
import fumi.day.literalfilm.data.export.GallerySaver
import fumi.day.literalfilm.data.presets.PresetRepository
import fumi.day.literalfilm.domain.model.FilmPreset
import fumi.day.literalfilm.render.BitmapExifDecoder
import fumi.day.literalfilm.render.OffscreenRenderer
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PREVIEW_MAX_DIMENSION = 1600
private const val THUMBNAIL_MAX_DIMENSION = 160

data class FilmEditorUiState(
    val sourceUri: Uri? = null,
    val previewBitmap: Bitmap? = null,
    val isDecoding: Boolean = false,
    val isProcessing: Boolean = false,
    val selectedPreset: FilmPreset? = null,
    val presets: List<FilmPreset> = emptyList(),
    val presetThumbnails: Map<String, Bitmap> = emptyMap(),
    val isEditorOpen: Boolean = false,
    val editingPreset: FilmPreset = FilmPreset.EDITOR_DEFAULT,
)

sealed interface FilmEditorEvent {
    data class Saved(val uri: Uri) : FilmEditorEvent
    data class ExportReady(val shareIntent: Intent) : FilmEditorEvent
    data class Error(val message: String) : FilmEditorEvent
}

class FilmEditorViewModel(private val presetRepository: PresetRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(FilmEditorUiState())
    val uiState: StateFlow<FilmEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<FilmEditorEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<FilmEditorEvent> = _events.asSharedFlow()

    /** Downscaled copy of the current preview, reused as the source for every thumbnail render. */
    private var thumbnailSourceBitmap: Bitmap? = null

    init {
        viewModelScope.launch { presetRepository.ensureSeeded() }
        viewModelScope.launch {
            presetRepository.presets.collect { list ->
                val previous = _uiState.value.presets
                _uiState.update { it.copy(presets = list) }
                regenerateThumbnailsForListChange(previous, list)
            }
        }
    }

    fun setSourceUri(context: Context, uri: Uri) {
        if (_uiState.value.sourceUri == uri) return
        // A freshly shared photo starts unfiltered — don't carry over the previous photo's pick.
        _uiState.update {
            it.copy(
                sourceUri = uri,
                isDecoding = true,
                previewBitmap = null,
                selectedPreset = null,
                presetThumbnails = emptyMap(),
            )
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapExifDecoder.decode(appContext, uri, PREVIEW_MAX_DIMENSION) }
            _uiState.update { it.copy(previewBitmap = bitmap, isDecoding = false) }
            regenerateAllThumbnails(bitmap)
        }
    }

    fun selectPreset(preset: FilmPreset) {
        _uiState.update { it.copy(selectedPreset = if (it.selectedPreset?.id == preset.id) null else preset) }
    }

    fun openEditor() {
        _uiState.update { it.copy(editingPreset = it.selectedPreset ?: FilmPreset.EDITOR_DEFAULT, isEditorOpen = true) }
    }

    fun closeEditor() {
        _uiState.update { it.copy(isEditorOpen = false) }
    }

    fun updateEditingPreset(transform: (FilmPreset) -> FilmPreset) {
        _uiState.update { it.copy(editingPreset = transform(it.editingPreset)) }
    }

    fun updateEditingTemperature(temperature: Float) {
        updateEditingPreset {
            it.copy(
                colorCast = floatArrayOf(1f + temperature * 0.22f, 1f, 1f - temperature * 0.22f),
                colorCastStrength = kotlin.math.abs(temperature) * 0.65f,
            )
        }
    }

    fun saveEditingPresetAs(name: String) {
        val newPreset = _uiState.value.editingPreset.copy(id = UUID.randomUUID().toString(), displayName = name)
        viewModelScope.launch {
            presetRepository.addPreset(newPreset)
            _uiState.update { it.copy(selectedPreset = newPreset, isEditorOpen = false) }
        }
    }

    fun deletePreset(id: String) {
        viewModelScope.launch {
            presetRepository.deletePreset(id)
            if (_uiState.value.selectedPreset?.id == id) {
                _uiState.update { it.copy(selectedPreset = null) }
            }
        }
    }

    fun save(context: Context, preset: FilmPreset? = null) =
        renderFullResThen(context, preset) { appContext, bitmap ->
            val uri = GallerySaver.save(appContext, bitmap)
            _events.emit(FilmEditorEvent.Saved(uri))
        }

    fun export(context: Context, preset: FilmPreset? = null) =
        renderFullResThen(context, preset) { appContext, bitmap ->
            val file = ExportFileManager.writeExportFile(appContext, bitmap)
            val shareIntent = ExportFileManager.buildShareIntent(appContext, file)
            _events.emit(FilmEditorEvent.ExportReady(shareIntent))
        }

    /** [presetOverride] lets the manual editor save/export the in-progress `editingPreset` instead of the main screen's `selectedPreset`. */
    private fun renderFullResThen(context: Context, presetOverride: FilmPreset?, action: suspend (Context, Bitmap) -> Unit) {
        val sourceUri = _uiState.value.sourceUri ?: return
        if (_uiState.value.isProcessing) return

        val preset = presetOverride ?: _uiState.value.selectedPreset ?: FilmPreset.IDENTITY
        val appContext = context.applicationContext

        _uiState.update { it.copy(isProcessing = true) }
        viewModelScope.launch {
            try {
                val resultBitmap =
                    withContext(Dispatchers.Default) {
                        val fullResSource = BitmapExifDecoder.decode(appContext, sourceUri, maxDimension = null)
                        OffscreenRenderer.render(fullResSource, preset)
                    }
                action(appContext, resultBitmap)
            } catch (t: Throwable) {
                _events.emit(FilmEditorEvent.Error(t.message ?: "処理に失敗しました"))
            } finally {
                _uiState.update { it.copy(isProcessing = false) }
            }
        }
    }

    private fun regenerateAllThumbnails(previewBitmap: Bitmap) {
        val source = downscale(previewBitmap, THUMBNAIL_MAX_DIMENSION)
        thumbnailSourceBitmap = source
        viewModelScope.launch(Dispatchers.Default) {
            val presets = _uiState.value.presets
            val thumbnails = presets.associate { it.id to OffscreenRenderer.render(source, it) }
            _uiState.update { it.copy(presetThumbnails = thumbnails) }
        }
    }

    private fun regenerateThumbnailsForListChange(previous: List<FilmPreset>, current: List<FilmPreset>) {
        val source = thumbnailSourceBitmap ?: return
        val previousIds = previous.map { it.id }.toSet()
        val currentIds = current.map { it.id }.toSet()
        val added = current.filter { it.id !in previousIds }
        val removedIds = previousIds - currentIds
        if (added.isEmpty() && removedIds.isEmpty()) return

        viewModelScope.launch(Dispatchers.Default) {
            val newThumbnails = added.associate { it.id to OffscreenRenderer.render(source, it) }
            _uiState.update {
                it.copy(presetThumbnails = (it.presetThumbnails - removedIds) + newThumbnails)
            }
        }
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val width = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

class FilmEditorViewModelFactory(private val appContext: Context) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return FilmEditorViewModel(PresetRepository(appContext)) as T
    }
}
