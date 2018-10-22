@echo off
rem		Command file for building apk. Written by Caddish Hedgehog.
rem		No Eclipse, or Android Studio, or other buggy shit! Just JDK and Android SDK!

rem		Your application name
set APP_NAME=HedgeCam
set APP_PACKAGE=com.caddish_hedgehog.hedgecam2

set MIN_SDK=15
set TARGET_SDK=26

set JDK_HOME=C:\Program Files\Java\jdk1.7.0_79
set BUILD_TOOLS_PATH=K:\android\sdk\build-tools\23.0.3
set ANDROID_JAR=K:\android\sdk\platforms\android-24\android.jar
set CLASS_PATH=%ANDROID_JAR%;libs\annotations.jar;libs\android-support-v4.jar

set PROGUARD_HOME=D:\Android\proguard

set ZIPALIGN=D:\Tools\adt\sdk\tools\zipalign.exe

call ../key.cmd
rem		File key.cmd must contain following variables:
rem		set KEYSTORE=<your keystore>
rem		set KEYSTORE_PASS=<your keystore password>
rem		set KEY=<certificate name>
rem		set KEY_PASS=<certificate password>

rem		ADB executable file. Delete this variable if you don't want to install apk.
set ADB=D:\Android\adb\adb.exe

rem		Launch this activity after installing the apk. Delete this variable if you don't want to launch activity. 
set MAIN_ACTIVITY=%APP_PACKAGE%/%APP_PACKAGE%.MainActivity

rem		Directory for generated files
set GEN_DIR=%CD%\gen

@md %GEN_DIR%\dex > nul 2> nul
@md %GEN_DIR%\java > nul 2> nul
@md %GEN_DIR%\obj > nul 2> nul


rem		Parsing version name from AndroidManifest.xml
for /F usebackq^ tokens^=2^ delims^=^" %%i in (`findstr android:versionName AndroidManifest.xml`) do set VERSION=%%i
IF [%VERSION%] == [] goto err_version


echo:
echo:
echo Compiling renderscripts...
FOR /R "%CD%\rs" %%i IN (*.rs) DO call set RS_LIST=%%RS_LIST%% %%i
call "%BUILD_TOOLS_PATH%\llvm-rs-cc.exe" -target-api 19 -I "%BUILD_TOOLS_PATH%\renderscript\include" -I "%BUILD_TOOLS_PATH%\renderscript\clang-include" -o "%GEN_DIR%\res\raw" -java-reflection-path-base "%GEN_DIR%\java" %RS_LIST%
@if errorlevel 1 goto err_compile_rs

echo package %APP_PACKAGE%;public class MyDebug {public static final boolean LOG = false;} > "%GEN_DIR%\java\MyDebug.java"

echo:
echo:
echo Creating R.java...
call "%BUILD_TOOLS_PATH%\aapt.exe" package -f -m --auto-add-overlay --min-sdk-version %MIN_SDK% --target-sdk-version %TARGET_SDK% -S "%CD%\res" -S "%CD%\res-release" -S "%GEN_DIR%\res" -J "%CD%\src" -M "%CD%\AndroidManifest.xml" -I "%ANDROID_JAR%" -J "%GEN_DIR%\java"
@if errorlevel 1 goto err_r


IF NOT EXIST "%GEN_DIR%\sources.lst" (
	echo:
	echo:
	echo Creating sources.lst
	FOR /R "%CD%\src" %%i IN (*.java) DO ECHO %%i >> "%GEN_DIR%\sources.lst"
	FOR /R "%GEN_DIR%\java" %%i IN (*.java) DO ECHO %%i >> "%GEN_DIR%\sources.lst"
)

echo:
echo:
echo Compiling...
call "%JDK_HOME%\bin\javac" -nowarn -d "%GEN_DIR%\obj" -cp "%CLASS_PATH%" -sourcepath "%CD%\src" @"%GEN_DIR%\sources.lst"
@if errorlevel 1 goto err_compile

