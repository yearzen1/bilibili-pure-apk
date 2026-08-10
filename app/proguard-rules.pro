-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

-keep class com.bilibili.pure.data.model.** { *; }

-keep class io.noties.markwon.** { *; }
-keep class dev.jeziellago.compose.markdowntext.** { *; }
