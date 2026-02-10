package com.example.scan_ocr_tts

import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.vision.text.Text as VisionText

// Données de retour simplifiées pour l'OCR
data class OcrResult(
    val text: String,
    val blocks: List<TextBlock>,
    val success: Boolean,
    val error: String? = null
)

data class TextBlock(
    val text: String,
    val boundingBox: Rect?,  // Garder Rect d'Android (pas de Compose)
    val confidence: Float? = null
)


// ... (gardez les data classes précédentes)

object OcrProcessor {

    private var recognizer: TextRecognizer? = null

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        Log.d("OCR_TOOLS", "ML Kit Text Recognizer initialisé")
    }


    fun processImageWithMlKit(
        bitmap: Bitmap,
        rotation: Int = 0,
        onResult: (OcrResult) -> Unit
    ) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotation)

            recognizer?.process(inputImage)
                ?.addOnSuccessListener { visionText ->
                    // Convertir les résultats ML Kit en notre format
                    val blocks = visionText.textBlocks.map { mlBlock ->
                        TextBlock(
                            text = mlBlock.text,
                            boundingBox = mlBlock.boundingBox,
                            confidence = null // ML Kit ne donne pas de confidence facilement
                        )
                    }

                    val result = OcrResult(
                        text = visionText.text,
                        blocks = blocks,
                        success = true
                    )

                    Log.d("OCR_TOOLS", "OCR réussi: ${visionText.text.length} caractères, ${blocks.size} blocs")
                    onResult(result)
                }
                ?.addOnFailureListener { e ->
                    Log.e("OCR_TOOLS", "Erreur OCR ML Kit", e)
                    onResult(OcrResult("", emptyList(), false, e.message))
                }

        } catch (e: Exception) {
            Log.e("OCR_TOOLS", "Exception dans processImageWithMlKit", e)
            onResult(OcrResult("", emptyList(), false, e.message))
        }
    }


    // Toujours dans l'object OcrProcessor, ajoutez cette fonction :
    fun extractTextFromRectangles(
        bitmap: Bitmap,
        rectangles: List<Rect>,
        rotation: Int = 0,
        onResult: (List<String>) -> Unit
    ) {
        try {
            val inputImage = InputImage.fromBitmap(bitmap, rotation)
            val extractedTexts = mutableListOf<String>()
            var pendingOperations = rectangles.size

            if (rectangles.isEmpty()) {
                onResult(emptyList())
                return
            }

            rectangles.forEach { rect ->
                // Créer un sous-bitmap pour chaque rectangle
                val subBitmap = Bitmap.createBitmap(
                    bitmap,
                    rect.left,
                    rect.top,
                    rect.width(),
                    rect.height()
                )

                val subImage = InputImage.fromBitmap(subBitmap, rotation)

                recognizer?.process(subImage)
                    ?.addOnSuccessListener { visionText ->
                        extractedTexts.add(visionText.text)
                        pendingOperations--

                        if (pendingOperations == 0) {
                            Log.d("OCR_TOOLS", "Extraction terminée: ${extractedTexts.size} rectangles")
                            onResult(extractedTexts)
                        }
                    }
                    ?.addOnFailureListener { e ->
                        Log.e("OCR_TOOLS", "Erreur extraction rectangle $rect", e)
                        extractedTexts.add("")
                        pendingOperations--

                        if (pendingOperations == 0) {
                            onResult(extractedTexts)
                        }
                    }
            }

        } catch (e: Exception) {
            Log.e("OCR_TOOLS", "Exception dans extractTextFromRectangles", e)
            onResult(emptyList())
        }
    }


    // Fonction pour extraire le texte d'un rectangle spécifique (pour TTS)
    fun extractTextFromRectangle(
        bitmap: Bitmap,
        rect: android.graphics.Rect,
        rotation: Int = 0,
        onResult: (String) -> Unit
    ) {
        try {
            // Créer un sous-bitmap du rectangle
            val safeLeft = rect.left.coerceAtLeast(0)
            val safeTop = rect.top.coerceAtLeast(0)
            val safeWidth = rect.width().coerceAtMost(bitmap.width - safeLeft)
            val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)

            if (safeWidth <= 0 || safeHeight <= 0) {
                onResult("")
                return
            }

            val croppedBitmap = Bitmap.createBitmap(
                bitmap,
                safeLeft,
                safeTop,
                safeWidth,
                safeHeight
            )

            // Utiliser ML Kit pour l'extraction
            val inputImage = InputImage.fromBitmap(croppedBitmap, rotation)

            recognizer?.process(inputImage)
                ?.addOnSuccessListener { visionText ->
                    onResult(visionText.text)
                }
                ?.addOnFailureListener { e ->
                    Log.e("OCR_TOOLS", "Erreur extraction rectangle", e)
                    onResult("")
                }

        } catch (e: Exception) {
            Log.e("OCR_TOOLS", "Exception dans extractTextFromRectangle", e)
            onResult("")
        }
    }

}