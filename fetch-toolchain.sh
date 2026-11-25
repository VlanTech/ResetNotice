#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p android-toolchain
curl -fL --retry 2 https://dl.google.com/android/repository/platform-35_r02.zip -o android-toolchain/platform.zip
curl -fL --retry 2 https://dl.google.com/android/repository/build-tools_r35_linux.zip -o android-toolchain/build-tools.zip
curl -fL --retry 2 https://repo.maven.apache.org/maven2/org/eclipse/jdt/ecj/3.39.0/ecj-3.39.0.jar -o android-toolchain/ecj.jar
printf '%s\n' '0bb560a90a7a2cbd0dd8348224d518b638fe7949  android-toolchain/platform.zip' '2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc  android-toolchain/build-tools.zip' | sha1sum -c
printf '%s\n' '01f5a92ac19bb2b3bf85e295a68f2c73c264369109158b566ce9b490af982948  android-toolchain/ecj.jar' | sha256sum -c
unzip -qo android-toolchain/platform.zip -d android-toolchain/platform
unzip -qo android-toolchain/build-tools.zip -d android-toolchain/build-tools
