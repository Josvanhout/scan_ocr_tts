package com.example.scan_ocr_tts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import java.io.File
import java.io.FileOutputStream

@Composable
fun OcrScreenFromPdf(
    pdfUri: Uri,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.applicationContext.getSharedPreferences("ocr_settings", Context.MODE_PRIVATE)

    var useHighRes by remember { mutableStateOf(false) }
    var imageFile by remember { mutableStateOf<File?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }

    var thresholdBias by remember { mutableStateOf(prefs.getFloat("thresholdBias", 50f)) }
    var rectPadding by remember { mutableStateOf(prefs.getFloat("rectPadding", 12f)) }
    var contrastBoost by remember { mutableStateOf(prefs.getFloat("contrastBoost", 1.2f)) }
    var speechRate by remember { mutableStateOf(prefs.getFloat("speechRate", 1.0f)) }

    DisposableEffect(Unit) {
        onDispose {
            prefs.edit()
                .putFloat("thresholdBias", thresholdBias)
                .putFloat("rectPadding", rectPadding)
                .putFloat("contrastBoost", contrastBoost)
                .commit()

            Log.d("PREFS_DEBUG", "SAVED ON DISPOSE")
        }
    }


    LaunchedEffect(pdfUri) {
        pdfUri?.let { uri ->
            try {
                val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return@let
                val renderer = PdfRenderer(fileDescriptor)

                totalPages = renderer.pageCount

                Log.d("NAV_DEBUG", "PDF chargé → pages = $totalPages")

                renderer.close()
                fileDescriptor.close()
            } catch (e: Exception) {
                Log.e("NAV_DEBUG", "Erreur lecture PDF", e)
            }
        }
    }

    LaunchedEffect(pdfUri, currentPageIndex) {
        pdfUri?.let { uri ->
            try {
                Log.d("PAGE_TRACE", "RENDER start pageIndex=$currentPageIndex uri=$uri")

                // AJOUTE useHighRes comme 4ème paramètre
                val (file, pageCount) = renderPdfPageToFile(
                    context,
                    uri,
                    currentPageIndex,
                    useHighRes  // ← AJOUTE ce paramètre
                )

                imageFile = file
                Log.d("PAGE_TRACE", "RENDER done pageIndex=$currentPageIndex file=${file.absolutePath}")

                totalPages = pageCount
                Log.d("NAV_DEBUG", "Page rendue = $currentPageIndex / $totalPages")
            } catch (e: Exception) {
                Log.e("NAV_DEBUG", "Erreur rendu page PDF", e)
            }
        }
    }



    imageFile?.let { file ->
        Log.d("PDF_FLOW", "Opening OcrScreen FROM PDF with uri=$pdfUri page=$currentPageIndex")

        OcrScreen(
            imageFile = file,
            pdfIdentity = pdfUri.toString(),
            thresholdBias = thresholdBias,
            onThresholdChange = {
                thresholdBias = it
                val ok = prefs.edit().putFloat("thresholdBias", it).commit()
                Log.d("NAVS_DEBUG", "SAVE thresholdBias=$it commit=$ok")
            },
            rectPadding = rectPadding,
            onRectPaddingChange = {
                rectPadding = it
                prefs.edit().putFloat("rectPadding", it).commit()
            },
            contrastBoost = contrastBoost,
            onContrastBoostChange = {
                contrastBoost = it
                prefs.edit().putFloat("contrastBoost", it).commit()
            },
            onNext = onNext,
            speechRate = speechRate,
            onSpeechRateChange = {
                speechRate = it
                prefs.edit().putFloat("speechRate", it).apply()
            },

            onLeavingScreen = {
                prefs.edit()
                    .putFloat("thresholdBias", thresholdBias)
                    .putFloat("rectPadding", rectPadding)
                    .putFloat("contrastBoost", contrastBoost)
                    .putFloat("speechRate", speechRate)

                    .commit()

                Log.d("PREFS_DEBUG", "SAVED ON LEAVE OCR SCREEN")
            },

            onPreviousPage = {
                Log.d("PAGE_TRACE", "CLICK < : currentPageIndex(before)=$currentPageIndex")
                if (currentPageIndex > 0) currentPageIndex--
                Log.d("PAGE_TRACE", "CLICK < : currentPageIndex(after)=$currentPageIndex")
            },
            onNextPage = {
                Log.d("PAGE_TRACE", "CLICK > : currentPageIndex(before)=$currentPageIndex totalPages=$totalPages")
                if (currentPageIndex < totalPages - 1) currentPageIndex++
                Log.d("PAGE_TRACE", "CLICK > : currentPageIndex(after)=$currentPageIndex")
            },
            onGoToPage = { pageIndex ->
                currentPageIndex = pageIndex.coerceIn(0, totalPages - 1)
            },

            currentPageIndex = currentPageIndex,
            totalPages = totalPages,
            useHighRes = useHighRes,  // ← AJOUTE
            onUseHighResChange = { useHighRes = it }  // ← AJOUTE



        )
    }
}

private fun renderPdfPageToFile(
    context: Context,
    uri: Uri,
    pageIndex: Int,
    useHighRes: Boolean  // ← NOUVEAU paramètre
): Pair<File, Int> {
    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
    val renderer = PdfRenderer(fileDescriptor)
    val pageCount = renderer.pageCount
    val page = renderer.openPage(pageIndex)

    // TEST SIMPLE : juste augmenter de 2x
    val scaleFactor = if (useHighRes) 1.5f else 1.0f

    val bitmap = Bitmap.createBitmap(
        (page.width * scaleFactor).toInt(),  // ← CHANGEMENT
        (page.height * scaleFactor).toInt(), // ← CHANGEMENT
        Bitmap.Config.ARGB_8888
    )

    // IMPORTANT : ajouter une matrice pour l'échelle
    val matrix = Matrix().apply {
        postScale(scaleFactor, scaleFactor)
    }

    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) // ← CHANGEMENT (ajout matrix)

    page.close()
    renderer.close()
    fileDescriptor.close()

    val file = File(context.cacheDir, "pdf_page_$pageIndex.png")
    FileOutputStream(file).use {
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }

    // Nettoyer
    bitmap.recycle() // ← IMPORTANT à ajouter aussi

    return Pair(file, pageCount)
}


