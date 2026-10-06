# Nova Store release proguard rules (R8).

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.novastore.app.**$$serializer { *; }
-keepclassmembers class com.novastore.app.** { *** Companion; }
-keepclasseswithmembers class com.novastore.app.** { kotlinx.serialization.KSerializer serializer(...); }

# JNI native parser (core/network). R8 keeps native method names, but the
# class + private external method must survive so the C++ side can resolve it.
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.novastore.app.core.network.fdroid.NativeFdroidIndex { *; }

# Google Play API (Reflection-free gson/proto reader built on field names and
# TypeToken that must not be renamed).
-keep class com.novastore.playapi.** { *; }

# WorkManager: workers are instantiated by class name by the library.
-keep public class * extends androidx.work.Worker { <init>(android.content.Context, androidx.work.WorkerParameters); }
-keep public class * extends androidx.work.CoroutineWorker { <init>(android.content.Context, androidx.work.WorkerParameters); }

# Room / Hilt ship their own consumer rules; silence the noisy platform warnings.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn androidx.room.paging.**