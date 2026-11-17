@echo off
setlocal
rem ??????????????????????? = streamName?? modelId?
set LIB=%~1
set SID=%~2
if "%LIB%"=="" set LIB=D:\ctbrec-fixed-544\lib
if "%SID%"=="" set SID=248813265
if not exist "%~dp0out" mkdir "%~dp0out"
javac -encoding UTF-8 -cp "%LIB%\*" -d "%~dp0out" "%~dp0RecSim.java" "%~dp0XorProof.java" || exit /b 1
java -cp "%~dp0out;%LIB%\*" RecSim %SID%
endlocal
