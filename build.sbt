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
  "tk.mygod" %% "mygod-lib-android" % "1.3.6-SNAPSHOT",
  "org.json4s" %% "json4s-native" % "3.3.0"
)

proguardOptions += "-dontwarn com.thoughtworks.**"
