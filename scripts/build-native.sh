#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
if [[ -x "$ROOT/.toolchain/cargo/bin/cargo" ]]; then
  export CARGO_HOME="$ROOT/.toolchain/cargo" RUSTUP_HOME="$ROOT/.toolchain/rustup"
  export PATH="$CARGO_HOME/bin:$PATH"
fi
command -v cargo >/dev/null || { echo 'Install Rust with rustup first.' >&2; exit 1; }
TARGETS=(aarch64-linux-android x86_64-linux-android)
ABIS=(arm64-v8a x86_64)
HOST="$(rustc -vV | sed -n 's/^host: //p')"
SYSROOT="$(rustc --print sysroot)"
for i in 0 1; do
  target="${TARGETS[$i]}"
  abi="${ABIS[$i]}"
  if [[ -n "${ANDROID_NDK_HOME:-}" ]]; then
    case "$(uname -s)" in Darwin) prebuilt=darwin-x86_64;; Linux) prebuilt=linux-x86_64;; *) echo 'Use macOS or Linux to build.' >&2; exit 1;; esac
    linker="$ANDROID_NDK_HOME/toolchains/llvm/prebuilt/$prebuilt/bin/${target}26-clang"
    key="CARGO_TARGET_$(echo "$target" | tr '[:lower:]-' '[:upper:]_')_LINKER"
    export "$key=$linker"
    export CARGO_ENCODED_RUSTFLAGS=$'-Clink-arg=-Wl,-z,max-page-size=16384'
  elif [[ -d "$ROOT/.toolchain/android-link/$target" ]]; then
    # Local bootstrap path: official NDK link stubs and Rust's bundled LLVM linker.
    libs="$ROOT/.toolchain/android-link/$target"
    export CARGO_ENCODED_RUSTFLAGS="-Clinker-flavor=ld.lld"$'\x1f'"-Clinker=$SYSROOT/lib/rustlib/$HOST/bin/rust-lld"$'\x1f'"-Lnative=$libs"$'\x1f'"-Clink-arg=$libs/crtbegin_so.o"$'\x1f'"-Clink-arg=$libs/crtend_so.o"$'\x1f'"-Clink-arg=-z"$'\x1f'"-Clink-arg=max-page-size=16384"$'\x1f'"-Clink-arg=--no-undefined"
  else
    echo 'Set ANDROID_NDK_HOME to an installed Android NDK (27 or newer).' >&2; exit 1
  fi
  cargo build --locked -p ink-engine --release --target "$target"
  mkdir -p "android/app/src/main/jniLibs/$abi"
  cp "target/$target/release/libinkstone.so" "android/app/src/main/jniLibs/$abi/"
done
