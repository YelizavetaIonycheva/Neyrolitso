
#ifndef _SPO_DB_JNI_H
#define _SPO_DB_JNI_H

#include <jni.h>
#include "spo_db.h"

extern "C" JNIEXPORT void
Java_org_pniei_portal_database_DBUtils_setDataBasePath(JNIEnv *, jobject, jstring, jbyteArray);

extern "C" JNIEXPORT void Java_org_pniei_portal_database_DBUtils_closeDB(JNIEnv *, jobject);

extern "C" JNIEXPORT void Java_org_pniei_portal_database_DBUtils_saveDB(JNIEnv *, jobject);

/*** SpoContact ***/
extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getContactList
  (JNIEnv *, jobject);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContact
  (JNIEnv *, jobject, jlong);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContactForNumber
  (JNIEnv *, jobject, jstring);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContactForIdUser
  (JNIEnv *, jobject, jstring);

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSearchContact
  (JNIEnv *, jobject, jstring);

extern "C" JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveContact
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateContact
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteContact
  (JNIEnv *, jobject, jobject);



/*** SpoChatRoom ***/
extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getChatList
  (JNIEnv *, jobject);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatRoom
  (JNIEnv *, jobject, jlong);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatRoomForIdUser
  (JNIEnv *, jobject, jstring);

extern "C" JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveChatRoom
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateChatRoom
  (JNIEnv *, jobject,  jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deletechatroom
  (JNIEnv *, jobject,  jobject);




/*** SpoChatMessage ***/

extern "C" JNIEXPORT jint JNICALL Java_org_pniei_portal_database_DBUtils_getUnreadMessagesCount
  (JNIEnv *, jobject,  jlong);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_setReadStatusMessages
  (JNIEnv *, jobject,  jlong);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatMessageById
  (JNIEnv *, jobject,  jlong);

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSpoChatMessageRange
  (JNIEnv *, jobject,  jlong, jint, jint, jboolean);

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getWaitingSpoChatMessages
  (JNIEnv *, jobject);

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getWaitingSpoChatMessagesForCharRoom
  (JNIEnv *, jobject, jlong);

extern "C" JNIEXPORT jint JNICALL Java_org_pniei_portal_database_DBUtils_getNumMessage
  (JNIEnv *, jobject, jlong);

extern "C" JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveMessage
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateMessage
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteMessage
  (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteAllMessages
  (JNIEnv *, jobject, jlong);



/*** SpoFile ***/

extern "C" JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSpoFiles
  (JNIEnv *, jobject,  jlong);

extern "C" JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getSpoFile
  (JNIEnv *, jobject,  jlong);

extern "C" JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveFile
        (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateFile
        (JNIEnv *, jobject, jobject);

extern "C" JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteFile
        (JNIEnv *, jobject, jobject);


#endif
