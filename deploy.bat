@echo off
setlocal
rem ctbrec 5.4.4 ?????????????? jar??? common-5.4.4.jar?
set TARGET=%~1
if "%TARGET%"=="" set TARGET=D:\ctbrec-fixed-544
if not exist "%TARGET%\ctbrec.jar" if not exist "%TARGET%\lib" (
  echo [!] ???? ctbrec ????: %TARGET%
  echo     ??: deploy.bat "D:\ctbrec-fixed-544"
  exit /b 1
)
for %%F in (common-5.3.4.jar) do (
  if exist "%TARGET%\lib\%%F" copy /Y "%TARGET%\lib\%%F" "%TARGET%\lib\%%F.bak" >nul
)
copy /Y "%~dp0dist\common-5.3.4.jar" "%TARGET%\lib\common-5.3.4.jar"
if errorlevel 1 (
  echo [!] ?????????????? ctbrec ???
  exit /b 1
)
echo [OK] ??? -> %TARGET%\lib\common-5.3.4.jar
echo [i]  ??????? jar ????
echo        pkey   = NTK9aqcLmNFMWrpQ   ^(????? keyId^)
echo        xorKey = tOcYOap4Ty1l9Jzb   ^(??????^)
endlocal
