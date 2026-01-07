#!/bin/bash
set -e

# =============================================================================
# Iroh-FFI Android Build Script
# =============================================================================
# This script builds iroh-ffi for Android and integrates it into Bailiwick.
#
# Prerequisites:
#   - Rust toolchain (rustup, cargo)
#   - Android SDK with NDK
#
# Usage:
#   ./scripts/build-iroh-ffi.sh
#
# =============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BAILIWICK_DIR="$(dirname "$SCRIPT_DIR")"
IROH_FFI_DIR="/home/lucas/workspace/iroh-ffi"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info() { echo -e "${BLUE}[INFO]${NC} $1"; }
log_success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# =============================================================================
# Step 0: Check Prerequisites
# =============================================================================
check_prerequisites() {
    log_info "Checking prerequisites..."

    local missing=()

    # Check Rust
    if ! command -v rustc &> /dev/null; then
        missing+=("rustc")
    else
        log_success "Rust: $(rustc --version)"
    fi

    if ! command -v cargo &> /dev/null; then
        missing+=("cargo")
    else
        log_success "Cargo: $(cargo --version)"
    fi

    if ! command -v rustup &> /dev/null; then
        missing+=("rustup")
    else
        log_success "Rustup: $(rustup --version 2>/dev/null | head -1)"
    fi

    # Check Android SDK
    if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
        # Try common locations
        if [ -d "$HOME/Android/Sdk" ]; then
            export ANDROID_HOME="$HOME/Android/Sdk"
        elif [ -d "/usr/local/android-sdk" ]; then
            export ANDROID_HOME="/usr/local/android-sdk"
        else
            missing+=("Android SDK (ANDROID_HOME not set)")
        fi
    fi

    ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"

    if [ -n "$ANDROID_HOME" ] && [ -d "$ANDROID_HOME" ]; then
        log_success "Android SDK: $ANDROID_HOME"
    fi

    # Check for NDK
    NDK_VERSION=""
    if [ -d "$ANDROID_HOME/ndk" ]; then
        NDK_VERSION=$(ls -1 "$ANDROID_HOME/ndk" 2>/dev/null | sort -V | tail -1)
        if [ -n "$NDK_VERSION" ]; then
            export NDK_HOME="$ANDROID_HOME/ndk/$NDK_VERSION"
            log_success "Android NDK: $NDK_HOME"
        fi
    fi

    if [ -z "$NDK_VERSION" ]; then
        missing+=("Android NDK")
    fi

    # Report missing items
    if [ ${#missing[@]} -gt 0 ]; then
        log_error "Missing prerequisites:"
        for item in "${missing[@]}"; do
            echo "  - $item"
        done
        exit 1
    fi

    log_success "All prerequisites satisfied!"
    echo ""
}

# =============================================================================
# Step 1: Clone iroh-ffi
# =============================================================================
clone_iroh_ffi() {
    log_info "Step 1: Setting up iroh-ffi repository..."

    if [ -d "$IROH_FFI_DIR" ]; then
        log_info "iroh-ffi already exists at $IROH_FFI_DIR"
        cd "$IROH_FFI_DIR"

        # Update to latest
        log_info "Fetching latest changes..."
        git fetch --tags

        # Check out latest stable release
        LATEST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
        if [ -n "$LATEST_TAG" ]; then
            log_info "Checking out $LATEST_TAG..."
            git checkout "$LATEST_TAG" 2>/dev/null || true
        fi
    else
        log_info "Cloning iroh-ffi to $IROH_FFI_DIR..."
        git clone https://github.com/n0-computer/iroh-ffi.git "$IROH_FFI_DIR"
        cd "$IROH_FFI_DIR"

        # Check out latest stable release
        LATEST_TAG=$(git tag -l 'v*' | sort -V | tail -1)
        if [ -n "$LATEST_TAG" ]; then
            log_info "Checking out $LATEST_TAG..."
            git checkout "$LATEST_TAG"
        fi
    fi

    log_success "iroh-ffi ready at $IROH_FFI_DIR"
    echo ""
}

# =============================================================================
# Step 2: Install Rust Android targets
# =============================================================================
install_rust_targets() {
    log_info "Step 2: Installing Rust Android targets..."

    local targets=(
        "aarch64-linux-android"
        "armv7-linux-androideabi"
        "x86_64-linux-android"
    )

    for target in "${targets[@]}"; do
        if rustup target list --installed | grep -q "$target"; then
            log_success "Target $target already installed"
        else
            log_info "Installing target $target..."
            rustup target add "$target"
        fi
    done

    log_success "All Rust Android targets installed"
    echo ""
}

# =============================================================================
# Step 3: Configure Cargo for Android NDK
# =============================================================================
configure_cargo() {
    log_info "Step 3: Configuring Cargo for Android NDK..."

    local CARGO_CONFIG="$HOME/.cargo/config.toml"
    local NDK_TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"

    if [ ! -d "$NDK_TOOLCHAIN" ]; then
        log_error "NDK toolchain not found at $NDK_TOOLCHAIN"
        exit 1
    fi

    # Check if Android targets already configured
    if grep -q "aarch64-linux-android" "$CARGO_CONFIG" 2>/dev/null; then
        log_info "Cargo config already has Android targets configured"
    else
        log_info "Adding Android target configuration to $CARGO_CONFIG..."

        # Backup existing config
        if [ -f "$CARGO_CONFIG" ]; then
            cp "$CARGO_CONFIG" "$CARGO_CONFIG.backup.$(date +%Y%m%d%H%M%S)"
        fi

        cat >> "$CARGO_CONFIG" << EOF

# Android NDK Configuration (added by build-iroh-ffi.sh)
[target.aarch64-linux-android]
ar = "$NDK_TOOLCHAIN/bin/llvm-ar"
linker = "$NDK_TOOLCHAIN/bin/aarch64-linux-android21-clang"

[target.armv7-linux-androideabi]
ar = "$NDK_TOOLCHAIN/bin/llvm-ar"
linker = "$NDK_TOOLCHAIN/bin/armv7a-linux-androideabi21-clang"

[target.x86_64-linux-android]
ar = "$NDK_TOOLCHAIN/bin/llvm-ar"
linker = "$NDK_TOOLCHAIN/bin/x86_64-linux-android21-clang"
EOF
    fi

    log_success "Cargo configured for Android NDK"
    echo ""
}

# =============================================================================
# Step 4: Build iroh-ffi for Android
# =============================================================================
build_iroh_ffi() {
    log_info "Step 4: Building iroh-ffi for Android targets..."

    cd "$IROH_FFI_DIR"

    local NDK_TOOLCHAIN="$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64"
    local API_LEVEL="21"

    # Build for each Android target
    # Format: rust_target -> android_abi, clang_prefix
    local targets=(
        "aarch64-linux-android:arm64-v8a:aarch64-linux-android"
        "armv7-linux-androideabi:armeabi-v7a:armv7a-linux-androideabi"
        "x86_64-linux-android:x86_64:x86_64-linux-android"
    )

    for target_info in "${targets[@]}"; do
        IFS=':' read -r rust_target android_abi clang_prefix <<< "$target_info"

        log_info "Building for $rust_target ($android_abi)..."

        # Set environment variables for cc-rs crate
        # These tell the C compiler where to find the NDK toolchain
        export CC="${NDK_TOOLCHAIN}/bin/${clang_prefix}${API_LEVEL}-clang"
        export CXX="${NDK_TOOLCHAIN}/bin/${clang_prefix}${API_LEVEL}-clang++"
        export AR="${NDK_TOOLCHAIN}/bin/llvm-ar"
        export RANLIB="${NDK_TOOLCHAIN}/bin/llvm-ranlib"

        # Also set target-specific env vars that cc-rs looks for
        local target_upper=$(echo "$rust_target" | tr '[:lower:]-' '[:upper:]_')
        export "CC_${target_upper}=${CC}"
        export "CXX_${target_upper}=${CXX}"
        export "AR_${target_upper}=${AR}"
        export "CARGO_TARGET_${target_upper}_LINKER=${CC}"

        log_info "  CC=$CC"

        if cargo build --release --target "$rust_target" --lib; then
            log_success "Built $rust_target"
        else
            log_error "Failed to build $rust_target"
            exit 1
        fi
    done

    # Clear the environment variables
    unset CC CXX AR RANLIB

    log_success "All Android targets built"
    echo ""
}

# =============================================================================
# Step 5: Generate Kotlin bindings
# =============================================================================
generate_bindings() {
    log_info "Step 5: Generating Kotlin/UniFFI bindings..."

    cd "$IROH_FFI_DIR"

    # First build for host to get uniffi-bindgen working
    log_info "Building for host (needed for uniffi-bindgen)..."
    cargo build --release --lib

    # Find the library
    local LIB_PATH=""
    if [ -f "target/release/libiroh_ffi.so" ]; then
        LIB_PATH="target/release/libiroh_ffi.so"
    elif [ -f "target/release/libiroh_ffi.dylib" ]; then
        LIB_PATH="target/release/libiroh_ffi.dylib"
    else
        log_error "Could not find built library"
        exit 1
    fi

    # Generate Kotlin bindings
    log_info "Generating Kotlin bindings with uniffi-bindgen..."
    mkdir -p kotlin-android-bindings

    cargo run --release --bin uniffi-bindgen generate \
        --library "$LIB_PATH" \
        --language kotlin \
        --out-dir kotlin-android-bindings/ \
        --config uniffi.toml

    log_success "Kotlin bindings generated"
    echo ""
}

# =============================================================================
# Step 6: Copy artifacts to Bailiwick
# =============================================================================
copy_to_bailiwick() {
    log_info "Step 6: Copying artifacts to Bailiwick..."

    cd "$IROH_FFI_DIR"

    # Create target directories
    mkdir -p "$BAILIWICK_DIR/app/src/main/jniLibs/arm64-v8a"
    mkdir -p "$BAILIWICK_DIR/app/src/main/jniLibs/armeabi-v7a"
    mkdir -p "$BAILIWICK_DIR/app/src/main/jniLibs/x86_64"

    # Copy native libraries
    local targets=(
        "aarch64-linux-android:arm64-v8a"
        "armv7-linux-androideabi:armeabi-v7a"
        "x86_64-linux-android:x86_64"
    )

    for target_info in "${targets[@]}"; do
        IFS=':' read -r rust_target android_abi <<< "$target_info"
        src="target/$rust_target/release/libiroh_ffi.so"
        dst="$BAILIWICK_DIR/app/src/main/jniLibs/$android_abi/libiroh_ffi.so"

        if [ -f "$src" ]; then
            cp "$src" "$dst"
            log_success "Copied $android_abi library"
        else
            log_warn "Library not found: $src"
        fi
    done

    # Copy Kotlin bindings
    if [ -d "kotlin-android-bindings/uniffi" ]; then
        log_info "Copying Kotlin bindings..."
        mkdir -p "$BAILIWICK_DIR/app/src/main/java/"
        cp -r kotlin-android-bindings/uniffi "$BAILIWICK_DIR/app/src/main/java/"
        log_success "Copied Kotlin bindings to app/src/main/java/uniffi/"
    else
        log_warn "Kotlin bindings not found at kotlin-android-bindings/uniffi"
    fi

    log_success "Artifacts copied to Bailiwick"
    echo ""
}

# =============================================================================
# Step 7: Update gradle and show next steps
# =============================================================================
show_next_steps() {
    log_info "Step 7: Configuration notes..."

    echo ""
    log_warn "You may need to add JNA dependency to app/build.gradle:"
    echo ""
    echo "    dependencies {"
    echo "        implementation 'net.java.dev.jna:jna:5.13.0@aar'"
    echo "        // ... other deps"
    echo "    }"
    echo ""

    log_warn "And remove old IPFS library if present:"
    echo ""
    echo "    // Remove this line:"
    echo "    // implementation files('libs/lite-debug.aar')"
    echo ""
}

# =============================================================================
# Main
# =============================================================================
main() {
    echo ""
    echo "=============================================="
    echo "  Iroh-FFI Android Build Script"
    echo "=============================================="
    echo ""

    check_prerequisites
    clone_iroh_ffi
    install_rust_targets
    configure_cargo
    build_iroh_ffi
    generate_bindings
    copy_to_bailiwick
    show_next_steps

    echo ""
    log_success "========================================"
    log_success "  Build Complete!"
    log_success "========================================"
    echo ""
    echo "Native libraries copied to:"
    echo "  app/src/main/jniLibs/arm64-v8a/libiroh_ffi.so"
    echo "  app/src/main/jniLibs/armeabi-v7a/libiroh_ffi.so"
    echo "  app/src/main/jniLibs/x86_64/libiroh_ffi.so"
    echo ""
    echo "Kotlin bindings copied to:"
    echo "  app/src/main/java/uniffi/"
    echo ""
    echo "Next steps:"
    echo "  1. Add JNA dependency to app/build.gradle"
    echo "  2. Update IrohWrapper.kt to use actual uniffi.iroh bindings"
    echo "  3. Run: ./gradlew assembleDebug"
    echo ""
}

main "$@"
