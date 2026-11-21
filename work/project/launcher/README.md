# Fire TV launcher controls

Two independent, on-device Android TV apps for the Fire TV Cube (Fire OS 7 / API 28).

* `dev.tvlaunchers.amazonhome` re-enables Amazon's two documented Home components, sets `com.amazon.tv.launcher/.ui.HomeActivity_vNext` as preferred, verifies resolution, then launches it.
* `dev.tvlaunchers.switcher` lists installed `MAIN` + `HOME` activities (and Refract when present), confirms the selection, disables only the two documented Amazon components, sets and launches the selected component, and checks the resolved Home activity.

Both use Magisk's `su -c`, show command output, and attempt rollback after a command or verification failure. They never touch Refract data, install state, or notification access. They do not simulate the physical remote Home key; Fire OS may still intercept Home independently of Android's preferred activity.

Build from the workspace root with `python3 work/project/launcher/build.py`. The package IDs are stable. Supply your own local signing key and password. No device connection is used by the build.
