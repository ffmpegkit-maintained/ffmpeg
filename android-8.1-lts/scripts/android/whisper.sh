#!/bin/bash

# ALWAYS CLEAN THE PREVIOUS BUILD
git clean -dfx 2>/dev/null 1>/dev/null

SYSTEM_PROCESSOR=""
case ${ARCH} in
arm-v7a | arm-v7a-neon)
  SYSTEM_PROCESSOR="armv7-a"
  ;;
arm64-v8a)
  SYSTEM_PROCESSOR="aarch64"
  ;;
x86)
  SYSTEM_PROCESSOR="i686"
  ;;
x86-64)
  SYSTEM_PROCESSOR="x86_64"
  ;;
esac

cmake -Wno-dev \
  -DCMAKE_VERBOSE_MAKEFILE=0 \
  -DCMAKE_C_FLAGS="${CFLAGS}" \
  -DCMAKE_CXX_FLAGS="${CXXFLAGS}" \
  -DCMAKE_EXE_LINKER_FLAGS="${LDFLAGS}" \
  -DCMAKE_SYSROOT="${ANDROID_SYSROOT}" \
  -DCMAKE_FIND_ROOT_PATH="${ANDROID_SYSROOT}" \
  -DCMAKE_BUILD_TYPE=Release \
  -DCMAKE_INSTALL_PREFIX="${LIB_INSTALL_PREFIX}" \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=${API} \
  -DCMAKE_ANDROID_NDK=${ANDROID_NDK_ROOT} \
  -DCMAKE_CXX_COMPILER="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${TOOLCHAIN}/bin/$CXX" \
  -DCMAKE_C_COMPILER="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${TOOLCHAIN}/bin/$CC" \
  -DCMAKE_LINKER="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${TOOLCHAIN}/bin/$LD" \
  -DCMAKE_AR="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${TOOLCHAIN}/bin/$AR" \
  -DCMAKE_AS="${ANDROID_NDK_ROOT}/toolchains/llvm/prebuilt/${TOOLCHAIN}/bin/$AS" \
  -DCMAKE_SYSTEM_LOADED=1 \
  -DCMAKE_SYSTEM_PROCESSOR="${SYSTEM_PROCESSOR}" \
  -DWHISPER_BUILD_TESTS=OFF \
  -DWHISPER_BUILD_EXAMPLES=OFF \
  -DWHISPER_BUILD_SERVER=OFF \
  -DWHISPER_STATIC=ON \
  -DBUILD_SHARED_LIBS=OFF \
  "${BASEDIR}"/src/"${LIB_NAME}" || return 1

make -j$(get_cpu_count) || return 1

make install || return 1

# CREATE PACKAGE CONFIG MANUALLY
create_whisper_package_config "1.7.5" || return 1
