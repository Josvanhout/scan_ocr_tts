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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun OcrScreenFromPdf(
    pdfUri: Uri,
    onNext: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val prefs = context.applicationContext.getSharedPreferences("ocr_settings", Context.MODE_PRIVATE)

    var useHighRes by remember { mutableStateOf(false) }
    var highResScaleFactor by rememberSaveable { mutableStateOf(prefs.getFloat("highResScaleFactor", 1.3f)) }

    var imageFile by remember { mutableStateOf<File?>(null) }
    var currentPageIndex by remember { mutableStateOf(0) }
    var totalPages by remember { mutableStateOf(1) }

    var isDoublePageMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

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
                .putFloat("speechRate", speechRate)
                .putFloat("highResScaleFactor", highResScaleFactor)
                .commit()

            Log.d("PREFS_DEBUG", "SAVED ON DISPOSE")
        }
    }

    LaunchedEffect(pdfUri, currentPageIndex, useHighRes, highResScaleFactor, isDoublePageMode) {
        pdfUri?.let { uri ->
            try {
                Log.d("PAGE_TRACE", "RENDER start pageIndex=$currentPageIndex uri=$uri")

                // Utiliser un contexte de coroutine approprié pour les opérations lourdes
                withContext(Dispatchers.Main) {
                    isLoading = true
                }
                
                withContext(Dispatchers.IO) {
                    val (file, pageCount) = renderPdfPageToFile(
                        context,
                        uri,
                        currentPageIndex,
                        useHighRes,
                        highResScaleFactor,
                        isDoublePageMode
                    )

                    // Revenir sur le thread principal pour mettre à jour l'état
                    withContext(Dispatchers.Main) {
                        imageFile = file
                        totalPages = pageCount
                        isLoading = false
                    }
                }

                Log.d("PAGE_TRACE", "RENDER done pageIndex=$currentPageIndex")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // Propager l'annulation de la coroutine normalement
            } catch (e: Exception) {
                Log.e("NAV_DEBUG", "Erreur rendu page PDF", e)
            }
        }
    }





    Box(modifier = Modifier.fillMaxSize()) {
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

//            onLeavingScreen = {
//                prefs.edit()
//                    .putFloat("thresholdBias", thresholdBias)
//                    .putFloat("rectPadding", rectPadding)
//                    .putFloat("contrastBoost", contrastBoost)
//                    .putFloat("speechRate", speechRate)
//
//                    .commit()
//
//                Log.d("PREFS_DEBUG", "SAVED ON LEAVE OCR SCREEN")
//            },

            onPreviousPage = {
                val step = if (isDoublePageMode) 2 else 1
                Log.d("PAGE_TRACE", "CLICK < : currentPageIndex(before)=$currentPageIndex")
                if (currentPageIndex > 0) currentPageIndex = maxOf(0, currentPageIndex - step)
                Log.d("PAGE_TRACE", "CLICK < : currentPageIndex(after)=$currentPageIndex")
            },
            onNextPage = {
                val step = if (isDoublePageMode) 2 else 1
                Log.d("PAGE_TRACE", "CLICK > : currentPageIndex(before)=$currentPageIndex totalPages=$totalPages")
                if (currentPageIndex < totalPages - 1) currentPageIndex = minOf(totalPages - 1, currentPageIndex + step)
                Log.d("PAGE_TRACE", "CLICK > : currentPageIndex(after)=$currentPageIndex")
            },
            onGoToPage = { pageIndex ->
                currentPageIndex = pageIndex.coerceIn(0, totalPages - 1)
            },

            currentPageIndex = currentPageIndex,
            totalPages = totalPages,
            useHighRes = useHighRes,  // ← AJOUTE
            onUseHighResChange = { useHighRes = it },  // ← AJOUTE
            highResScaleFactor = highResScaleFactor,
            onHighResScaleChange = { newScale ->
                highResScaleFactor = newScale
                prefs.edit().putFloat("highResScaleFactor", newScale).apply()
            },
            isDoublePageMode = isDoublePageMode,
            onDoublePageModeChange = { isDoublePageMode = it }


        )
    }
    
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                color = Color.White
            )
        }
    }
  }
}

private fun renderPdfPageToFile(
    context: Context,
    uri: Uri,
    pageIndex: Int,
    useHighRes: Boolean,
    highResScaleFactor: Float,
    isDoublePageMode: Boolean
): Pair<File, Int> {
    val fileDescriptor = context.contentResolver.openFileDescriptor(uri, "r")!!
    val renderer = PdfRenderer(fileDescriptor)
    val pageCount = renderer.pageCount

    val scaleFactor = if (useHighRes) highResScaleFactor else 1.0f
    
    val finalBitmap: Bitmap
    
    if (isDoublePageMode && pageIndex + 1 < pageCount) {
        val page1 = renderer.openPage(pageIndex)
        val w1 = (page1.width * scaleFactor).toInt()
        val h1 = (page1.height * scaleFactor).toInt()
        val b1 = Bitmap.createBitmap(w1, h1, Bitmap.Config.ARGB_8888)
        val mat1 = Matrix().apply { postScale(scaleFactor, scaleFactor) }
        val canv1 = android.graphics.Canvas(b1)
        canv1.drawColor(android.graphics.Color.WHITE)
        page1.render(b1, null, mat1, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page1.close()

        val page2 = renderer.openPage(pageIndex + 1)
        val w2 = (page2.width * scaleFactor).toInt()
        val h2 = (page2.height * scaleFactor).toInt()
        val b2 = Bitmap.createBitmap(w2, h2, Bitmap.Config.ARGB_8888)
        val mat2 = Matrix().apply { postScale(scaleFactor, scaleFactor) }
        val canv2 = android.graphics.Canvas(b2)
        canv2.drawColor(android.graphics.Color.WHITE)
        page2.render(b2, null, mat2, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page2.close()

        val compWidth = w1 + w2
        val compHeight = maxOf(h1, h2)
        val compoBitmap = Bitmap.createBitmap(compWidth, compHeight, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(compoBitmap)
        c.drawColor(android.graphics.Color.WHITE)
        c.drawBitmap(b1, 0f, 0f, null)
        c.drawBitmap(b2, w1.toFloat(), 0f, null)
        
        b1.recycle()
        b2.recycle()
        
        // Rotation physique de 90° comme demandé par l'utilisateur
        val m = Matrix().apply { postRotate(90f) }
        finalBitmap = Bitmap.createBitmap(compoBitmap, 0, 0, compoBitmap.width, compoBitmap.height, m, true)
        if (finalBitmap != compoBitmap) compoBitmap.recycle()
        
    } else {
        val page = renderer.openPage(pageIndex)
        finalBitmap = Bitmap.createBitmap(
            (page.width * scaleFactor).toInt(),
            (page.height * scaleFactor).toInt(),
            Bitmap.Config.ARGB_8888
        )
        val canvas = android.graphics.Canvas(finalBitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val matrix = Matrix().apply { postScale(scaleFactor, scaleFactor) }
        page.render(finalBitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        page.close()
    }

    renderer.close()
    fileDescriptor.close()

    val suffix = if (isDoublePageMode) "double" else "single"
    val file = File(context.cacheDir, "pdf_page_${pageIndex}_$suffix.png")
    FileOutputStream(file).use {
        finalBitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
    }

    finalBitmap.recycle()

    return Pair(file, pageCount)
}


