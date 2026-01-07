#!/bin/bash
# Bailiwick Android Emulator Setup Script
# Run with: sudo ./scripts/setup-emulator.sh

set -e

echo "=== Bailiwick Emulator Setup ==="
echo

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo "ERROR: Please run as root (sudo ./scripts/setup-emulator.sh)"
    exit 1
fi

# Get the actual user (not root)
ACTUAL_USER=${SUDO_USER:-$USER}
echo "Setting up for user: $ACTUAL_USER"

# Step 1: Load KVM modules
echo
echo "=== Step 1: Loading KVM modules ==="
modprobe kvm
modprobe kvm-amd  # For AMD processors (Ryzen 7 3700X)
echo "KVM modules loaded successfully"

# Step 2: Check KVM is working
echo
echo "=== Step 2: Verifying KVM ==="
if [ -e /dev/kvm ]; then
    echo "/dev/kvm exists"
else
    echo "ERROR: /dev/kvm not found after loading modules"
    exit 1
fi

# Step 3: Set permissions on /dev/kvm
echo
echo "=== Step 3: Setting permissions ==="
chmod 666 /dev/kvm
echo "/dev/kvm permissions set"

# Step 4: Add user to kvm group (for persistent access)
echo
echo "=== Step 4: Adding user to kvm group ==="
if getent group kvm > /dev/null; then
    usermod -aG kvm "$ACTUAL_USER"
    echo "User $ACTUAL_USER added to kvm group"
else
    echo "kvm group doesn't exist, skipping (permissions already set)"
fi

# Step 5: Make KVM modules load on boot
echo
echo "=== Step 5: Configuring modules to load on boot ==="
if [ ! -f /etc/modules-load.d/kvm.conf ]; then
    cat > /etc/modules-load.d/kvm.conf << EOF
# KVM modules for Android emulator
kvm
kvm-amd
EOF
    echo "Created /etc/modules-load.d/kvm.conf"
else
    echo "/etc/modules-load.d/kvm.conf already exists"
fi

echo
echo "=== Setup Complete ==="
echo
echo "KVM is now configured. You can start the emulator with:"
echo
echo "  export ANDROID_AVD_HOME=/home/$ACTUAL_USER/.config/.android/avd"
echo "  /home/$ACTUAL_USER/Android/Sdk/emulator/emulator -avd Bailiwick_Test"
echo
echo "Or run the instrumentation tests with:"
echo
echo "  ./gradlew connectedDebugAndroidTest"
echo
echo "NOTE: You may need to log out and back in for group changes to take effect."
