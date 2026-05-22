# Value Finder project-specific ProGuard rules.
#
# NOTE: abc and xyz build variants should NOT have R8/ProGuard enabled at all.
# These variants are for development/debugging and should skip code shrinking
# to avoid TypeToken serialization issues and maintain debugging capability.
# Only apply these rules to release builds where R8 is actually enabled.

# Keep the main app package with careful rule
-keep class com.example.valuefinder.ValuePicsApp { *; }
-keep class com.example.valuefinder.ValuePicsViewModel { *; }
-keep class com.example.valuefinder.ValuePicsRepository { *; }

# Keep UI composables
-keep @androidx.compose.runtime.Composable class com.example.valuefinder.ui.** { *; }

# Keep Room database entities and DAOs (these need reflection)
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep interface * extends androidx.room.Dao { *; }

# Keep data classes with GSON annotations
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep custom View constructors for XML inflation
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

# Preserve line numbers for stack traces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep nested classes
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Preserve generic signatures for Gson TypeToken
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Keep TypeToken usage and preserve generic signatures
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken

# Keep all TypeToken instantiations with generic signatures
-keepclassmembers class * {
    *** getType();
}
-keep class com.example.valuefinder.ValuePicsRepository$BackupPayload { *; }
-keep class com.example.valuefinder.ValuePicsRepository$BackupItemRecord { *; }
-keep class com.example.valuefinder.ValuePicsRepository$BackupItemPhotoRecord { *; }
-keep class com.example.valuefinder.ValuePicsRepository$BackupItemAudioNoteRecord { *; }
-keep class com.example.valuefinder.ValuePicsRepository$ImportedItemPhotoRecord { *; }


# Remove logging in release builds for better obfuscation
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}




