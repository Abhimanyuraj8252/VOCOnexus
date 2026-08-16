# VocoNexus Production ProGuard & R8 Optimization Rules

# Room Database Keep Rules
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Dao interface *
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# Kotlinx Serialization Rules
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class *$$serializer {
    *** INSTANCE;
}

# Media3 ExoPlayer Rules
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.common.** { *; }
-keep class androidx.media3.session.** { *; }

# Sherpa-ONNX & ONNX Runtime Native JNI Bindings
-keep class ai.onnxruntime.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# VocoNexus Domain & Entity Models
-keep class com.voconexus.app.core.data.db.** { *; }
-keep class com.voconexus.app.core.domain.model.** { *; }
-keep class com.voconexus.app.core.speech.model.** { *; }
-keep class com.voconexus.app.core.tts.model.** { *; }
