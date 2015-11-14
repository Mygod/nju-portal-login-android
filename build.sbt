import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-23"

name := "nju-portal-login-android"

scalaVersion := "2.11.7"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-target:jvm-1.6", "-Xexperimental")

shrinkResources in Android := true

// Duplicate mygod-lib-android's dependencies due to bug in android-sdk-plugin.
libraryDependencies ++= Seq(
  "com.android.support" % "design" % "23.1.0",
  "com.android.support" % "preference-v14" % "23.1.0",
  "com.android.support" % "support-v13" % "23.1.0",
  "eu.chainfire" % "libsuperuser" % "1.0.0.201510071325",
  "org.json4s" %% "json4s-native" % "3.3.0"
  // TODO: libraryDependencies += aar("tk.mygod" % "mygod-lib-android" % "1.3.0")
)

localAars in Android += baseDirectory.value / "mygod-lib-android.aar"

proguardConfig in Android := List("-dontobfuscate",
  "-dontoptimize",
  "-renamesourcefileattribute SourceFile",
  "-keepattributes SourceFile,LineNumberTable",
  "-verbose",
  "-flattenpackagehierarchy",
  "-dontusemixedcaseclassnames",
  "-dontskipnonpubliclibraryclasses",
  "-dontpreverify",
  "-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable",
  "-keepattributes *Annotation*",
  "-dontnote android.annotation.**",
  "-dontwarn android.support.**",
  "-dontnote android.support.**",
  "-dontnote scala.ScalaObject",
  "-dontnote org.xml.sax.EntityResolver",
  "-dontnote scala.concurrent.forkjoin.**",
  "-dontwarn scala.beans.ScalaBeanInfo",
  "-dontwarn scala.concurrent.**",
  "-dontnote scala.reflect.**",
  "-dontwarn scala.reflect.**",
  "-dontwarn scala.sys.process.package$",
  "-dontwarn **$$anonfun$*",
  "-dontwarn scala.collection.immutable.RedBlack$Empty",
  "-dontwarn scala.tools.**,plugintemplate.**",

  "-keep class android.support.v4.widget.Space { <init>(...); }",
  "-keep class android.support.v7.internal.widget.ButtonBarLayout { <init>(...); }",
  "-keep class android.support.v7.internal.widget.FitWindowsLinearLayout { <init>(...); }",
  "-keep class android.support.v7.internal.widget.ViewStubCompat { <init>(...); }",
  "-keep class android.support.v7.widget.Toolbar { <init>(...); }",

  // AlertDialog
  "-keep class android.support.v7.internal.widget.DialogTitle { <init>(...); }",
  "-keep class android.support.v7.internal.widget.FitWindowsFrameLayout { <init>(...); }",
  "-keep class android.support.v4.widget.NestedScrollView { <init>(...); }",

  // Preferences
  "-keep class android.support.v7.internal.widget.PreferenceImageView { <init>(...); }",
  "-keep class android.support.v7.widget.RecyclerView { <init>(...); }",
  // EditTextPreference
  "-keep class scala.collection.SeqLike { public java.lang.String toString(); }",

  "-keep class tk.mygod.nju.portal.login.App { <init>(...); }",
  "-keep class tk.mygod.nju.portal.login.MainActivity { <init>(...); }",
  "-keep class tk.mygod.nju.portal.login.NetworkConditionsReceiver { <init>(...); }",
  "-keep class tk.mygod.nju.portal.login.PortalManager { <init>(...); }",
  "-keep class tk.mygod.nju.portal.login.SettingsFragment { <init>(...); }",

  "-keep class android.support.v7.preference.PreferenceScreen { <init>(...); }",
  "-keep class android.support.v7.preference.PreferenceCategory { <init>(...); }",
  "-keep class android.support.v14.preference.SwitchPreference { <init>(...); }",
  "-keep class tk.mygod.preference.EditTextPreference { <init>(...); }",
  "-keep class tk.mygod.preference.NumberPickerPreference { <init>(...); }",
  "-dontwarn com.thoughtworks.paranamer.**")
