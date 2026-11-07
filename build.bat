@echo off
setlocal
set JAVA=C:\Users\Admin\.jdks\ms-21.0.12\bin
set ROOT=%~dp0
set OUT=%ROOT%build
set LIBS=%ROOT%lib\gson.jar

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes"
mkdir "%OUT%\mods"

"%JAVA%\javac.exe" -encoding UTF-8 --release 21 -cp "%LIBS%" -d "%OUT%\classes" %ROOT%src\java\launcher\*.java
if errorlevel 1 exit /b 1

copy /y "%ROOT%\mods\workup-1.6.1.jar" "%OUT%\mods\" >nul
copy /y "%ROOT%\mods\fabric-api-0.141.6+1.21.11.jar" "%OUT%\mods\" >nul
copy /y "%ROOT%\mods\sodium-fabric-0.8.14+mc1.21.11.jar" "%OUT%\mods\" >nul

mkdir "%OUT%\tmp"
xcopy /e /i /q "%OUT%\classes\*" "%OUT%\tmp\" >nul
xcopy /e /i /q "%OUT%\mods\*" "%OUT%\tmp\mods\" >nul
xcopy /e /i /q "%ROOT%res\*" "%OUT%\tmp\" >nul

pushd "%OUT%\tmp"
"C:\Users\Admin\.jdks\ms-21.0.12\bin\jar.exe" xf "%LIBS%"
popd

"C:\Users\Admin\.jdks\ms-21.0.12\bin\jar.exe" cfe "%OUT%\workup-launcher.jar" launcher.Main -C "%OUT%\tmp" .
if errorlevel 1 exit /b 1

echo BUILD_OK