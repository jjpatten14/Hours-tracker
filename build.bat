@echo off
echo Building Dayforce Tracker APK...

set ANDROID_HOME=C:\Users\jjpat\AppData\Local\Android\Sdk
set JAVA_HOME=C:\Program Files\Java\jdk-11.0.11

echo Using Android SDK: %ANDROID_HOME%
echo Using Java: %JAVA_HOME%

echo.
echo Creating output directories...
mkdir app\build\intermediates\classes 2>nul
mkdir app\build\outputs\apk\debug 2>nul

echo.
echo Compiling Kotlin sources...
%ANDROID_HOME%\build-tools\33.0.1\aapt2 compile --dir app\src\main\res -o app\build\intermediates\res.zip

echo.
echo Linking resources...
%ANDROID_HOME%\build-tools\33.0.1\aapt2 link -o app\build\intermediates\app.apk -I %ANDROID_HOME%\platforms\android-33\android.jar --manifest app\src\main\AndroidManifest.xml app\build\intermediates\res.zip

echo.
echo Compiling Java/Kotlin classes...
"%JAVA_HOME%\bin\javac" -cp "%ANDROID_HOME%\platforms\android-33\android.jar" -d app\build\intermediates\classes app\src\main\java\com\dayforcetracker\*.kt

echo.
echo Creating DEX files...
%ANDROID_HOME%\build-tools\33.0.1\d8 --lib %ANDROID_HOME%\platforms\android-33\android.jar --output app\build\intermediates\ app\build\intermediates\classes\com\dayforcetracker\*.class

echo.
echo Building APK...
%ANDROID_HOME%\build-tools\33.0.1\aapt package -f -m -J app\build\intermediates\ -S app\src\main\res -M app\src\main\AndroidManifest.xml -I %ANDROID_HOME%\platforms\android-33\android.jar

echo.
echo APK build complete!
echo Check app\build\outputs\apk\debug\ for the APK file

pause