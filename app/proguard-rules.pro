# R8 rules for release builds.

# The Month widget's arrows run ChangeMonth, which Glance makes from its class name when the
# arrow is tapped, so R8 mustn't rename or remove it.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Glance runs widget updates through WorkManager, which loads its database and its workers by
# class name at startup. The release build crashed on launch without these.
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class * extends androidx.work.InputMerger { <init>(); }
