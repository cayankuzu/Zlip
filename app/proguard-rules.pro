# androidx.window loads the OEM WindowManager Extensions through reflection.
# The library ships its own consumer rules; these keep the vendor interfaces intact as a safety net.
-keep class androidx.window.extensions.** { *; }
-dontwarn androidx.window.extensions.**
-keep class androidx.window.sidecar.** { *; }
-dontwarn androidx.window.sidecar.**
