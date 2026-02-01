# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ============================================
# TES RÈGLES PROGUARD PERSONNALISÉES
# ============================================

# 1. GARDE TOUTES TES CLASSES
-keep class com.example.scan_ocr_tts.** { *; }

# 2. OPENCV (CRITIQUE)
-keep class org.opencv.** { *; }
-dontwarn org.opencv.**

# 3. ML KIT OCR
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# 4. CAMERAX
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# 5. JETPACK COMPOSE
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# 6. TTS (TextToSpeech)
-keep class android.speech.tts.** { *; }

# 7. PDF RENDERER
-keep class android.graphics.pdf.** { *; }

# 8. ATTRIBUTS IMPORTANTS
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepclassmembers class **.R$* {
    public static <fields>;
}