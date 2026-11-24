# SimpleLauncher for Windows (HTPC)

The Windows version of SimpleLauncher, for PCs plugged into a TV. See the main [README](../README.md)
for what it does and [RELEASING.md](../RELEASING.md) for how it's built and published.

- `SimpleLauncherHTPC/` – the app (C#, .NET 8, WPF). Screens are built in code, like the Android app.
- `installer/` – the Inno Setup script for the installer.
- `version.txt` – the build number for the next release.

Settings, downloaded artwork and your background photos are kept in `%LOCALAPPDATA%\SimpleLauncher`.
If something goes wrong, `errors.log` in that folder says what happened.
