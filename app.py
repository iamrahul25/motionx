"""DualCam / time-wise build-and-run helper.

Menu:
  1. Build the debug APK and run it on a connected phone (USB debugging on).
  2. Build the debug APK and run it on an Android emulator (AVD).
  0. Exit.
"""

import os
import shutil
import subprocess
import sys
import time

PROJECT_DIR = os.path.dirname(os.path.abspath(__file__))
PACKAGE = "com.example.first_app"
LAUNCH_COMPONENT = f"{PACKAGE}/{PACKAGE}.MainActivity"
IS_WINDOWS = os.name == "nt"
GRADLEW = os.path.join(PROJECT_DIR, "gradlew.bat" if IS_WINDOWS else "gradlew")
DEBUG_APK = os.path.join(PROJECT_DIR, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
EMULATOR_BOOT_TIMEOUT = 300  # seconds


def sdk_dir():
    """Return the Android SDK root from the environment or local.properties."""
    for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
        value = os.environ.get(var)
        if value and os.path.isdir(value):
            return value
    props = os.path.join(PROJECT_DIR, "local.properties")
    if os.path.isfile(props):
        with open(props, encoding="utf-8") as f:
            for line in f:
                if line.strip().startswith("sdk.dir"):
                    value = line.split("=", 1)[1].strip().replace("\\:", ":").replace("\\\\", "\\")
                    if os.path.isdir(value):
                        return value
    return None


def tool(name):
    """Find an SDK tool (adb, emulator) or fall back to PATH."""
    root = sdk_dir()
    exe = name + (".exe" if IS_WINDOWS else "")
    if root:
        sub = "emulator" if name == "emulator" else "platform-tools"
        candidate = os.path.join(root, sub, exe)
        if os.path.isfile(candidate):
            return candidate
    found = shutil.which(name)
    if found:
        return found
    sys.exit(f"ERROR: '{name}' not found. Set ANDROID_HOME or install the Android SDK.")


def run(cmd, check=True, capture=False, timeout=None):
    """Run a command with an argument list (no shell)."""
    return subprocess.run(
        cmd,
        cwd=PROJECT_DIR,
        check=check,
        capture_output=capture,
        text=True,
        timeout=timeout,
    )


def build_debug_apk():
    print("\n[build] Running assembleDebug ...")
    cmd = [GRADLEW, "assembleDebug"]
    if IS_WINDOWS:
        cmd = ["cmd", "/c", GRADLEW, "assembleDebug"]
    try:
        run(cmd)
    except subprocess.CalledProcessError:
        print("[build] FAILED. Fix the errors above and try again.")
        return False
    if not os.path.isfile(DEBUG_APK):
        print(f"[build] APK not found at {DEBUG_APK}")
        return False
    print(f"[build] OK: {DEBUG_APK}")
    return True


def install_and_launch(serial=None):
    adb = tool("adb")
    base = [adb] + (["-s", serial] if serial else [])
    print("[install] Installing APK ...")
    run(base + ["install", "-r", DEBUG_APK])
    print("[launch] Starting app ...")
    run(base + ["shell", "am", "start", "-n", LAUNCH_COMPONENT])
    print("[done] App is running.")


def connected_devices():
    """Return serials of devices in the 'device' state (authorized, online)."""
    out = run([tool("adb"), "devices"], capture=True).stdout
    serials = []
    for line in out.splitlines()[1:]:
        parts = line.split()
        if len(parts) == 2 and parts[1] == "device":
            serials.append(parts[0])
    return serials


def run_on_phone():
    if not build_debug_apk():
        return
    devices = [d for d in connected_devices() if not d.startswith("emulator-")]
    if not devices:
        print("\nNo phone found. Connect the phone with USB debugging enabled,")
        print("accept the 'Allow USB debugging' prompt, and try again.")
        return
    serial = devices[0]
    if len(devices) > 1:
        print("\nMultiple phones connected:")
        for i, d in enumerate(devices, 1):
            print(f"  {i}. {d}")
        choice = input("Choose a phone number: ").strip()
        if not choice.isdigit() or not 1 <= int(choice) <= len(devices):
            print("Invalid choice.")
            return
        serial = devices[int(choice) - 1]
    print(f"\nPhone: {serial}")
    install_and_launch(serial)


def list_avds():
    out = run([tool("emulator"), "-list-avds"], capture=True).stdout
    return [line.strip() for line in out.splitlines() if line.strip()]


def wait_for_boot(adb, serial):
    deadline = time.time() + EMULATOR_BOOT_TIMEOUT
    while time.time() < deadline:
        res = run([adb, "-s", serial, "shell", "getprop", "sys.boot_completed"],
                  check=False, capture=True)
        if res.stdout.strip() == "1":
            return True
        time.sleep(3)
    return False


def run_on_emulator():
    if not build_debug_apk():
        return
    adb = tool("adb")
    running = [d for d in connected_devices() if d.startswith("emulator-")]
    if running:
        serial = running[0]
        print(f"\nEmulator already running: {serial}")
    else:
        avds = list_avds()
        if not avds:
            print("\nNo emulator (AVD) found. Create one in Android Studio: Device Manager.")
            return
        print("\nAvailable emulators:")
        for i, name in enumerate(avds, 1):
            print(f"  {i}. {name}")
        choice = input("Choose an emulator number: ").strip()
        if not choice.isdigit() or not 1 <= int(choice) <= len(avds):
            print("Invalid choice.")
            return
        avd = avds[int(choice) - 1]
        print(f"[emulator] Starting {avd} (this can take a minute) ...")
        # Detached so the emulator keeps running after this script exits.
        flags = {"creationflags": subprocess.DETACHED_PROCESS} if IS_WINDOWS else {"start_new_session": True}
        subprocess.Popen([tool("emulator"), "-avd", avd], cwd=PROJECT_DIR,
                         stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, **flags)
        print("[emulator] Waiting for the device ...")
        run([adb, "wait-for-device"], timeout=EMULATOR_BOOT_TIMEOUT)
        running = [d for d in connected_devices() if d.startswith("emulator-")]
        if not running:
            print("Emulator did not appear. Check it in Android Studio.")
            return
        serial = running[0]
        if not wait_for_boot(adb, serial):
            print("Emulator did not finish booting in time.")
            return
    print(f"\nEmulator: {serial}")
    install_and_launch(serial)


def menu():
    print("\n==============================")
    print(" DualCam app runner")
    print("==============================")
    print(" 1. Run APK on connected phone")
    print(" 2. Run in emulator")
    print(" 0. Exit")
    return input("Select an option: ").strip()


def main():
    while True:
        try:
            choice = menu()
            if choice == "1":
                run_on_phone()
            elif choice == "2":
                run_on_emulator()
            elif choice == "0":
                print("Bye.")
                return
            else:
                print("Please enter 1, 2 or 0.")
        except subprocess.CalledProcessError as exc:
            print(f"\nCommand failed (exit {exc.returncode}): {' '.join(map(str, exc.cmd))}")
        except subprocess.TimeoutExpired:
            print("\nTimed out waiting for a device.")
        except KeyboardInterrupt:
            print("\nCancelled.")
            return


if __name__ == "__main__":
    main()
