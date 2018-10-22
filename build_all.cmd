@echo off

set DONT_INSTALL=true

call ./build_debug.cmd
call ./build_main.cmd
call ./build_global_debug.cmd
call ./build_global.cmd
call ./build_play.cmd
call ./pack_source.cmd