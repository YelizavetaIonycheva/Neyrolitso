LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := libsqlite3
LOCAL_SRC_FILES :=
LOCAL_EXPORT_LDLIBS := -lsqlite3
include $(PREBUILT_SHARED_LIBRARY)
