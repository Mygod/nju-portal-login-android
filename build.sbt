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

proguardOptions in Android ++= Seq("-keep class android.support.v7.preference.PreferenceScreen { <init>(...); }",
  "-keep class android.support.v7.preference.PreferenceCategory { <init>(...); }",
  "-keep class android.support.v14.preference.SwitchPreference { <init>(...); }",
  "-keep class tk.mygod.preference.EditTextPreference { <init>(...); }",
  "-dontwarn com.thoughtworks.paranamer.**")
