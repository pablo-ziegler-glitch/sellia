# ProGuard Rules for Firebase, Hilt, Room, Retrofit, Gson, and Coroutines

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Hilt
-keep class dagger.** { *; }
-dontwarn dagger.**
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keep @dagger.hilt.android.lifecycle.HiltAndroidApp class * { *; }

# Room
-keepclassmembers class * {
    @androidx.room.* <fields>;
}
-keep class * implements androidx.room.OnConflictStrategy {
  public static final int *; }

# Retrofit
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes *Annotation*

# Gson
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**

# Coroutines
-keep class kotlin.coroutines.** { *; }
-dontwarn kotlin.coroutines.**
