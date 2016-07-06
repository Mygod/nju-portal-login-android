import android.Keys._

android.Plugin.androidBuild

platformTarget in Android := "android-24"

name := "nju-portal-login-android"

scalaVersion := "2.11.8"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-target:jvm-1.6", "-Xexperimental")

shrinkResources in Android := true

resConfigs in Android := Seq("zh")

useSupportVectors

resolvers += Resolver.sonatypeRepo("public")

libraryDependencies ++= Seq(
  "com.j256.ormlite" % "ormlite-android" % "4.48",
  "me.leolin" % "ShortcutBadger" % "1.1.5",
  "org.json4s" %% "json4s-native" % "3.3.0",
  "tk.mygod" %% "mygod-lib-android" % "2.0.0-SNAPSHOT"
)

proguardOptions += "-dontwarn com.thoughtworks.**"

proguardVersion in Android := "5.2.1"
