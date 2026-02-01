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
        Button(
            onClick = {
                if (hasCameraPermission) {
                    onScanClick()
                } else {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                }
            }

        ) {
            Text("Scanner une page")
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = { onImportClick() }
        ) {
            Text("Importer une image")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                pdfPickerLauncher.launch(arrayOf("application/pdf"))
            }
        ) {
            Text("Importer un PDF")
        }

         Spacer(modifier = Modifier.height(16.dp))

         Button(
             onClick = { (context as? android.app.Activity)?.finish() },
             modifier = Modifier.fillMaxWidth(0.4f),
             colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                 containerColor = androidx.compose.ui.graphics.Color(0xFFB00020)
             )
         ) {
             Text("Quitter")
         }


    }


 }

private fun renderPdfPageToFile(context: Context, uri: Uri): File {
    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
    val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)

    val page = renderer.openPage(0)

    val bitmap = android.graphics.Bitmap.createBitmap(
        page.width,
        page.height,
        android.graphics.Bitmap.Config.ARGB_8888
    )

    page.render(bitmap, null, null, android.graphics.pdf.PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    renderer.close()
    fileDescriptor.close()

    val file = File(context.cacheDir, "pdf_page.png")
    FileOutputStream(file).use {
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
    }

    return file
}

