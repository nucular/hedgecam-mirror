@echo off

rem	Main configuration
call ./build/config.cmd

@set FILELIST=pack_source.lst

for /F usebackq^ tokens^=2^ delims^=^" %%i in (`findstr android:versionName AndroidManifest.xml`) do set VERSION=%%i
IF [%VERSION%] == [] goto err_version

set ARCHIVE=%APP_NAME%_%VERSION%_src.zip

IF EXIST %ARCHIVE% goto err_exist

%WINRAR% a -afzip -k -m5 -r -dh -zgpl-3.0.txt %ARCHIVE% @%FILELIST%

goto exit

:err_version
echo Cannot parse version from AndroidManifest.xml
goto make_pause

:err_exist
echo The archive already exists
goto make_pause

:make_pause
pause

:exit