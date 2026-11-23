<p align="center">
  <img src="app/src/main/res/drawable-nodpi/logo.png" alt="SimpleLauncher logo" width="220">
</p>

<h1 align="center">SimpleLauncher</h1>
<p align="center"><b>by Cherry Universe</b></p>

SimpleLauncher is a clean, simple home screen for Amazon Fire TV and other Android devices.
It replaces the cluttered, advert-filled home screen with just what you need: your apps, the time,
and the shows you're part-way through.

- **Your apps, your way.** Choose which apps sit across the middle of the screen, and in what order.
- **Continue Watching (Plex).** Carry on where you left off, straight from the home screen.
- **No adverts, no tracking, no autoplay.** It's quiet, fast and lightweight.
- **Made to look good.** Smooth animations, and background and accent colours you choose.
- **Updates itself when you ask.** Settings › Information › Software update checks here for a newer build and installs it.
- **Works on Fire TV and other Android devices.** It detects which it's running on and sets itself up to suit.

## What it looks like

<p align="center">
  <img src="docs/home-screen.png" alt="The SimpleLauncher home screen: logo, clock and date, your apps across the middle, and Continue Watching from Plex underneath" width="900">
</p>
<p align="center"><sub>The home screen on a Fire TV. On your TV, each app shows its own banner.</sub></p>

## Supported devices

Every device can use SimpleLauncher. Extras that change how the device behaves are only switched on
for devices that have been tested, so nothing can go wrong on the rest.

| Device | Model code | Tested on | SimpleLauncher | HomeCatch | Debloat & Restore |
|---|---|---|:---:|:---:|:---:|
| Fire TV Stick HD | AFTSS | Fire OS 7 | ✓ | ✓ | ✓ |
| Other Fire TV devices | | | ✓ (as an app) | Not yet | Not yet |
| Android TV, Google TV, phones and tablets (Android 7.1+) | | | ✓ (as your home screen) | Not needed | Not offered |

- **HomeCatch** makes the Home button on a Fire TV remote open SimpleLauncher instead of Amazon's home
  screen. Other Android devices let you choose your home screen anyway, so they don't need it.
- **Debloat** switches off Amazon's adverts, tracking and apps you choose. **Restore** switches them
  all back on.

Want your device supported? [Open an issue](../../issues) with its name and model code. SimpleScript
shows the model code when it connects.

## How to install

### The easy way: SimpleScript (Windows, macOS and Linux)

SimpleScript walks you through everything: it gets the tools your computer needs, helps you switch on
ADB debugging on your TV, connects to it, and then installs SimpleLauncher (with HomeCatch on
supported Fire TVs). On supported devices it can also debloat or restore your TV.

**Windows**

1. Download **SimpleScript.exe** from the [latest release](../../releases/latest)
   (**SimpleScript-arm64.exe** if your PC has an ARM processor, such as a Snapdragon laptop).
2. Double-click it and follow the steps on screen.
   Windows may say *"Windows protected your PC"* because the app isn't signed: select
   **More info › Run anyway**.

**macOS and Linux**

1. Download **SimpleScript.sh** from the [latest release](../../releases/latest).
2. Open **Terminal** and run:
   ```
   bash ~/Downloads/SimpleScript.sh
   ```
3. Follow the steps on screen.

You'll need your computer and TV on the same network. If ADB isn't installed, SimpleScript offers to
install it for you (with winget on Windows, Homebrew on a Mac, or by downloading it from Google) or
tells you how to do it yourself.

When debloating, you choose **y** or **n** for each item, or take the recommended set.
SimpleScript remembers your answers, so next time it only asks about anything new.

### Installing the app yourself

Download the APK from the [latest release](../../releases/latest) and install it however you
normally sideload apps (for example with Downloader on a Fire TV, or `adb install`).

This gives you SimpleLauncher as an app. On a Fire TV, HomeCatch needs a permission only a computer
can grant, so run SimpleScript once if you want the Home button to open SimpleLauncher.

### Updating

Once installed, SimpleLauncher updates itself when you ask:
**Settings › Information › Software update**. Your apps and settings are kept.

---

<sub>SimpleLauncher is an independent project and isn't affiliated with Amazon or Plex.</sub>
