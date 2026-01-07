#!/bin/bash
set -e

# =============================================================================
# Android SDK/NDK Installation Script
# =============================================================================
# This script installs Android SDK and NDK for command-line builds.
#
# Usage:
#   ./scripts/install-android-sdk.sh
#
# =============================================================================

ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Download and install command-line tools
# =============================================================================
install_cmdline_tools() {
    log_info "Installing Android command-line tools..."

    mkdir -p "$ANDROID_HOME/cmdline-tools"
    cd "$ANDROID_HOME"

    # Download latest command-line tools
    # Check https://developer.android.com/studio#command-tools for latest version
    local CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
    local CMDLINE_TOOLS_ZIP="cmdline-tools.zip"

    if [ ! -f "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" ]; then
        log_info "Downloading command-line tools..."
        curl -L -o "$CMDLINE_TOOLS_ZIP" "$CMDLINE_TOOLS_URL"

        log_info "Extracting..."
        unzip -q "$CMDLINE_TOOLS_ZIP"
        rm "$CMDLINE_TOOLS_ZIP"

        # Move to expected location
        mv cmdline-tools latest 2>/dev/null || true
        mkdir -p cmdline-tools
        mv latest cmdline-tools/ 2>/dev/null || true

        log_success "Command-line tools installed"
    else
        log_success "Command-line tools already installed"
    fi
}

# =============================================================================
# Install SDK components
# =============================================================================
install_sdk_components() {
    log_info "Installing SDK components..."

    local SDKMANAGER="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"

    if [ ! -f "$SDKMANAGER" ]; then
        log_error "sdkmanager not found at $SDKMANAGER"
        exit 1
    fi

    # Accept licenses
    log_info "Accepting licenses..."
    yes | "$SDKMANAGER" --licenses > /dev/null 2>&1 || true

    # Install required components
    log_info "Installing platform-tools..."
    "$SDKMANAGER" "platform-tools"

    log_info "Installing build-tools..."
    "$SDKMANAGER" "build-tools;34.0.0"

    log_info "Installing Android platform (API 34)..."
    "$SDKMANAGER" "platforms;android-34"

    log_info "Installing NDK..."
    "$SDKMANAGER" "ndk;25.2.9519653"

    log_info "Installing CMake..."
    "$SDKMANAGER" "cmake;3.22.1"

    log_success "SDK components installed"
}

# =============================================================================
# Setup environment variables
# =============================================================================
setup_environment() {
    log_info "Setting up environment variables..."

    local NDK_VERSION="25.2.9519653"
    local SHELL_RC=""

    # Detect shell config file
    if [ -n "$ZSH_VERSION" ] || [ -f "$HOME/.zshrc" ]; then
        SHELL_RC="$HOME/.zshrc"
    elif [ -n "$BASH_VERSION" ] || [ -f "$HOME/.bashrc" ]; then
        SHELL_RC="$HOME/.bashrc"
    fi

    local ENV_BLOCK="
# Android SDK (added by install-android-sdk.sh)
export ANDROID_HOME=\"$ANDROID_HOME\"
export ANDROID_SDK_ROOT=\"\$ANDROID_HOME\"
export NDK_HOME=\"\$ANDROID_HOME/ndk/$NDK_VERSION\"
export PATH=\"\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools:\$PATH\"
"

    if [ -n "$SHELL_RC" ]; then
        if grep -q "ANDROID_HOME" "$SHELL_RC" 2>/dev/null; then
            log_warn "ANDROID_HOME already in $SHELL_RC"
            log_info "You may want to update it manually if paths changed"
        else
            log_info "Adding environment variables to $SHELL_RC..."
            echo "$ENV_BLOCK" >> "$SHELL_RC"
            log_success "Environment variables added to $SHELL_RC"
        fi
    fi

    # Export for current session
    export ANDROID_HOME="$ANDROID_HOME"
    export ANDROID_SDK_ROOT="$ANDROID_HOME"
    export NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
    export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

    echo ""
    log_info "For current session, run:"
    echo "  export ANDROID_HOME=\"$ANDROID_HOME\""
    echo "  export NDK_HOME=\"\$ANDROID_HOME/ndk/$NDK_VERSION\""
    echo ""
    log_info "Or source your shell config:"
    echo "  source $SHELL_RC"
    echo ""
}

# =============================================================================
# Main
# =============================================================================
main() {
    echo ""
    echo "=============================================="
    echo "  Android SDK/NDK Installation"
    echo "=============================================="
    echo ""
    echo "Installation directory: $ANDROID_HOME"
    echo ""

    # Check for required tools
    if ! command -v curl &> /dev/null; then
        log_error "curl is required but not installed"
        exit 1
    fi

    if ! command -v unzip &> /dev/null; then
        log_error "unzip is required but not installed"
        exit 1
    fi

    install_cmdline_tools
    install_sdk_components
    setup_environment

    echo ""
    log_success "========================================"
    log_success "  Android SDK/NDK Installation Complete!"
    log_success "========================================"
    echo ""
    echo "Installed to: $ANDROID_HOME"
    echo ""
    echo "Next step: Run the iroh-ffi build script:"
    echo "  ./scripts/build-iroh-ffi.sh"
    echo ""
}

main "$@"
