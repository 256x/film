package fumi.day.literalfilm

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import fumi.day.literalfilm.data.export.ExportFileManager
import fumi.day.literalfilm.editor.FilmEditorScreen
import fumi.day.literalfilm.editor.FilmEditorViewModel
import fumi.day.literalfilm.editor.FilmEditorViewModelFactory
import fumi.day.literalfilm.theme.LiteralFilmTheme

class MainActivity : ComponentActivity() {

    // Compose only recomposes on observed state changes — plain `intent` reads inside
    // setContent do NOT update when onNewIntent later mutates the Activity's intent
    // (e.g. sharing a second photo while this Activity instance is reused). Route new
    // intents through this state instead so the LaunchedEffect below actually re-fires.
    private val sharedUriState = mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Best-effort cleanup of last session's exported temp files (see ExportFileManager kdoc).
        ExportFileManager.cleanupStaleExports(applicationContext)

        sharedUriState.value = extractSharedImageUri(intent)

        setContent {
            val context = LocalContext.current
            val viewModel: FilmEditorViewModel =
                viewModel(factory = FilmEditorViewModelFactory(context.applicationContext))
            val sharedUri by sharedUriState

            LaunchedEffect(sharedUri) { sharedUri?.let { viewModel.setSourceUri(context, it) } }

            LiteralFilmTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    FilmEditorScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedUriState.value = extractSharedImageUri(intent)
    }

    private fun extractSharedImageUri(intent: Intent): Uri? {
        if (intent.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
