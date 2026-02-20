package com.example.scan_ocr_tts

import android.graphics.Bitmap
import android.graphics.Bitmap.createBitmap
import android.util.Log
// import androidx.compose.remote.creation.compose.state.max
import com.google.mlkit.vision.common.InputImage
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import org.opencv.core.CvType
import org.opencv.core.Size
import org.opencv.core.*

import kotlin.math.max
import kotlin.math.min

import org.opencv.core.Point


import com.google.mlkit.vision.text.TextRecognition

import com.google.mlkit.vision.text.latin.TextRecognizerOptions



object ImageProcessing {

    fun toGrayscale(inputBitmap: Bitmap): Bitmap {
        val src = Mat()
        val gray = Mat()

        // Bitmap → Mat
        Utils.bitmapToMat(inputBitmap, src)

        // Conversion en niveaux de gris
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)





        // Reconvertir en Bitmap
        val resultBitmap = Bitmap.createBitmap(
            gray.cols(),
            gray.rows(),
            Bitmap.Config.ARGB_8888
        )

        Utils.matToBitmap(gray, resultBitmap)

        src.release()
        gray.release()

        return resultBitmap
    }


    fun toAdaptiveThreshold(
        bitmap: Bitmap,
        whiteThreshold: Float,  // <-- Float au lieu de Int, nom changé
        contrastBoost: Float,
        preGrayAdjust: Float = 0.0f,  // <-- NOUVEAU PARAMÈTRE ICI
        minWidthRatio: Float = 0.15f,
        skipDetection: Boolean = false,
        boostMode: Boolean = false
    ): Pair<Bitmap, List<Rect>> {

        if (skipDetection) {
            Log.d("NO_SQUARES", "Détection de texte ignorée (mode no_squares)")
            val resultBitmap = Bitmap.createBitmap(
                bitmap.width,
                bitmap.height,
                Bitmap.Config.ARGB_8888
            )
            // Copier l'image originale
            val canvas = android.graphics.Canvas(resultBitmap)
            canvas.drawBitmap(bitmap, 0f, 0f, null)
            return Pair(resultBitmap, emptyList())  // ← Liste vide = pas de cadres rouges
        }

        val src = Mat()
        val gray = Mat()

        val thresh = Mat()

        Utils.bitmapToMat(bitmap, src)

// Application du renforcement de contraste en amont (mode boost)
        if (boostMode) {
            val boostFactor = 1.50f  // Correspond au facteur utilisé dans OcrScreen
            val srcMat = Mat()
            src.copyTo(srcMat)  // On copie pour ne pas modifier l'original

            // Application du renforcement de contraste
            srcMat.convertTo(srcMat, -1, boostFactor.toDouble(), 0.0)

            // Remplacer src par srcMat pour la suite du traitement
            srcMat.copyTo(src)
            srcMat.release()

            Log.d("BOOST_MODE", "Renforcement de contraste appliqué en amont")
        }




        // 👇 AJOUTER ICI - Début de l'ajustement pré-gris
        if (preGrayAdjust != 0.0f) {
            val contrast = 1.0f + kotlin.math.abs(preGrayAdjust)
            val brightness = if (preGrayAdjust < 0) 30.0 * preGrayAdjust else -20.0 * preGrayAdjust
            Log.d("PRE_GRAY_DEBUG", "Contraste: $contrast, Luminosité: $brightness")
            src.convertTo(src, -1, contrast.toDouble(), brightness)
            Log.d("PRE_GRAY_DEBUG", "Pixel après ajustement: ${src.get(0, 0)?.contentToString()}")
        }
        // 👆 FIN de l'ajustement pré-gris



        // Gris
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        Imgproc.equalizeHist(gray, gray)
// Amplifie ou adoucit le contraste selon le slider utilisateur
        Core.normalize(gray, gray, 0.0, 255.0, Core.NORM_MINMAX)




// Taille du noyau adaptée au contraste choisi par l'utilisateur
        // val kernelSize = (13 * contrastBoost).coerceIn(9f, 17f)
        val kernelSize = (9 * contrastBoost).coerceIn(5f, 13f)
        val blackhatKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(kernelSize.toDouble(), kernelSize.toDouble())
        )


        val blackhat = Mat()
        Imgproc.morphologyEx(gray, blackhat, Imgproc.MORPH_BLACKHAT, blackhatKernel)

        // 🔵 Binarisation automatique du texte détecté

        val otsuValue = Imgproc.threshold(
            blackhat,
            thresh,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU
        )

