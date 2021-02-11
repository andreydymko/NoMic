LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE := soundVolumeIncreaser
LOCAL_SRC_FILES := soundVolumeIncreaser.cpp
include $(BUILD_SHARED_LIBRARY)