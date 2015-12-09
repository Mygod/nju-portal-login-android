# Portal Login Helper for NJU
This app is designed for Nanjing University users to login into [p.nju.edu.cn](http://p.nju.edu.cn) automatically and
conveniently with your Android device. However it can be easily modified to adapt to other network portals.

This application uses NJU portal's API and is not endorsed or certified by Nanjing University.

No personal or private information about you or your device is collected or transmitted by this app.

## Dependencies
* Android Support Repository
* SBT

## Building
First, create a `local.properties` following [this guide](https://github.com/pfn/android-sdk-plugin#usage). Then:

    sbt clean android:packageRelease
