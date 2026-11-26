# Preserve source file and line numbers for readable Play Console crash reports
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,InnerClasses,EnclosingMethod
-renamesourcefileattribute SourceFile

# Kotlinx Serialization
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
    kotlinx.serialization.KSerializer serializer();
}
-keep,includedescriptorclasses class com.wayfare.ride.domain.** { *; }
-keep,includedescriptorclasses class com.wayfare.ride.data.** { *; }

# Keep enum values intact for serialization and Room TypeConverters
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Room & SQLite generated implementations
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.**
-dontwarn org.robolectric.**
