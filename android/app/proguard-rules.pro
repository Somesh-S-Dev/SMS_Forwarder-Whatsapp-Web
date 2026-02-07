# Add project specific ProGuard rules here.
# Security: Keep crypto classes and WhatsApp-related classes
-keep class com.secure.otpforwarder.CryptoManager { *; }
-keep class com.secure.otpforwarder.ConfigManager { *; }

# Keep Gson classes
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }

# Keep Security Crypto
-keep class androidx.security.crypto.** { *; }
