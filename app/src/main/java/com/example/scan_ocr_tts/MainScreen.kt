package com.example.scan_ocr_tts



import android.os.Environment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.Manifest
import android.R.attr.bitmap
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.net.Uri

import android.content.Context
import android.util.Log
import android.widget.Toast

import java.io.File
import java.io.FileOutputStream

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit,
    onImportClick: () -> Unit,
    onPdfSelected: (Uri) -> Unit   // 👈 AJOUT
)
 {
    val context = LocalContext.current

     // Récupérer la versionName depuis le package
     val packageInfo = try {
         context.packageManager.getPackageInfo(context.packageName, 0)
     } catch (e: Exception) {
         null
     }
     val versionName = packageInfo?.versionName ?: "N/A"



    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }



     val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

     val pdfPickerLauncher = rememberLauncherForActivityResult(
         contract = ActivityResultContracts.OpenDocument(),
         onResult = { uri: android.net.Uri? ->

             uri?.let { pdfUri ->
                 onPdfSelected(pdfUri)

             }
         }
     )


     Column(
        modifier = modifier
            .fillMaxSize()
            .background(androidx.compose.material3.MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

         Text(
             text = "Scan OCR TTS",
             style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
             fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
             color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
             modifier = Modifier.padding(bottom = 8.dp)
         )

         Text(
             text = "Version: $versionName",
             modifier = Modifier.padding(bottom = 32.dp),
             style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
             color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
         )

         androidx.compose.material3.Card(
             modifier = Modifier.fillMaxWidth(0.9f),
             shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
             colors = androidx.compose.material3.CardDefaults.cardColors(
                 containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
             ),
             elevation = androidx.compose.material3.CardDefaults.cardElevation(defaultElevation = 6.dp)
         ) {
             Column(
                 modifier = Modifier
                     .fillMaxWidth()
                     .padding(24.dp),
                 horizontalAlignment = Alignment.CenterHorizontally,
                 verticalArrangement = Arrangement.spacedBy(16.dp)
             ) {

                 Button(
                     onClick = {
                         val fileName = "User_Manual.pdf"
                         val file = File(context.cacheDir, fileName)

                         // Copie du fichier des assets vers le cache pour obtenir un Uri compatible
                         context.assets.open(fileName).use { input ->
                             FileOutputStream(file).use { output ->
                                 input.copyTo(output)
                             }
                         }

                         val uri = androidx.core.content.FileProvider.getUriForFile(
                             context,
                             "${context.packageName}.provider",
                             file
                         )

                         // On envoie l'Uri à votre fonction de sélection existante
                         onPdfSelected(uri)
                     },
                     modifier = Modifier.fillMaxWidth().height(56.dp),
                     shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                 ) {
                     Icon(androidx.compose.material.icons.Icons.Default.MenuBook, contentDescription = null)
                     Spacer(modifier = Modifier.width(12.dp))
                     Text("User Manual", fontWeight = FontWeight.Bold)
                 }

                 Button(
                     onClick = {
                         val fileName = "Manuel_d_utilisation.pdf"
                         val file = File(context.cacheDir, fileName)

                         // Copie du fichier des assets vers le cache pour obtenir un Uri compatible
                         context.assets.open(fileName).use { input ->
                             FileOutputStream(file).use { output ->
                                 input.copyTo(output)
                             }
                         }

                         val uri = androidx.core.content.FileProvider.getUriForFile(
                             context,
                             "${context.packageName}.provider",
                             file
                         )

                         // On envoie l'Uri à votre fonction de sélection existante
                         onPdfSelected(uri)
                     },
                     modifier = Modifier.fillMaxWidth().height(56.dp),
                     shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                 ) {
                     Icon(androidx.compose.material.icons.Icons.Default.MenuBook, contentDescription = null)
                     Spacer(modifier = Modifier.width(12.dp))
                     Text("Manuel d'utilisation", fontWeight = FontWeight.Bold)
                 }

                 androidx.compose.material3.HorizontalDivider(
                     modifier = Modifier.padding(vertical = 8.dp),
                     color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                 )

                 var lastPdfPath by remember { mutableStateOf<String?>(null) }

                 // Charger le dernier chemin depuis le JSON
                 LaunchedEffect(Unit) {
                     try {
                         // Chercher le fichier bookmarks.json (comme dans votre OcrScreen)
                         val jsonFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "bookmarks.json")
                         if (jsonFile.exists()) {
                             val jsonString = jsonFile.readText()
                             val jsonObject = org.json.JSONObject(jsonString)
                             lastPdfPath = jsonObject.optString("dernierLivre", null)
                         }
                     } catch (e: Exception) {
                         Log.e("MainScreen", "Erreur lecture JSON: ${e.message}")
                     }
                 }

                 Button(
                     onClick = {
                         lastPdfPath?.let { path ->
                             try {
                                 val uri = Uri.parse(path)
                                 onPdfSelected(uri)
                             } catch (e: Exception) {
                                 Toast.makeText(context, "Erreur avec le dernier PDF", Toast.LENGTH_SHORT).show()
                             }
                         } ?: run {
                             Toast.makeText(context, "Aucun dernier PDF trouvé", Toast.LENGTH_SHORT).show()
                         }
                     },
                     modifier = Modifier.fillMaxWidth().height(56.dp),
                     shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                     enabled = !lastPdfPath.isNullOrBlank()
                 ) {
                     Icon(androidx.compose.material.icons.Icons.Default.Book, contentDescription = null)
                     Spacer(modifier = Modifier.width(12.dp))
                     Text("Open last pdf", fontWeight = FontWeight.Bold)
                 }

                 Button(
                     onClick = {
                         pdfPickerLauncher.launch(arrayOf("application/pdf"))
                     },
                     modifier = Modifier.fillMaxWidth().height(56.dp),
                     shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                     colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                         containerColor = androidx.compose.ui.graphics.Color(0xFF2E7D32) // Material dark green
                     )
                 ) {
                     Icon(androidx.compose.material.icons.Icons.Default.FolderOpen, contentDescription = null)
                     Spacer(modifier = Modifier.width(12.dp))
                     Text("Open a PDF file", fontWeight = FontWeight.Bold)
                 }
             }
         }

         Spacer(modifier = Modifier.height(32.dp))

         androidx.compose.material3.TextButton(
             onClick = { (context as? android.app.Activity)?.finish() },
             modifier = Modifier.fillMaxWidth(0.4f)
         ) {
             Icon(
                 androidx.compose.material.icons.Icons.Default.ExitToApp,
                 contentDescription = null,
                 tint = androidx.compose.ui.graphics.Color(0xFFEF4444)
             )
             Spacer(modifier = Modifier.width(8.dp))
             Text(
                 "Exit",
                 color = androidx.compose.ui.graphics.Color(0xFFEF4444),
                 fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
             )
         }

    }


 }

