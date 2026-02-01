package com.example.scan_ocr_tts

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.exifinterface.media.ExifInterface
import android.graphics.Matrix

@Composable
fun ImagePreviewScreen(
    imageFile: File,
    onConfirm: () -> Unit,
    onRetake: () -> Unit
) {
    val originalBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)

    val exif = ExifInterface(imageFile.absolutePath)
    val orientation = exif.getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL
    )

    val rotationDegrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }

    val bitmap = if (rotationDegrees != 0f) {
        val matrix = Matrix().apply { postRotate(rotationDegrees) }
        Bitmap.createBitmap(
            originalBitmap,
            0,
            0,
            originalBitmap.width,
            originalBitmap.height,
            matrix,
            true
        )
    } else {
        originalBitmap
    }

    val finalBitmap = if (bitmap.width > bitmap.height) {
        val matrix = Matrix().apply { postRotate(90f) }
        Bitmap.createBitmap(
            bitmap,
            0,
            0,
            bitmap.width,
            bitmap.height,
            matrix,
            true
        )
    } else {
        bitmap
    }

    Box(modifier = Modifier.fillMaxSize()) {

        Image(
            bitmap = finalBitmap.asImageBitmap(),

            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop

        )


        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Utiliser cette image")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = onRetake, modifier = Modifier.fillMaxWidth()) {
                Text("Reprendre la photo")
            }
        }
    }
}


