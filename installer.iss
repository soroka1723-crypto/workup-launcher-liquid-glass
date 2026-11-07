; Workup Launcher - Inno Setup script
; Требуется: Inno Setup 6.x (https://jrsoftware.org/isinfo.php)
; Перед сборкой подготовьте app-image:
;   1) build.bat
;   2) jpackage --type app-image --input build --main-jar workup-launcher.jar --main-class launcher.Main --name WorkupLauncher --dest build\dist2 --icon res\workup.ico (если есть иконка)
; Затем: "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" installer.iss

#define MyAppName "Workup Launcher"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "Workup"
#define MyAppExeName "WorkupLauncher.exe"
#define MySourceDir "build\dist2\WorkupLauncher"

[Setup]
AppId={{C7E8F5A1-4D2B-4E9A-8F3C-1A2B3C4D5E6F}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
DisableProgramGroupPage=yes
; OutputBaseFilename=WorkupLauncher-Setup
OutputDir=build\installer
OutputBaseFilename=WorkupLauncher-Setup
Compression=lzma
SolidCompression=yes
WizardStyle=modern
PrivilegesRequired=lowest
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"; Flags: unchecked

[Files]
; Копирует app-image целиком (app\, runtime\, exe и др.). Папка {#MySourceDir} должна существовать.
Source: "{#MySourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
