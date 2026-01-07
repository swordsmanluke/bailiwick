#!/bin/bash
# Start the Bailiwick test emulator
# Run with: ./scripts/start-emulator.sh

set -e

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
export ANDROID_AVD_HOME="$HOME/.config/.android/avd"

echo "=== Starting Bailiwick Test Emulator ==="
echo "AVD: Bailiwick_Test"
echo

# Check if KVM is available
if [ ! -e /dev/kvm ]; then
    echo "ERROR: /dev/kvm not found"
    echo "Run: sudo ./scripts/setup-emulator.sh"
    exit 1
fi

# Check if AVD exists
if [ ! -d "$ANDROID_AVD_HOME/Bailiwick_Test.avd" ]; then
    echo "ERROR: AVD not found at $ANDROID_AVD_HOME/Bailiwick_Test.avd"
    exit 1
fi

# Parse arguments
HEADLESS=false
for arg in "$@"; do
    case $arg in
        --headless)
            HEADLESS=true
            shift
            ;;
    esac
done

if [ "$HEADLESS" = true ]; then
    echo "Starting in headless mode..."
    "$ANDROID_HOME/emulator/emulator" -avd Bailiwick_Test \
        -no-window \
        -no-audio \
        -gpu swiftshader_indirect &

    echo "Waiting for emulator to boot..."
    "$ANDROID_HOME/platform-tools/adb" wait-for-device

    # Wait for boot to complete
    while [ "$("$ANDROID_HOME/platform-tools/adb" shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 2
    done

    echo "Emulator is ready!"
    "$ANDROID_HOME/platform-tools/adb" devices
else
    echo "Starting emulator with GUI..."
    "$ANDROID_HOME/emulator/emulator" -avd Bailiwick_Test
fi