// Rend le texte plus épais ou plus fin selon le slider
        val adjusted = (otsuValue * contrastBoost).coerceIn(0.0, 255.0)

        Imgproc.threshold(
            blackhat,
            thresh,
            adjusted,
            255.0,
            Imgproc.THRESH_BINARY
        )

        // 🔵 Regroupe les lettres pour former des lignes / paragraphes
// Fusion forte horizontale, faible verticale
// Plus le slider est haut → moins on relie horizontalement (évite de coller les colonnes)
        val horizontalSize = (25.0 / contrastBoost).coerceIn(12.0, 28.0)


        val lineKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(horizontalSize, 5.0)
        )
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, lineKernel)
        lineKernel.release()

// 2️⃣ Relie légèrement les lignes proches verticalement (sans coller les colonnes)
        val blockKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(12.0, 18.0)
        )
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, blockKernel)

        // 👇 COUPER LES PONTS HORIZONTAUX
        val cutHorizontalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(25.0, 1.0)  // Long horizontal, fin vertical
        )
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_OPEN, cutHorizontalKernel)
        cutHorizontalKernel.release()

// 👇 COUPER LES PONTS VERTICAUX
        val cutVerticalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(1.0, 25.0)  // Long vertical, fin horizontal
        )

        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_OPEN, cutVerticalKernel)

        blockKernel.release()




        val contours = mutableListOf<MatOfPoint>()

// Détection des contours dans l'image seuillée
        val hierarchy = Mat()

//        Imgproc.findContours(
//            thresh,
//            contours,
//            hierarchy,
//            Imgproc.RETR_EXTERNAL,
//            Imgproc.CHAIN_APPROX_SIMPLE
//        )


        Imgproc.findContours(
            thresh,
            contours,
            hierarchy,
            Imgproc.RETR_LIST, // Changez RETR_EXTERNAL par RETR_LIST
            Imgproc.CHAIN_APPROX_SIMPLE
        )

        val detectedRects = mutableListOf<Rect>()
        val filteredRects = mutableListOf<Rect>()

        for (contour in contours) {
            val rect = Imgproc.boundingRect(contour)
            // if (isInsideImage(rect, imageRects)) continue


            // ❌ Ignore les cadres trop étroits (bruit)
            if (rect.width < src.width() * minWidthRatio) continue

            val area = rect.width * rect.height
            val aspect = rect.width.toFloat() / rect.height

            if (rect.height < thresh.height() * 0.02 && aspect > 3f) continue

            val imageArea = thresh.width() * thresh.height().toDouble()
            // ❌ Trop petit = bruit
            if (area < 1200) continue
            // ❌ Trop haut = plutôt une image qu'une ligne de texte
            if (rect.height > thresh.height() * 0.9) continue
            // ✅ Zone valide → on la garde
            detectedRects.add(rect)
        }
// 🔴 Supprimer les rectangles inclus dans d'autres (petits cadres dans grands)
// 🔴 Nouvelle logique : Supprimer les cadres qui contiennent d'autres blocs


        for (r in detectedRects) {
            var isContainer = false

            for (other in detectedRects) {
                if (r == other) continue

                // Si 'r' contient 'other' (et que 'other' est assez grand)
                if (other.x > r.x &&
                    other.y > r.y &&
                    (other.x + other.width) < (r.x + r.width) &&
                    (other.y + other.height) < (r.y + r.height)) {

                    // Si le bloc interne représente une part importante de la zone,
                    // alors 'r' est probablement un cadre décoratif.
                    isContainer = true
                    break
                }
            }

            if (!isContainer) {
                filteredRects.add(r)
            }
        }


// ✅ Convertir l'image (thresh) en Bitmap
        val resultBitmap = Bitmap.createBitmap(
            thresh.cols(),
            thresh.rows(),
            Bitmap.Config.ARGB_8888
        )
        //Modifie Original
        // Utils.matToBitmap(src, resultBitmap)
        Utils.matToBitmap(thresh, resultBitmap)





// ✅ Retourner Bitmap + rectangles détectés
        // return Pair(resultBitmap, detectedRects)
        // return Pair(resultBitmap, filteredRects)


// Appliquez un seuillage pour convertir l'image en noir et blanc strict
        Imgproc.threshold(thresh, thresh, 128.0, 255.0, Imgproc.THRESH_BINARY)




