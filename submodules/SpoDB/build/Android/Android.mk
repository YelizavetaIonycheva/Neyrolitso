LOCAL_PATH:= $(call my-dir)/../../
include $(CLEAR_VARS)

LOCAL_MODULE:= libspodb

LOCAL_SRC_FILES := \
	src/chat_file_table.cpp \
	src/chat_message_table.cpp \
	src/chat_room_table.cpp \
	src/contact_table.cpp \
	src/spo_db.cpp \
	src/spo_db_jni.cpp \

LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include \
	$(LOCAL_PATH)/../spmemvfs \
	$(LOCAL_PATH)/../magma/include \
	$(LOCAL_PATH)/../bctoolbox/include \
	
LOCAL_LDLIBS := -lsqlite3

LOCAL_SHARED_LIBRARIES := libmagma libspmemvfs libbctoolbox

LOCAL_CFLAGS +=	-frtti -Wno-pointer-sign

include $(BUILD_SHARED_LIBRARY)
