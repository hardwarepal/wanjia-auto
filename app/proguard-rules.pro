# 保留 ML Kit 相关类，避免压缩导致反射初始化失败
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
