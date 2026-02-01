import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream


@Composable
fun ImportImageScreen(
    onImageImported: (File) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            val inputStream = context.contentResolver.openInputStream(uri)
            val tempFile = File.createTempFile("import_", ".jpg", context.cacheDir)

            inputStream?.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            onImageImported(tempFile)
        } else {
            onCancel()
        }
    }

    LaunchedEffect(Unit) {
        launcher.launch("image/*")
    }
}
