@echo off
rem		Command file for building apk. Written by Caddish Hedgehog.
rem		No Eclipse, or Android Studio, or other buggy shit! Just JDK and Android SDK!

rem	Main configuration
call ./build/config.cmd

set DEBUG=true
set GOOGLE_PLAY=false

set FILENAME_SUFFIX=_global_debug
rem		Directory for generated files
set GEN_DIR=%CD%\gen\global_debug
rem		Resource directories for aapt.exe
set RES=-S "%CD%\res" -S "%CD%\res-lang" -S "%CD%\res-parsed" -S "%GEN_DIR%\res"

call ../key.cmd
rem		File key.cmd must contain following variables:
rem		set KEYSTORE=<your keystore>
rem		set KEYSTORE_PASS=<your keystore password>
rem		set KEY=<certificate name>
rem		set KEY_PASS=<certificate password>

call ./build/main.cmd