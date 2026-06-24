ifeq ($(MY_LTS_POSTFIX),-lts)
    WHISPER_LIB_PATH := $(call my-dir)/../../../prebuilt/android-$(TARGET_ARCH)-lts/whisper/lib
else
    WHISPER_LIB_PATH := $(call my-dir)/../../../prebuilt/android-$(TARGET_ARCH)/whisper/lib
endif

include $(CLEAR_VARS)
LOCAL_MODULE := libwhisper_prebuilt
LOCAL_SRC_FILES := $(WHISPER_LIB_PATH)/libwhisper.a
include $(PREBUILT_STATIC_LIBRARY)