private fun renderPdfPageToFile(context: Context, uri: Uri): File {
    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
    val page = renderer.openPage(0)

    // Création du bitmap
    val bitmap = android.graphics.Bitmap.createBitmap(
        page.width,
        page.height,
        //android.graphics.Bitmap.Config.ARGB_8888
                android.graphics.Bitmap.Config.RGB_565
    )

    // INITIALISATION DU FOND : Très important
    val canvas = android.graphics.Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE) // On peint tout en blanc

    // RENDU : Essayez de changer le mode ici si FOR_DISPLAY reste noir
    page.render(
        bitmap,
        null,
        null,
        android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_PRINT // <--- Changez DISPLAY par PRINT
    )

    page.close()
    renderer.close()
    fileDescriptor.close()

    val file = File(context.cacheDir, "pdf_page.png")
    FileOutputStream(file).use {
        // Optionnel : utilisez JPEG au lieu de PNG pour forcer l'absence de transparence
        // bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }



    return file
}

//private fun openAssetPdf(context: Context, fileName: String) {
//    try {
//        val file = File(context.cacheDir, fileName)
//        context.assets.open(fileName).use { input ->
//            FileOutputStream(file).use { output ->
//                input.copyTo(output)
//            }
//        }
//
//        val uri = androidx.core.content.FileProvider.getUriForFile(
//            context,
//            "${context.packageName}.provider",
//            file
//        )
//
//        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
//            setDataAndType(uri, "application/pdf")
//            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
//        }
//        context.startActivity(intent)
//    } catch (e: Exception) {
//        android.widget.Toast.makeText(context, "Erreur : impossible d'ouvrir le fichier", android.widget.Toast.LENGTH_SHORT).show()
//    }
//}