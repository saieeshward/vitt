# R8 keep rules for the release build.
#
# Every rule here is for something that is reached by *name* rather than by a
# call R8 can see: a reflective lookup, a generated companion, or a resource on
# the classpath. Anything reached by an ordinary call needs no rule, and adding
# one anyway keeps dead code in the APK — so this file is deliberately short
# and each rule says what breaks without it.
#
# Verified by installing the shrunk, resource-shrunk release build on an
# emulator and using it, not by reading the mapping file. Rules that have never
# run are not rules, they are hopes.

# ---- kotlinx.serialization ---------------------------------------------------
#
# The plugin generates a `Companion.serializer()` for every @Serializable class
# and `SheetsWire.kt` has fifteen of them. The lookup is by name, so R8 sees
# nothing calling these and strips them; the failure is a
# SerializationException at the first Sheets request, which is after sign-in and
# therefore not on any path a smoke test walks by accident.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# The generated serializers themselves, and the descriptors they hand back.
-keepclasseswithmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}

# ---- Ktor -------------------------------------------------------------------
#
# Ktor picks its engine off the classpath through a ServiceLoader, so nothing
# references OkHttpEngineContainer by name in our code. Without this the client
# throws "no engine found" the moment auth starts.
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }

# Ktor and coroutines both ship rules of their own in their AARs; these two are
# the gaps those do not cover for a client assembled the way `HttpClientFactory`
# assembles one.
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# ---- SQLDelight -------------------------------------------------------------
#
# The generated database implementation is referenced directly, so it needs no
# rule. The Android driver reaches the framework SQLite classes through the
# support library's interfaces, which are already kept by their own consumer
# rules. What is not covered is the callback: `AndroidSqliteDriver` instantiates
# the schema callback reflectively when a version differs.
-keep class app.cash.sqldelight.driver.android.** { *; }
-keep class ie.shoonya.vitt.db.** { *; }

# ---- The app ----------------------------------------------------------------
#
# `expect`/`actual` platform bindings are resolved at compile time and need no
# rule. What does need one: nothing in the app is reached reflectively, so there
# is deliberately no blanket `-keep class ie.shoonya.vitt.**`. A rule that keeps
# the whole app defeats the point of running R8 at all, and hides the very
# breakage this file exists to prevent.

# Kotlin's own metadata, which coroutines' debug probes and serialization both
# read at runtime.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod

# Line numbers in a crash report from the Play console, with the source file
# name renamed so it does not leak the tree. Without this a stack trace from a
# real user is unreadable and the mapping file cannot help.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
