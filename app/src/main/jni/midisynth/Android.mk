LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := midisynth
LOCAL_SRC_FILES := midisynth.c
LOCAL_LDLIBS := -lOpenSLES -llog
LOCAL_STATIC_LIBRARIES := libsonivox opensl_stream
include $(BUILD_SHARED_LIBRARY)
