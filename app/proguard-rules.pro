# Earnd release rules. Manifest components, Room, WorkManager and Glance ship their own
# consumer rules; the entries below cover what reflection or persistence still relies on.

# Enum names are persisted in Room/settings and parsed with valueOf().
-keepclassmembers enum com.earnd.app.** { *; }

# Glance action callbacks are instantiated reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# WorkManager workers are created by class name.
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Room entities/DAOs (generated _Impl classes reference them by name).
-keep class com.earnd.app.data.** { *; }

-dontwarn org.jetbrains.annotations.**
