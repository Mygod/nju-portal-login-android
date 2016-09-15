android.Plugin.androidBuild

platformTarget := "android-24"

name := "nju-portal-login-android"

scalaVersion := "2.11.8"

javacOptions ++= Seq("-source", "1.6", "-target", "1.6")

scalacOptions ++= Seq("-target:jvm-1.6", "-Xexperimental")

shrinkResources := true

typedViewHolders := false

resConfigs := Seq("zh")

useSupportVectors

resolvers ++= Seq(Resolver.jcenterRepo, Resolver.sonatypeRepo("public"))

libraryDependencies ++= Seq(
  "com.j256.ormlite" % "ormlite-android" % "4.48",
  "eu.chainfire" % "libsuperuser" % "1.0.0.201607041850",
  "me.leolin" % "ShortcutBadger" % "1.1.6",
  "org.json4s" %% "json4s-native" % "3.4.0",
  "tk.mygod" %% "mygod-lib-android" % "3.0.1"
)

proguardCache := Seq()

proguardOptions += "-dontwarn com.thoughtworks.**"

proguardVersion := "5.2.1"
