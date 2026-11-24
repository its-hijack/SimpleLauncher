; The SimpleLauncher for Windows installer (Inno Setup 6).
; Built by .github/workflows/htpc-release.yml, which sets HTPC_BUILD and publishes the app to ..\..\publish first.

#define AppBuild GetEnv("HTPC_BUILD")
#if AppBuild == ""
  #define AppBuild "dev"
#endif

[Setup]
AppId={{B6E3C0D2-6A2B-4C1E-9C7F-5C1A0E2F9D41}
AppName=SimpleLauncher
AppVersion={#AppBuild}
AppVerName=SimpleLauncher for Windows (build {#AppBuild})
AppPublisher=Cherry Universe
AppPublisherURL=https://github.com/its-hijack/SimpleLauncher
AppSupportURL=https://github.com/its-hijack/SimpleLauncher/issues
DefaultDirName={autopf}\SimpleLauncher
DefaultGroupName=SimpleLauncher
DisableProgramGroupPage=yes
DisableWelcomePage=no
OutputDir=..\..\out
OutputBaseFilename=SimpleLauncherHTPC-{#AppBuild}-Setup
SetupIconFile=..\SimpleLauncherHTPC\Assets\icon.ico
UninstallDisplayIcon={app}\SimpleLauncher.exe
UninstallDisplayName=SimpleLauncher for Windows
Compression=lzma2/max
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
PrivilegesRequired=admin
CloseApplications=yes
RestartApplications=no
MinVersion=10.0

[Messages]
WelcomeLabel1=Install SimpleLauncher
WelcomeLabel2=A simple, full-screen home for your PC, by Cherry Universe.%n%nThis installs build {#AppBuild} on your computer.

[Tasks]
Name: "desktopicon"; Description: "Add a shortcut to the desktop"; GroupDescription: "Shortcuts and starting up:"
Name: "startmenu"; Description: "Add to the Start menu"; GroupDescription: "Shortcuts and starting up:"
Name: "startup"; Description: "Start with Windows (also in SimpleLauncher's Settings › Windows)"; GroupDescription: "Shortcuts and starting up:"; Flags: unchecked

[Files]
Source: "..\..\publish\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{autoprograms}\SimpleLauncher"; Filename: "{app}\SimpleLauncher.exe"; Tasks: startmenu
Name: "{autodesktop}\SimpleLauncher"; Filename: "{app}\SimpleLauncher.exe"; Tasks: desktopicon

[Registry]
Root: HKCU; Subkey: "Software\Microsoft\Windows\CurrentVersion\Run"; ValueType: string; ValueName: "SimpleLauncher"; ValueData: """{app}\SimpleLauncher.exe"" --startup"; Tasks: startup

[Run]
Filename: "{app}\SimpleLauncher.exe"; Description: "Open SimpleLauncher now"; Flags: nowait postinstall skipifsilent runasoriginaluser
; Updates from inside SimpleLauncher install quietly, then open it again.
Filename: "{app}\SimpleLauncher.exe"; Flags: nowait runasoriginaluser; Check: WizardSilent

[Code]
// Removing SimpleLauncher also undoes Start with Windows and console mode, so Windows is left as it was.
procedure CurUninstallStepChanged(CurUninstallStep: TUninstallStep);
var
  Value: String;
begin
  if CurUninstallStep = usUninstall then
  begin
    if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'SimpleLauncher', Value) then
      RegDeleteValue(HKCU, 'Software\Microsoft\Windows\CurrentVersion\Run', 'SimpleLauncher');
    if RegQueryStringValue(HKCU, 'Software\Microsoft\Windows NT\CurrentVersion\Winlogon', 'Shell', Value) then
      if Pos('SimpleLauncher', Value) > 0 then
        RegDeleteValue(HKCU, 'Software\Microsoft\Windows NT\CurrentVersion\Winlogon', 'Shell');
  end;
end;
