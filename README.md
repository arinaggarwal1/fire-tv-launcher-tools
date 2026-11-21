# Fire TV launcher tools

Three lightweight APKs for Amazon Fire TV. **Switch Launcher** chooses an installed home screen and can restore the original Fire TV Home. **Fire TV Settings** and **Amazon Appstore** open those Amazon screens directly.

## Compatibility

Tested on a rooted Fire TV Cube (3rd generation), Fire OS 7.7.1.7 (Android 9). Switch Launcher requires root access (such as Magisk); the two shortcuts did not require root on the tested device. Other Fire TV Sticks, Cubes and firmware versions have not been verified and may use different components or handle the physical Home button differently. This project is not affiliated with Amazon.

## Download and install

Download the APKs from the [latest release](../../releases/latest), or from the `outputs` folder. With ADB debugging enabled and the Fire TV connected:

```sh
adb install -r Switch-Launcher.apk
adb install -r Fire-TV-Settings.apk
adb install -r Amazon-Appstore.apk
```

In Switch Launcher, select an installed launcher and choose **Continue** to review the change. Choose **Restore Fire TV Home** to re-enable the original Amazon home screen. An orange dot indicates the current launcher; a white ring indicates the selected launcher. The app attempts rollback if a command or verification fails.

Switching launchers changes system component settings and may behave differently across firmware. Keep ADB access available. The software Home-key check cannot confirm how every physical remote behaves.

The APKs are signed; the private signing key is not included. To update an existing installation, the APK must be signed by the same key as the installed version.
