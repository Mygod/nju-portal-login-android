# Portal Login Helper for NJU
This app is designed for Nanjing University users to login into [p.nju.edu.cn](http://p.nju.edu.cn) automatically and
conveniently with your Android device. However it can be easily modified to adapt to other network portals.

Please note that this app is NOT official, which means it is NOT authorized by nor produced by Nanjing University.
But your username/password will NEVER be shared with anyone or uploaded to anywhere except the portal servers to log in.
See [FAQ for more security-related stuff](https://github.com/Mygod/nju-portal-login-android/wiki/FAQ-&-Support#i-need-to-enter-my-username-and-password-is-that-secure).

## Dependencies
* Android Support Repository
* SBT

## Building
First, create a `local.properties` following [this guide](https://github.com/pfn/android-sdk-plugin#usage). Then:

    sbt clean android:packageRelease
