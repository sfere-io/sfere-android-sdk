# Sfere Analytics: keep the public API and the base library's serialized event classes.
-keep class io.sfere.analytics.** { public *; }
-keepclassmembers class com.segment.analytics.kotlin.core.** { *** Companion; }
-keepclasseswithmembers class com.segment.analytics.kotlin.core.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn com.segment.analytics.kotlin.**
