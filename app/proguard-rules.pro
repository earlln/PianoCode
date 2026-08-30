# ML Kit text recognition ships its own consumer rules; keep the model entry points
# reachable so R8 does not strip the recognizer that is only reached reflectively.
-keep class com.google.mlkit.vision.text.** { *; }
-dontwarn com.google.mlkit.**
