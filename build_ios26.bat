@echo off
setlocal
set JAVA=C:\Users\Admin\.jdks\ms-21.0.12\bin
set ROOT=C:\Users\Admin\Desktop\WorkupLauncher-LiquidGlass
set DIST=C:\Users\Admin\Desktop\Workup-iOS26-Launcher
set OUT=%ROOT%\build

rem 1) Compiling the launcher (same sources, no code changes)
if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes"
mkdir "%OUT%\mods"

"%JAVA%\javac.exe" -encoding UTF-8 --release 21 -cp "%ROOT%\lib\gson.jar" -d "%OUT%\classes" %ROOT%\src\java\launcher\*.java
if errorlevel 1 exit /b 1

copy /y "%ROOT%\mods\workup-1.6.1.jar" "%OUT%\mods\" >nul
copy /y "%ROOT%\mods\fabric-api-0.141.6+1.21.11.jar" "%OUT%\mods\" >nul
copy /y "%ROOT%\mods\sodium-fabric-0.8.14+mc1.21.11.jar" "%OUT%\mods\" >nul

mkdir "%OUT%\tmp"
mkdir "%OUT%\tmp\mods"
xcopy /e /i /q "%OUT%\classes\*" "%OUT%\tmp\" >nul
xcopy /e /i /q "%OUT%\mods\*" "%OUT%\tmp\mods\" >nul
xcopy /e /i /q "%ROOT%\res\*" "%OUT%\tmp\" >nul

pushd "%OUT%\tmp"
"%JAVA%\jar.exe" xf "%ROOT%\lib\gson.jar"
popd

"%JAVA%\jar.exe" cfe "%OUT%\workup-launcher.jar" launcher.Main -C "%OUT%\tmp" .
if errorlevel 1 exit /b 1

rem 2) Collect_DIST: new iOS 26 style launcher folder (app-image + 7z)
if exist "%DIST%" rmdir /s /q "%DIST%"
mkdir "%DIST%"

rem 2a) app-image (keeps own JRE, standalone)
"%JAVA%\jpackage.exe" --type app-image ^
  --input "%OUT%" ^
  --main-jar workup-launcher.jar ^
  --main-class launcher.Main ^
  --name WorkupLauncher ^
  --app-version 2.0.0 ^
  --dest "%DIST%"
if errorlevel 1 exit /b 1

rem 3) 7-Zip executable for ONE-CLICK zip of the launcher (via 7z if available)
set SEVENZIP=
for %%P in (
  "C:\Program Files\7-Zip\7z.exe"
  "C:\Program Files (x86)\7-Zip\7z.exe"
  "%DIST%\7z.exe"
  "C:\Users\Admin\Desktop\Workup-Offline-Site\7z.exe"
) do (
  if exist %%P if not defined SEVENZIP set SEVENZIP=%%~P
)
if not defined SEVENZIP (
  rem Use the Windows tar (bag.exe) as a fallback packs zip - keep a copy for future manual re-pack
  tar -a -c -f "%DIST%\WorkupLauncher-iOS26.zip" -C "%DIST%" WorkupLauncher
  rem no 7z found, leave .zip (fallback), user can pass 7z path or drop 7z.exe near script to pack .7z
  echo PACKED zip fallback ^(no 7z.exe found^)
) else (
  "%SEVENZIP%" a -t7z -mx=9 -mmt=on "%DIST%\WorkupLauncher-iOS26.7z" "%DIST%\WorkupLauncher"
  echo PACKED 7z ^(via %SEVENZIP%^)
)

echo DIST_OK
pause
