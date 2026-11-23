# Releasing SimpleLauncher

## Version numbers
- **Release version**: what people see, e.g. **V1**.
- **Build**: the internal build, `DDMMYY-build`, e.g. **231126-2.7**. The GitHub tag uses the same value.

Both live at the top of `app/build.gradle` (`releaseVersion`, `buildDate`, `buildVersion`).
Raise `versionCode` by 1 for every build. Android uses it to tell which build is newer.

## Publishing a build
1. Update the numbers in `app/build.gradle` and commit.
2. Tag the commit with the build and push it:
   ```
   git tag 231126-2.7
   git push origin 231126-2.7
   ```
3. GitHub Actions builds the app, signs it, and publishes `SimpleLauncher-V1-231126-2.7.apk` on the Releases page.

## Signing key (one-time setup)
Android only installs an update that's signed with the same key as the installed app.
Builds here are signed with the key in the repository secret `SIGNING_KEYSTORE_BASE64`
(Settings → Secrets and variables → Actions → New repository secret).

To use the Android Studio debug key from a Mac (so releases update copies installed from Android Studio):
```
base64 -i ~/.android/debug.keystore | pbcopy
```
Then paste it as the value of `SIGNING_KEYSTORE_BASE64`.

For a dedicated release key, also add `SIGNING_STORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`.

## Building locally
Open the project in Android Studio and choose **Build → Build APK(s)**, or run `./gradlew assembleDebug`.
Plain Java and Android framework APIs only, with no dependencies.

## How TVs get the update

Once a release is published, SimpleLauncher on any TV can install it from Settings › Information › Software update.
It compares the release's tag with its own build (date first, then build number), so the tag must look like
`231126-2.9` and every new release needs a higher build. Only the latest published release is offered; drafts and
pre-releases are ignored, and the APK must be signed with the same key as the copy on the TV.

## Adding a supported device or a device extra

Both lists are at the top of `SimpleScript.sh`:

- `SUPPORTED_DEVICES`: one line per tested device (`model code|name|Fire OS version|debloat|HomeCatch`).
  Only listed devices get HomeCatch and the device extras (Debloat and Restore, shown only in SimpleScript on
  that device). Add it to the README table too, but keep the extras out of the README.
- `TESTING_DEVICES`: devices being tested (`model code|name|Fire OS version`). They get HomeCatch only, and
  aren't called supported. Once one is confirmed, move it up to `SUPPORTED_DEVICES` and the README table.
- `DEBLOAT_LIST`: one line per app (`group|package|description|recommended y/n|disable or uninstall`).
  People who debloated before are asked about new lines next time, marked NEW.

The Windows version, `SimpleScript.ps1`, is made from `windows/SimpleScript.template.ps1` by
`bash windows/build.sh`, which copies both lists in from `SimpleScript.sh`. Run it after changing either
file and commit the result. Each release also runs it and attaches `SimpleScript.ps1` and `SimpleScript.sh`.

## The home screen picture in the README

`docs/home-screen.png` is rendered from `docs/home-screen.html`. When the home screen's design changes,
update that HTML to match and run `bash docs/render.sh` (needs Google Chrome), then commit both.
