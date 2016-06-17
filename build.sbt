import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-23"

name := "nju-portal-login-android"

scalaVersion := "2.11.8"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-target:jvm-1.6", "-Xexperimental")

shrinkResources in Android := true

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= Seq(
  "com.j256.ormlite" % "ormlite-core" % "4.48",
  "com.j256.ormlite" % "ormlite-android" % "4.48",
  "me.leolin" % "ShortcutBadger" % "1.1.5",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "tk.mygod" %% "mygod-lib-android" % "1.4.2"
)

proguardOptions += "-dontwarn com.thoughtworks.**"

proguardVersion in Android := "5.2.1"
