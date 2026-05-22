@echo off
echo Starting logcat monitoring for restore operations...
echo Press Ctrl+C to stop monitoring
echo.

"C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe" logcat -c
"C:\Program Files (x86)\Android\android-sdk\platform-tools\adb.exe" logcat | findstr /i "ValuePicsRepository ValueFinder AndroidRuntime FATAL ERROR Exception restore merge backup"
