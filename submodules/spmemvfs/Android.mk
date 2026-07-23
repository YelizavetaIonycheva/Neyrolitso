LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE:= libspmemvfs

LOCAL_SRC_FILES := spmemvfs.c

LOCAL_C_INCLUDES := \
$(LOCAL_PATH)/ \

LOCAL_LDLIBS := -lsqlite3

LOCAL_CFLAGS +=	-fstack-protector -frtti

include $(BUILD_SHARED_LIBRARY)
