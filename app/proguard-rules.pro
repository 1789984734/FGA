-keepattributes LineNumberTable,SourceFile
-keep class org.opencv.core.CvException { *; }

-keep class io.github.fate_grand_automata.scripts.enums.** { *; }
-keep class io.github.fate_grand_automata.scripts.models.** { *; }
-keep class io.github.fate_grand_automata.scripts.entrypoints.** { *; }
-keep class io.github.fate_grand_automata.imaging.** { *; }

# ML Kit Text Recognition
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_text_common.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**