IF [%PROGUARD_HOME%] == [] goto dex_no_shrink


@rmdir /S /Q "%GEN_DIR%\opt" > nul 2> nul
echo:
echo:
echo Shrinking classes...
call "%JDK_HOME%\bin\java" -jar "%PROGUARD_HOME%\lib\proguard.jar" @proguard_options -injars "%GEN_DIR%\obj" -injars "%CD%\libs" -outjars "%GEN_DIR%\opt" -libraryjars %ANDROID_JAR% -verbose
@if errorlevel 1 goto err_proguard


echo:
echo:
echo Creating classes.dex...
call "%JDK_HOME%\bin\java" -jar "%BUILD_TOOLS_PATH%\lib\dx.jar" --dex --output="%GEN_DIR%\dex\classes.dex" "%GEN_DIR%\opt"
@if errorlevel 1 goto err_dx
goto package


:dex_no_shrink
echo:
echo:
echo Creating classes.dex...
call "%JDK_HOME%\bin\java" -jar "%BUILD_TOOLS_PATH%\lib\dx.jar" --dex --output="%GEN_DIR%\dex\classes.dex" "%GEN_DIR%\obj" "%CD%\libs"
@if errorlevel 1 goto err_dx

:package
echo:
echo:
echo Creating package...
call "%BUILD_TOOLS_PATH%\aapt.exe" package -f --auto-add-overlay --min-sdk-version %MIN_SDK% --target-sdk-version %TARGET_SDK% -M "%CD%\AndroidManifest.xml" -S "%CD%\res" -S "%CD%\res-release" -S "%GEN_DIR%\res" -A "%CD%\assets" -I "%ANDROID_JAR%" -F "%GEN_DIR%\%APP_NAME%_unsigned.apk" %GEN_DIR%\dex
@if errorlevel 1 goto err_pack

IF [%KEYSTORE%] == [] goto zipalign
echo:
echo:
echo Signing...
call "%JDK_HOME%\bin\jarsigner" -sigalg MD5withRSA -digestalg SHA1 -keystore %KEYSTORE% -storepass %KEYSTORE_PASS% -keypass %KEY_PASS% -signedjar "%GEN_DIR%\%APP_NAME%_unaligned.apk" "%GEN_DIR%\%APP_NAME%_unsigned.apk" %KEY%
@if errorlevel 1 goto err_sign


:zipalign
echo:
echo:
echo Aligning...
call "%ZIPALIGN%" -f 4 "%GEN_DIR%\%APP_NAME%_unaligned.apk" "%CD%\%APP_NAME%_%VERSION%.apk"
@if errorlevel 1 goto err_align

@del "%GEN_DIR%\%APP_NAME%_unsigned.apk"
@del "%GEN_DIR%\%APP_NAME%_unaligned.apk"

IF [%ADB%] == [] goto exit
echo:
echo:
echo Installing...
call "%ADB%" wait-for-device
call "%ADB%" install -r "%CD%\%APP_NAME%_%VERSION%.apk"
@if errorlevel 1 goto err_install

IF [%MAIN_ACTIVITY%] == [] goto exit

echo:
echo:
echo Launching...
call "%ADB%" shell input keyevent KEYCODE_WAKEUP
call "%ADB%" shell am start -n "%MAIN_ACTIVITY%"
@if errorlevel 1 goto err_launch

goto exit

:err_version
echo Cannot parse version from AndroidManifest.xml
goto make_pause

:err_r
echo Error creating R.java
goto make_pause

:err_compile_rs
echo Compile renderscripts error
goto make_pause

:err_compile
echo Compile error
goto make_pause

:err_proguard
echo Error executing proguard
goto make_pause

:err_dx
echo Error creating classes.dex
goto make_pause

:err_pack
echo Error creating package
goto make_pause

:err_sign
echo Sign error
goto make_pause

:err_align
echo Align error
goto make_pause

:err_install
echo Installing error
goto make_pause

:err_launch
echo Launching error
goto make_pause

:make_pause
pause
:exit
