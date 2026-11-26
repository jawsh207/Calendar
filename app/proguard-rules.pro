# R8 rules for release builds.

# The Month widget's arrows run ChangeMonth, which Glance makes from its class name when the
# arrow is tapped, so R8 mustn't rename or remove it.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
