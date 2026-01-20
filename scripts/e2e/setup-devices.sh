#!/bin/bash
# setup-devices.sh - Install app on E2E test devices and clear app data
#
# Device serials:
#   SM-G965U1 (Android 10): 3441333643543098
#   SM-G935T (Android 8.0):  7c276784
#
# Usage:
#   ./setup-devices.sh           # Build and install on both devices
#   ./setup-devices.sh --no-build # Install existing APK without rebuilding
#   ./setup-devices.sh --clear-only # Only clear app data, don't reinstall

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
APK_PATH="$PROJECT_ROOT/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE_NAME="com.perfectlunacy.bailiwick"

# Device configuration
DEVICE_1_SERIAL="3441333643543098"
DEVICE_1_NAME="SM-G965U1"

DEVICE_2_SERIAL="7c276784"
DEVICE_2_NAME="SM-G935T"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Parse arguments
BUILD=true
INSTALL=true
CLEAR_DATA=true

while [[ $# -gt 0 ]]; do
    case $1 in
        --no-build)
            BUILD=false
            shift
            ;;
        --clear-only)
            BUILD=false
            INSTALL=false
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --no-build    Install existing APK without rebuilding"
            echo "  --clear-only  Only clear app data, don't reinstall"
            echo "  --help, -h    Show this help message"
            exit 0
            ;;
        *)
            echo "Unknown option: $1"
            exit 1
            ;;
    esac
done

echo "=== E2E Device Setup ==="
echo ""

# Check if adb is available
if ! command -v adb &> /dev/null; then
    echo -e "${RED}Error: adb not found in PATH${NC}"
    exit 1
fi

# Build APK if requested
if [ "$BUILD" = true ]; then
    echo "Building debug APK..."
    cd "$PROJECT_ROOT"
    ./gradlew assembleDebug --quiet
    echo -e "${GREEN}Build complete${NC}"
    echo ""
fi

# Check APK exists
if [ "$INSTALL" = true ] && [ ! -f "$APK_PATH" ]; then
    echo -e "${RED}Error: APK not found at $APK_PATH${NC}"
    echo "Run without --no-build to build first, or run: ./gradlew assembleDebug"
    exit 1
fi

# Function to setup a single device
setup_device() {
    local serial=$1
    local name=$2

    echo "Setting up $name ($serial)..."

    # Check device is connected
    if ! adb -s "$serial" get-state &>/dev/null; then
        echo -e "${RED}  Error: Device not connected${NC}"
        return 1
    fi

    # Clear app data (if app is installed)
    if [ "$CLEAR_DATA" = true ]; then
        echo "  Clearing app data..."
        if adb -s "$serial" shell pm list packages 2>/dev/null | grep -q "$PACKAGE_NAME"; then
            adb -s "$serial" shell pm clear "$PACKAGE_NAME" &>/dev/null || true
            echo -e "  ${GREEN}App data cleared${NC}"
        else
            echo -e "  ${YELLOW}App not installed, skipping clear${NC}"
        fi
    fi

    # Install APK
    if [ "$INSTALL" = true ]; then
        echo "  Installing APK..."
        if adb -s "$serial" install -r "$APK_PATH" &>/dev/null; then
            echo -e "  ${GREEN}APK installed successfully${NC}"
        else
            echo -e "${RED}  Error: APK installation failed${NC}"
            return 1
        fi
    fi

    # Grant permissions (avoid permission dialogs during tests)
    echo "  Granting permissions..."
    local permissions=(
        "android.permission.CAMERA"
        "android.permission.READ_EXTERNAL_STORAGE"
        "android.permission.WRITE_EXTERNAL_STORAGE"
        "android.permission.POST_NOTIFICATIONS"
    )

    for perm in "${permissions[@]}"; do
        adb -s "$serial" shell pm grant "$PACKAGE_NAME" "$perm" 2>/dev/null || true
    done
    echo -e "  ${GREEN}Permissions granted${NC}"

    # Force stop app (ensure clean state)
    adb -s "$serial" shell am force-stop "$PACKAGE_NAME" 2>/dev/null || true

    echo -e "${GREEN}  Setup complete${NC}"
    return 0
}

# Setup both devices
FAILED=0

echo "Device 1:"
if ! setup_device "$DEVICE_1_SERIAL" "$DEVICE_1_NAME"; then
    FAILED=$((FAILED + 1))
fi
echo ""

echo "Device 2:"
if ! setup_device "$DEVICE_2_SERIAL" "$DEVICE_2_NAME"; then
    FAILED=$((FAILED + 1))
fi
echo ""

# Summary
echo "=== Summary ==="
if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All devices ready for E2E testing${NC}"
    echo ""
    echo "To run E2E tests:"
    echo "  ./gradlew connectedDebugAndroidTest"
    echo ""
    echo "To launch app manually:"
    echo "  adb -s $DEVICE_1_SERIAL shell am start -n $PACKAGE_NAME/.BailiwickActivity"
    echo "  adb -s $DEVICE_2_SERIAL shell am start -n $PACKAGE_NAME/.BailiwickActivity"
    exit 0
else
    echo -e "${RED}$FAILED device(s) failed setup${NC}"
    exit 1
fi
