MY_LOCAL_PATH := $(call my-dir)
$(call import-add-path, $(MY_LOCAL_PATH))

MY_ARMV7 := false
MY_ARMV7_NEON := false
ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
    ifeq ("$(shell test -e $(MY_LOCAL_PATH)/../build/.armv7 && echo armv7)","armv7")
        MY_ARMV7 := true
    endif
    ifeq ("$(shell test -e $(MY_LOCAL_PATH)/../build/.armv7neon && echo armv7neon)","armv7neon")
        MY_ARMV7_NEON := true
    endif
endif

ifeq ("$(shell test -e $(MY_LOCAL_PATH)/../build/.lts && echo lts)","lts")
    MY_LTS_POSTFIX := -lts
else
    MY_LTS_POSTFIX :=
endif

ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
    ifeq ($(MY_ARMV7_NEON), true)
        MY_BUILD_DIR := android-$(TARGET_ARCH)-neon$(MY_LTS_POSTFIX)
    else
        MY_BUILD_DIR := android-$(TARGET_ARCH)$(MY_LTS_POSTFIX)
    endif
else
    MY_BUILD_DIR := android-$(TARGET_ARCH)$(MY_LTS_POSTFIX)
endif

FFMPEG_INCLUDES := $(MY_LOCAL_PATH)/../../prebuilt/$(MY_BUILD_DIR)/ffmpeg/include

MY_ARM_MODE := arm
MY_ARM_NEON := false
LOCAL_PATH := $(MY_LOCAL_PATH)/../ffmpeg-kit-android-lib/src/main/cpp

# DEFINE ARCH FLAGS
ifeq ($(TARGET_ARCH_ABI), armeabi-v7a)
    MY_ARCH_FLAGS := ARM_V7A
    ifeq ("$(shell test -e $(MY_LOCAL_PATH)/../build/.lts && echo lts)","lts")
        MY_ARM_NEON := false
    else
        MY_ARM_NEON := true
    endif
endif
ifeq ($(TARGET_ARCH_ABI), arm64-v8a)
    MY_ARCH_FLAGS := ARM64_V8A
    MY_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI), x86)
    MY_ARCH_FLAGS := X86
    MY_ARM_NEON := true
endif
ifeq ($(TARGET_ARCH_ABI), x86_64)
    MY_ARCH_FLAGS := X86_64
    MY_ARM_NEON := true
endif

include $(CLEAR_VARS)
LOCAL_ARM_MODE := $(MY_ARM_MODE)
LOCAL_MODULE := ffmpegkit_abidetect
LOCAL_SRC_FILES := ffmpegkit_abidetect.c
LOCAL_CFLAGS := -Wall -Wextra -Werror -Wno-unused-parameter -DFFMPEG_KIT_${MY_ARCH_FLAGS}
LOCAL_C_INCLUDES := $(FFMPEG_INCLUDES)
LOCAL_LDLIBS := -llog -lz -landroid
LOCAL_STATIC_LIBRARIES := cpu-features
LOCAL_ARM_NEON := ${MY_ARM_NEON}
include $(BUILD_SHARED_LIBRARY)

$(call import-module, cpu-features)

MY_SRC_FILES := ffmpegkit.c ffprobekit.c ffmpegkit_exception.c fftools_cmdutils.c fftools_ffmpeg.c fftools_ffprobe.c fftools_ffmpeg_mux.c fftools_ffmpeg_mux_init.c fftools_ffmpeg_demux.c fftools_ffmpeg_opt.c fftools_opt_common.c fftools_ffmpeg_hw.c fftools_ffmpeg_filter.c fftools_objpool.c fftools_sync_queue.c fftools_thread_queue.c

ifeq ($(TARGET_PLATFORM),android-16)
    MY_SRC_FILES += android_lts_support.c
else ifeq ($(TARGET_PLATFORM),android-17)
    MY_SRC_FILES += android_lts_support.c
endif

MY_CFLAGS := -Wall -Werror -Wno-unused-parameter -Wno-switch -Wno-sign-compare -Wno-error=single-bit-bitfield-constant-conversion
MY_LDLIBS := -llog -lz -landroid

MY_BUILD_GENERIC_FFMPEG_KIT := true

