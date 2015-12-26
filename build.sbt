import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-23"

name := "nju-portal-login-android"

scalaVersion := "2.11.7"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-target:jvm-1.6", "-Xexperimental")

shrinkResources in Android := true

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= Seq(
  "com.j256.ormlite" % "ormlite-core" % "4.48",
  "com.j256.ormlite" % "ormlite-android" % "4.48",
  "me.leolin" % "ShortcutBadger" % "1.1.3",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "tk.mygod" %% "mygod-lib-android" % "1.3.9-SNAPSHOT"
)

proguardOptions ++= Seq("-dontwarn com.thoughtworks.**",
  "-keep class me.leolin.shortcutbadger.impl.AdwHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.ApexHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.AsusHomeLauncher { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.DefaultBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.NewHtcHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.NovaHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.SolidHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.SonyHomeBadger { <init>(...); }",
  "-keep class me.leolin.shortcutbadger.impl.XiaomiHomeBadger { <init>(...); }")
