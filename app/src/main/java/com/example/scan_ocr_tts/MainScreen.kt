package com.example.scan_ocr_tts

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.net.Uri

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.padding

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
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

         Text(
             text = "Version: $versionName  Beta",
             modifier = Modifier.padding(bottom = 32.dp), // 👈 Ajouter du padding en bas
             style = androidx.compose.material3.MaterialTheme.typography.titleMedium, // 👈 Texte plus grand
             color = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
         )

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
             modifier = Modifier.fillMaxWidth(0.6f)
         ) {
             Text("User_Manual")
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
             modifier = Modifier.fillMaxWidth(0.6f)
         ) {
             Text("Manuel d'utilisation")
         }


//        Button(
//            onClick = {
//                Toast.makeText(
//                    context,
//                    "Scanner une page - Fonctionnalité à venir prochainement",
//                    Toast.LENGTH_LONG
//                ).show()
//
////                if (hasCameraPermission) {
////                    onScanClick()
////                } else {
////                    permissionLauncher.launch(Manifest.permission.CAMERA)
////                }
//            }
//
//        ) {
//            Text("Scanner une page")
//        }


//        Spacer(modifier = Modifier.height(16.dp))



//        Button(
//            onClick = {
//
//                Toast.makeText(
//                    context,
//                    "Importer une image- Fonctionnalité à venir prochainement",
//                    Toast.LENGTH_LONG
//                ).show()
//
//            //    onImportClick()
//
//
//            }
//        ) {
//            Text("Importer une image")
//        }
//
       Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            }
        ) {
            Text("Open a PDF file")
        }

         Spacer(modifier = Modifier.height(16.dp))

         Button(
             onClick = { (context as? android.app.Activity)?.finish() },
             modifier = Modifier.fillMaxWidth(0.4f),
             colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                 containerColor = androidx.compose.ui.graphics.Color(0xFFB00020)
             )
         ) {
             Text("Exit")
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