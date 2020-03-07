rem		Your application name
set APP_NAME=HedgeCam
set APP_PACKAGE=com.caddish_hedgehog.hedgecam2

set MIN_SDK=15
set TARGET_SDK=26

set JDK_HOME=C:\Program Files\Java\jdk1.7.0_79
set BUILD_TOOLS_PATH=K:\android\sdk\build-tools\28.0.3
rem set NDK_PATH=K:\android\android-ndk-r11c
set PLATFORM_SDK_PATH=K:\android\sdk\platforms\android-28

set CLASS_PATH=%PLATFORM_SDK_PATH%\android.jar;libs\annotations.jar;libs\support-v4.jar

rem		ProGuard 5 home directory
set PROGUARD_HOME=D:\Android\proguard

rem		ADB executable file. Delete this variable if you don't want to install apk.
set ADB=D:\Android\adb\adb.exe

rem		For source code packing
set WINRAR="D:\Program Files\WinRAR\WinRAR.exe"

rem		Launch this activity after installing the apk. Delete this variable if you don't want to launch activity. 
set MAIN_ACTIVITY=%APP_PACKAGE%/%APP_PACKAGE%.MainActivity