// Maintenant, convertissez thresh en Bitmap (qui est une image binaire)
        val finalResultBitmap = createBitmap(
            thresh.cols(),
            thresh.rows(),
            Bitmap.Config.ARGB_8888
        )
        Utils.matToBitmap(thresh, finalResultBitmap)

// Filtrage des blocs en fonction du pourcentage de blanc
        val whiteFilteredRects = filterBlocksByWhitePercentage(
            finalResultBitmap,
            filteredRects,
            whiteThreshold  // 👈 Utiliser le slider
        )

        src.release()
        gray.release()
        hierarchy.release()
        thresh.release()
        blackhat.release()
        blackhatKernel.release()


        return Pair(resultBitmap, whiteFilteredRects)

    }


    fun strengthenText(src: Bitmap): Bitmap {
        val mat = Mat()
        Utils.bitmapToMat(src, mat)

        // Petit noyau pour épaissir légèrement le texte
        val kernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(2.0, 2.0)
        )

        Imgproc.dilate(mat, mat, kernel)

        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, out)
        mat.release()
        return out
    }


    fun adjustContrast(src: Bitmap, contrast: Float): Bitmap {
        val bmp = src.copy(Bitmap.Config.ARGB_8888, true)
        val width = bmp.width
        val height = bmp.height

        val pixels = IntArray(width * height)
        bmp.getPixels(pixels, 0, width, 0, 0, width, height)

        for (i in pixels.indices) {
            val color = pixels[i]

            val r = ((android.graphics.Color.red(color) - 128) * contrast + 128).toInt()
                .coerceIn(0, 255)
            val g = ((android.graphics.Color.green(color) - 128) * contrast + 128).toInt()
                .coerceIn(0, 255)
            val b = ((android.graphics.Color.blue(color) - 128) * contrast + 128).toInt()
                .coerceIn(0, 255)

            pixels[i] = android.graphics.Color.rgb(r, g, b)
        }

        bmp.setPixels(pixels, 0, width, 0, 0, width, height)
        return bmp
    }

    // Ajoute à la fin du fichier ImageProcessing.kt, avant la dernière accolade fermante '}'

    fun filterBlocksByWhitePercentage(
        bitmap: Bitmap,
        rects: List<Rect>,
        minWhitePercentage: Float = 40.0f
    ): List<Rect> {
        return rects.filter { rect ->
            calculateWhitePercentage(bitmap, rect) >= minWhitePercentage
        }
    }

    fun calculateWhitePercentage(bitmap: Bitmap, rect: Rect): Float {
        var whitePixels = 0
        var totalPixels = 0

        // S'assurer que le rectangle est dans les limites de l'image
        val safeX = rect.x.coerceAtLeast(0)
        val safeY = rect.y.coerceAtLeast(0)
        // Calculer la largeur/hauteur disponible sans dépasser les bords du bitmap
        val safeWidth = min(rect.width, bitmap.width - safeX)
        val safeHeight = min(rect.height, bitmap.height - safeY)

        val safeRect = Rect(
            safeX, safeY, safeWidth, safeHeight
        )

        val width = safeRect.width
        val height = safeRect.height

        if (width <= 0 || height <= 0) return 0f

        // Échantillonner pour performance
        val step = max(1, min(width, height) / 15)

        // Corrige aussi la boucle pour éviter les dépassements
        for (x in 0 until width step step) {
            for (y in 0 until height step step) {
                val pixelX = safeRect.x + x
                val pixelY = safeRect.y + y

                // Vérification supplémentaire de sécurité
                if (pixelX >= 0 && pixelX < bitmap.width &&
                    pixelY >= 0 && pixelY < bitmap.height) {

                    val pixel = bitmap.getPixel(pixelX, pixelY)

                    // Convertir en niveaux de gris
                    val r = android.graphics.Color.red(pixel)
                    val g = android.graphics.Color.green(pixel)
                    val b = android.graphics.Color.blue(pixel)

                    // Formule de luminance standard
                    val luminance = 0.299 * r + 0.587 * g + 0.114 * b

                    // Considérer comme "blanc" si luminance > 200 (sur 255)
                    if (luminance > 200) {
                        whitePixels++
                    }

                    totalPixels++
                }
            }
        }

        return if (totalPixels > 0) {
            (whitePixels.toFloat() / totalPixels.toFloat()) * 100
        } else {
            0f
        }
    }
}