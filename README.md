# NJU Portal Login for Android
This app is designed for Nanjing University users to login into [p.nju.edu.cn](http://p.nju.edu.cn) automatically and
 conveniently with your Android device. However it can be easily modified to adapt to other network portals.

## Dependencies
* Android Support Repository
* SBT

## Building
First, create a `local.properties` following [this guide](https://github.com/pfn/android-sdk-plugin#usage). Then:

    sbt clean android:packageRelease