ifeq ($(MY_ARMV7_NEON), true)
    include $(CLEAR_VARS)
    LOCAL_PATH := $(MY_LOCAL_PATH)/../ffmpeg-kit-android-lib/src/main/cpp
    LOCAL_ARM_MODE := $(MY_ARM_MODE)
    LOCAL_MODULE := ffmpegkit_armv7a_neon
    LOCAL_SRC_FILES := $(MY_SRC_FILES)
    LOCAL_CFLAGS := $(MY_CFLAGS)
    LOCAL_LDLIBS := $(MY_LDLIBS)
    LOCAL_SHARED_LIBRARIES := libavcodec_neon libavfilter_neon libswscale_neon libavformat_neon libavutil_neon libswresample_neon libavdevice_neon
    # NOT adding c++_shared to LOCAL_SHARED_LIBRARIES here: NDK r26+'s
    # ndk-build validates module dependencies strictly and fails with
    # "depends on undefined modules: c++_shared" (c++_shared isn't a real
    # ndk-build module to depend on, it's linked in via APP_STL). Worked
    # around on older NDKs by listing it anyway; r26c doesn't need or
    # tolerate that. See github.com/arthenica/ffmpeg-kit/issues/1076.
    LOCAL_ARM_NEON := true
    include $(BUILD_SHARED_LIBRARY)

    $(call import-module, ffmpeg/neon)

    ifneq ($(MY_ARMV7), true)
        MY_BUILD_GENERIC_FFMPEG_KIT := false
    endif
endif

ifeq ($(MY_BUILD_GENERIC_FFMPEG_KIT), true)
    include $(CLEAR_VARS)
    LOCAL_PATH := $(MY_LOCAL_PATH)/../ffmpeg-kit-android-lib/src/main/cpp
    LOCAL_ARM_MODE := $(MY_ARM_MODE)
    LOCAL_MODULE := ffmpegkit
    LOCAL_SRC_FILES := $(MY_SRC_FILES)
    LOCAL_CFLAGS := $(MY_CFLAGS)
    LOCAL_LDLIBS := $(MY_LDLIBS)
    LOCAL_SHARED_LIBRARIES := libavfilter libavformat libavcodec libavutil libswresample libavdevice libswscale
    # See the matching comment in the armv7a_neon block above - same NDK
    # r26c module-validation issue, c++_shared isn't a real dependency to
    # list here.
    LOCAL_ARM_NEON := ${MY_ARM_NEON}
    include $(BUILD_SHARED_LIBRARY)

    $(call import-module, ffmpeg)
endif

# Whisper JNI bridge — only built when libwhisper.a is present in the prebuilt tree.
# Produced exclusively by the Full and Full GPL tiers (--enable-whisper).
ifeq ($(MY_LTS_POSTFIX),-lts)
    WHISPER_PREBUILT_LIB_DIR := $(MY_LOCAL_PATH)/../../prebuilt/android-$(TARGET_ARCH)-lts/whisper/lib
    WHISPER_PREBUILT_INC_DIR := $(MY_LOCAL_PATH)/../../prebuilt/android-$(TARGET_ARCH)-lts/whisper/include
else
    WHISPER_PREBUILT_LIB_DIR := $(MY_LOCAL_PATH)/../../prebuilt/android-$(TARGET_ARCH)/whisper/lib
    WHISPER_PREBUILT_INC_DIR := $(MY_LOCAL_PATH)/../../prebuilt/android-$(TARGET_ARCH)/whisper/include
endif

WHISPER_LIB_EXISTS := $(shell test -f $(WHISPER_PREBUILT_LIB_DIR)/libwhisper.a && echo yes)
ifeq ($(WHISPER_LIB_EXISTS),yes)
    # Collect all static libs from the whisper prebuilt dir (whisper + ggml sub-libs).
    # --start-group/--end-group resolves circular references between the archives.
    WHISPER_ALL_STATIC_LIBS := $(shell find $(WHISPER_PREBUILT_LIB_DIR) -name "lib*.a" 2>/dev/null)

    include $(CLEAR_VARS)
    LOCAL_PATH := $(MY_LOCAL_PATH)/../ffmpeg-kit-android-lib/src/main/cpp
    LOCAL_ARM_MODE := $(MY_ARM_MODE)
    LOCAL_MODULE := whisperkit
    LOCAL_SRC_FILES := whisperkitjni.c
    LOCAL_C_INCLUDES := $(WHISPER_PREBUILT_INC_DIR)
    LOCAL_CFLAGS := -Wall -Wno-unused-parameter
    LOCAL_LDLIBS := -llog -lm -landroid
    LOCAL_LDFLAGS := -Wl,--start-group $(WHISPER_ALL_STATIC_LIBS) -Wl,--end-group
    LOCAL_ARM_NEON := ${MY_ARM_NEON}
    include $(BUILD_SHARED_LIBRARY)
endif
