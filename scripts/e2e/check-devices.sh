#!/bin/bash
# check-devices.sh - Verify E2E test devices are connected and ready
#
# Device serials:
#   SM-G965U1 (Android 10): 3441333643543098
#   SM-G935T (Android 8.0):  7c276784

set -e

# Device configuration
DEVICE_1_SERIAL="3441333643543098"
DEVICE_1_NAME="SM-G965U1"
DEVICE_1_ANDROID="10"

DEVICE_2_SERIAL="7c276784"
DEVICE_2_NAME="SM-G935T"
DEVICE_2_ANDROID="8.0.0"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "=== E2E Device Check ==="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo -e "${RED}Error: adb not found in PATH${NC}"
    exit 1
fi

# Get list of connected devices
CONNECTED_DEVICES=$(adb devices | grep -v "List of devices" | grep -v "^$" | awk '{print $1}')

check_device() {
    local serial=$1
    local name=$2
    local expected_android=$3

    if echo "$CONNECTED_DEVICES" | grep -q "$serial"; then
        # Get device state
        local state=$(adb -s "$serial" get-state 2>/dev/null || echo "unknown")

        if [ "$state" = "device" ]; then
            # Get Android version
            local android_version=$(adb -s "$serial" shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
            local model=$(adb -s "$serial" shell getprop ro.product.model 2>/dev/null | tr -d '\r')

            echo -e "${GREEN}[OK]${NC} $name ($serial)"
            echo "     Model: $model"
            echo "     Android: $android_version"

            # Check if device is unlocked and ready
            local screen_state=$(adb -s "$serial" shell dumpsys power 2>/dev/null | grep "mHoldingDisplaySuspendBlocker" | awk -F= '{print $2}' | tr -d '\r')
            if [ "$screen_state" = "true" ]; then
                echo -e "     Screen: ${GREEN}ON${NC}"
            else
                echo -e "     Screen: ${YELLOW}OFF (may need to unlock)${NC}"
            fi

            return 0
        else
            echo -e "${YELLOW}[WARN]${NC} $name ($serial) - State: $state"
            return 1
        fi
    else
        echo -e "${RED}[MISSING]${NC} $name ($serial) - Not connected"
        return 1
    fi
}

# Check each device
FAILED=0

echo "Checking Device 1..."
if ! check_device "$DEVICE_1_SERIAL" "$DEVICE_1_NAME" "$DEVICE_1_ANDROID"; then
    FAILED=$((FAILED + 1))
fi
echo ""

echo "Checking Device 2..."
if ! check_device "$DEVICE_2_SERIAL" "$DEVICE_2_NAME" "$DEVICE_2_ANDROID"; then
    FAILED=$((FAILED + 1))
fi
echo ""

# Summary
echo "=== Summary ==="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All devices ready for E2E testing${NC}"
    exit 0
else
    echo -e "${RED}$FAILED device(s) not ready${NC}"
    echo ""
    echo "Troubleshooting:"
    echo "  1. Ensure USB debugging is enabled on each device"
    echo "  2. Check USB cable connections"
    echo "  3. Accept USB debugging prompt on device if shown"
    echo "  4. Try: adb kill-server && adb start-server"
    exit 1
fi
