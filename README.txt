ZedF9Logger - Project skeleton
Generated: 2025-10-07 15:19 UTC

What this project is:
- Android app (Kotlin) that connects to a USB-OTG serial GNSS receiver (e.g. u-blox ZED-F9)
  and reads NMEA sentences. On app start it creates a CSV file named session_YYYYMMDD_HHMMSS.csv
  inside the app's external files directory. Pressing "Enregistrer point" writes the last parsed
  NMEA GGA position (timestamp,lat,lon,alt) into the CSV.

Important notes / limitations:
- This environment cannot compile an APK. I created the full Android Studio project skeleton.
  You must open it with Android Studio (Arctic Fox or newer), let it download Gradle/SDK,
  then Build > Build APK(s) to produce an installable debug APK.
- The project uses the USB serial library 'com.github.mik3y:usb-serial-for-android:3.4.6'.
  Make sure Android Studio can fetch it from Maven/JitPack; repositories are included.
- This app assumes the GNSS receiver emits NMEA (GGA/RMC). If your ZED-F9 is set to output
  only UBX binary or RAWX (raw observations), configure the module (u-center) to output NMEA GGA
  on the USB port.
- For RTK-corrected positions: your ZED-F9 must itself be connected to the RTK network (NTRIP or
  other) so it outputs corrected NMEA solution. This app does not implement NTRIP or RTK computation.
- Permissions: on modern Android you may need to grant USB permissions. The app checks for devices
  via usb-serial-prober and attempts to open the device; you might need to accept a system dialog.
- CSV location: /storage/emulated/0/Android/data/com.example.zedf9logger/files
  (accessible via USB or Files app).

How to build:
1) Install Android Studio.
2) File > New > Import Project and choose this folder.
3) Let Gradle sync. If prompted, install missing SDK components.
4) Connect an Android device for testing (enable USB debugging if needed).
5) Build > Build Bundle(s) / APK(s) > Build APK(s). Android Studio will create a debug APK signed
   with the local debug key (since you wanted a debug APK).
6) Install the APK on your phone and run. Connect the ZED-F9 via USB-OTG and press "Connecter USB".
   Then press "Enregistrer point" to log the current GPS solution into the CSV file.

If you want, I can:
- add more CSV fields (fix type, HDOP, number of satellites),
- add automatic capture on button long-press, or
- try to produce a compiled APK if you provide CI with Android SDK access.

