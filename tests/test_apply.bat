@echo off
rem Applies the fix with the real ZombieBuddy agent and calls the real, patched
rem SmartTexture.calculate() on a hidden GL window (see ApplyCheck.java).
rem Build the JAR first (42\src\build.ps1), close the game, then run this.
rem Usage: test_apply.bat [path\to\SmartTextureLeakFix.jar]
setlocal
if not defined GAME set "GAME=C:\Program Files (x86)\Steam\steamapps\common\ProjectZomboid"
if not defined ZB set "ZB=%GAME%\..\..\workshop\content\108600\3619862853\mods\ZombieBuddy\libs\ZombieBuddy.jar"
set "HERE=%~dp0"
set "JAR=%~1"
if "%JAR%"=="" set "JAR=%HERE%..\42\media\java\client\SmartTextureLeakFix.jar"
set "CP=%GAME%;%GAME%\projectzomboid.jar;%ZB%;%JAR%"
if not exist "%HERE%build\tests" mkdir "%HERE%build\tests"
"%GAME%\jre64\bin\javac.exe" -nowarn -encoding UTF-8 -classpath "%CP%" -d "%HERE%build\tests" "%HERE%ApplyCheck.java"
if errorlevel 1 exit /b 1
pushd "%GAME%"
"%GAME%\jre64\bin\java.exe" "-javaagent:%ZB%" "-Djava.library.path=win64;." -Dstdout.encoding=UTF-8 -classpath "%HERE%build\tests;%CP%" ApplyCheck "%JAR%"
set "RC=%ERRORLEVEL%"
popd
exit /b %RC%
