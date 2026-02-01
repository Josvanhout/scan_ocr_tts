package com.example.scan_ocr_tts

import ImportImageScreen
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.scan_ocr_tts.ui.theme.Scan_ocr_ttsTheme
import java.io.File
import org.opencv.android.OpenCVLoader
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext


import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.saveable.rememberSaveable


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Impossible de charger OpenCV")
        } else {
            Log.d("OpenCV", "OpenCV chargé avec succès")
        }

        setContent {
            Scan_ocr_ttsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var currentScreen by remember { mutableStateOf("main") }
                    LaunchedEffect(currentScreen) {
                        android.util.Log.d("NAV_DEBUG", "Changement d'écran -> $currentScreen")
                    }
                    var capturedImageFile by remember { mutableStateOf<File?>(null) }
                    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
                    var pdfPageIndex by remember { mutableStateOf(0) }

                    val prefs = applicationContext.getSharedPreferences("ocr_settings", MODE_PRIVATE)

                    LaunchedEffect(Unit) {
                        val lastUri = prefs.getString("last_pdf_uri", null)
                        val lastPage = prefs.getInt("last_pdf_page", 0)

                        if (lastUri != null) {
                            val uri = Uri.parse(lastUri)

                            try {
                                contentResolver.openInputStream(uri)?.close()

                                selectedPdfUri = uri
                                pdfPageIndex = lastPage
                                currentScreen = "ocr_pdf"

                                Log.d("NAV_DEBUG", "Reprise automatique PDF page=$lastPage")

                            } catch (e: Exception) {
                                Log.d("NAV_DEBUG", "PDF sauvegardé introuvable, reset")

                                prefs.edit()
                                    .remove("last_pdf_uri")
                                    .remove("last_pdf_page")
                                    .apply()
                            }
                        }
                    }





//                    var thresholdBias by remember { mutableStateOf(prefs.getFloat("thresholdBias", 50f)) }
//                    var rectPadding by remember { mutableStateOf(prefs.getFloat("rectPadding", 28f)) }
//                    var contrastBoost by remember { mutableStateOf(prefs.getFloat("contrastBoost", 1.2f)) }
//                    var speechRate by remember { mutableFloatStateOf(prefs.getFloat("speechRate", 1.0f)) }


                    when (currentScreen) {



                        "main" -> MainScreen(
                            modifier = Modifier.fillMaxSize(),
                            onScanClick = { currentScreen = "camera" },
                            onImportClick = { currentScreen = "import" },
                            onPdfSelected = { uri ->
                                contentResolver.takePersistableUriPermission(
                                    uri,
                                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )

                                selectedPdfUri = uri
                                currentScreen = "ocr_pdf"
                            }


                        )


                        "camera" -> CameraScreen(
                            onPhotoCaptured = { file ->
                                capturedImageFile = file
                                currentScreen = "preview"
                            }
                        )

                        "preview" -> if (selectedPdfUri == null) capturedImageFile?.let { file ->

                        }

                        "ocr_pdf" -> {
                            selectedPdfUri?.let { uri ->
                                OcrScreenFromPdf(
                                    pdfUri = uri,
                                    onNext = {
                                        val prefs = applicationContext.getSharedPreferences("ocr_settings", MODE_PRIVATE)

                                        prefs.edit()
                                            .putString("last_pdf_uri", uri.toString())
                                            .putInt("last_pdf_page", pdfPageIndex)
                                            .apply()

                                        currentScreen = "main"
                                    },
                                    onExit = {
                                        currentScreen = "main"
                                    }
                                )
                            }
                        }


                    }






                }
            }
        }


    }
//    private fun renderPdfPageToFile(context: android.content.Context, uri: Uri, pageIndex: Int): File {
//
//        val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
//        val renderer = android.graphics.pdf.PdfRenderer(fileDescriptor)
//
//        val page = renderer.openPage(pageIndex)
//
//        // Facteur de qualité : 2.5 à 3 = très bon OCR
//        val scale = 3.0f
//
//        val bitmap = Bitmap.createBitmap(
//            (page.width * scale).toInt(),
//            (page.height * scale).toInt(),
//            Bitmap.Config.ARGB_8888
//        )
//
//        val matrix = android.graphics.Matrix().apply {
//            postScale(scale, scale)
//        }
//
//        page.render(
//            bitmap,
//            null,
//            matrix,
//            PdfRenderer.Page.RENDER_MODE_FOR_PRINT
//        )
//
//        page.close()
//        renderer.close()
//        fileDescriptor.close()
//
//        val file = File(context.cacheDir, "pdf_page.png")
//        java.io.FileOutputStream(file).use {
//            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
//        }
//
//        return file
//    }

}
