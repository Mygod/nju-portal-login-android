scalaVersion := "2.11.8"

enablePlugins(AndroidApp)
android.useSupportVectors

name := "nju-portal-login-android"
version := "3.4.0"
versionCode := Some(411)

platformTarget := "android-25"

compileOrder := CompileOrder.JavaThenScala
javacOptions ++= "-source" :: "1.7" :: "-target" :: "1.7" :: Nil
scalacOptions ++= "-target:jvm-1.7" :: "-Xexperimental" :: Nil

proguardVersion := "5.3.2"
proguardCache := Seq()
proguardOptions ++=
  "-dontwarn com.thoughtworks.**" ::
  "-dontwarn com.j256.ormlite.**" ::
  Nil

shrinkResources := true
typedViewHolders := false
resConfigs := Seq("zh-rCN")

resolvers ++= Seq(Resolver.jcenterRepo, Resolver.sonatypeRepo("public"))
libraryDependencies ++=
  "com.j256.ormlite" % "ormlite-android" % "5.0" ::
  "eu.chainfire" % "libsuperuser" % "1.0.0.201608240809" ::
  "me.leolin" % "ShortcutBadger" % "1.1.13" ::
  "be.mygod" %% "mygod-lib-android" % "4.0.4-SNAPSHOT" ::
  Nil
