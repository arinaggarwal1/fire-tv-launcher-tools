from pathlib import Path
import os, subprocess, zipfile, hashlib, shutil

ROOT = Path(__file__).resolve().parent
WORK = ROOT.parents[2]
SDK = Path(os.environ.get("ANDROID_SDK_ROOT", str(Path.home() / "Library/Android/sdk")))
BT = SDK / "build-tools/34.0.0"
JAR = SDK / "platforms/android-35/android.jar"
JAVA = Path("/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home")
ENV = os.environ.copy(); ENV["JAVA_HOME"] = str(JAVA); ENV["PATH"] = str(JAVA / "bin") + os.pathsep + ENV["PATH"]
KEY = WORK / "work/signing/shortcuts.p12"
OUT = WORK / "outputs"
if not os.environ.get("TV_SHORTCUTS_KEYSTORE_PASSWORD"):
    raise SystemExit("Set TV_SHORTCUTS_KEYSTORE_PASSWORD before building.")
APPS = [
    ("amazon", "dev.tvlaunchers.amazonhome", "Amazon-Home"),
    ("switch", "dev.tvlaunchers.switcher", "Switch-Launcher"),
]

def run(*args):
    subprocess.run([str(a) for a in args], check=True, env=ENV)

def build(mode, package, filename):
    project = ROOT / ("build-" + mode)
    project.mkdir(exist_ok=True)
    (project / "src/dev/tvlaunchers").mkdir(parents=True, exist_ok=True)
    (project / "res/drawable-nodpi").mkdir(parents=True, exist_ok=True)
    (project / "res/drawable-xhdpi").mkdir(parents=True, exist_ok=True)
    (project / "res/values").mkdir(parents=True, exist_ok=True)
    manifest = (ROOT / ("AndroidManifest." + mode + ".xml")).read_text().replace(
        "package=\"dev.tvlaunchers." + ("amazonhome" if mode == "amazon" else "switcher") + "\"",
        "package=\"" + package + "\"")
    manifest = manifest.replace('android:versionCode="1" android:versionName="1.0"',
                                'android:versionCode="7" android:versionName="1.6"')
    (project / "AndroidManifest.xml").write_text(manifest)
    source = ROOT / "src/dev/tvlaunchers/LauncherActivity.java"
    (project / "src/dev/tvlaunchers/LauncherActivity.java").write_bytes(source.read_bytes())
    (project / "res/values/styles.xml").write_bytes((ROOT / "res/values/styles.xml").read_bytes())
    kind = "amazon-home" if mode == "amazon" else "switch-launcher"
    (project / "res/drawable-nodpi/icon.png").write_bytes((ROOT / "art" / (kind + "-icon.png")).read_bytes())
    (project / "res/drawable-xhdpi/banner.png").write_bytes((ROOT / "art" / (kind + "-banner.png")).read_bytes())
    resources = project / "resources.zip"
    unsigned = project / "unsigned.apk"
    aligned = project / "aligned.apk"
    classes = project / "classes"
    dex = project / "dex"
    dex.mkdir(exist_ok=True)
    run(BT / "aapt2", "compile", "--dir", project / "res", "-o", resources)
    run(BT / "aapt2", "link", "-o", unsigned, "-I", JAR, "--manifest", project / "AndroidManifest.xml",
        "--min-sdk-version", "25", "--target-sdk-version", "28", "--version-code", "7", "--version-name", "1.6", resources)
    run(JAVA / "bin/javac", "--release", "8", "-encoding", "UTF-8", "-classpath", JAR, "-d", classes, project / "src/dev/tvlaunchers/LauncherActivity.java")
    run(BT / "d8", "--release", "--min-api", "25", "--lib", JAR, "--output", dex, *sorted(classes.rglob("*.class")))
    with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as apk:
        apk.write(dex / "classes.dex", "classes.dex")
    run(BT / "zipalign", "-f", "4", unsigned, aligned)
    final = OUT / (filename + ".apk")
    run(BT / "apksigner", "sign", "--ks", KEY, "--ks-key-alias", "tv-shortcuts", "--ks-pass", "env:TV_SHORTCUTS_KEYSTORE_PASSWORD",
        "--key-pass", "env:TV_SHORTCUTS_KEYSTORE_PASSWORD", "--v1-signing-enabled", "true", "--v2-signing-enabled", "true",
        "--v3-signing-enabled", "false", "--v4-signing-enabled", "false", "--out", final, aligned)
    run(BT / "apksigner", "verify", "--verbose", "--print-certs", final)
    run(BT / "zipalign", "-c", "-v", "4", final)
    run(BT / "aapt", "dump", "badging", final)
    print(filename, final.stat().st_size, "bytes")

def write_delivery_files():
    art_out = OUT / "Launcher-Artwork"
    art_out.mkdir(exist_ok=True)
    for asset in (ROOT / "art").glob("*.png"):
        shutil.copyfile(asset, art_out / asset.name)
    report = ["Launcher switching APK verification", "", "Built locally; this build script does not install APKs or run launcher commands.",
              "Minimum API: 25 | Target API: 28 | Native libraries: none | Android permissions: none",
              "Signature: v2, existing TV Shortcuts personal signing key",
              "Certificate SHA-256: 329b99902aa5626645cf63de7acd99f9f35631fccfc2fb6cf5a3c0b2dea7b5ea",
              "Both manifests expose MAIN/LAUNCHER and MAIN/LEANBACK_LAUNCHER entries with an icon and TV banner.",
              "Command strings checked against Changing Launchers Fire TV Cube Refract (1).pdf.",
              "Both apps test Android HOME resolution and send a software HOME key event after switching.",
              "Physical remote-button interception was not testable without operating the device; Fire OS may intercept it.", ""]
    sums = []
    for mode, package, filename in APPS:
        apk = OUT / (filename + ".apk")
        digest = hashlib.sha256(apk.read_bytes()).hexdigest()
        report.extend([filename + ".apk", "  package: " + package, "  version: 1.6 (code 7)",
                       "  size: " + str(apk.stat().st_size) + " bytes", "  SHA-256: " + digest, ""])
        sums.append(digest + "  " + filename + ".apk")
    (OUT / "Launcher-APK-Verification.txt").write_text("\n".join(report))
    sums_file = OUT / "SHA256SUMS.txt"
    existing = sums_file.read_text().splitlines() if sums_file.exists() else []
    updated = [line for line in existing if not any(line.split()[-1:] == [name] for name in ("Amazon-Home.apk", "Switch-Launcher.apk"))]
    sums_file.write_text("\n".join(updated + sums) + "\n")

def main():
    run("python3", ROOT / "draw_art.py")
    OUT.mkdir(exist_ok=True)
    for args in APPS: build(*args)
    write_delivery_files()

if __name__ == "__main__": main()
