scalaVersion := "2.11.8"

enablePlugins(AndroidApp)
useSupportVectors

name := "nju-portal-login-android"
version := "3.3.1"
versionCode := Some(409)

platformTarget := "android-25"

compileOrder := CompileOrder.JavaThenScala
javacOptions ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil
scalacOptions ++= "-target:jvm-1.7" :: "-Xexperimental" :: Nil

proguardVersion := "5.3"
proguardCache := Seq()
proguardOptions += "-dontwarn com.thoughtworks.**"

shrinkResources := true
typedViewHolders := false
resConfigs := Seq("zh")

resolvers ++= Seq(Resolver.jcenterRepo, Resolver.sonatypeRepo("public"))
libraryDependencies ++=
  "com.j256.ormlite" % "ormlite-android" % "4.48" ::
  "eu.chainfire" % "libsuperuser" % "1.0.0.201607041850" ::
  "me.leolin" % "ShortcutBadger" % "1.1.6" ::
  "org.json4s" %% "json4s-native" % "3.4.0" ::
  "be.mygod" %% "mygod-lib-android" % "4.0.1-SNAPSHOT" ::
  Nil
