# Cloud Launcher ProGuard rules
-keep class com.cloud.launcher.service.** { *; }
-keep class com.cloud.launcher.receiver.** { *; }
-keep class com.cloud.launcher.MainActivity { *; }
-keep class com.cloud.launcher.ui.LauncherViewModel { *; }
-keep class com.cloud.launcher.root.RunningApp { *; }
-dontwarn kotlin.**
