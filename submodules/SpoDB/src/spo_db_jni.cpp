
#include <bctoolbox/list.h>
#include "spo_db_jni.h"


static const char* GetStringUTFChars(JNIEnv* env, jstring string) {
	const char * cstring = string ? env->GetStringUTFChars(string, NULL) : NULL;
	return cstring;
}

static void ReleaseStringUTFChars(JNIEnv* env, jstring string, const char *cstring) {
	if (string) env->ReleaseStringUTFChars(string, cstring);
}

JNIEXPORT void Java_org_pniei_portal_database_DBUtils_setDataBasePath(JNIEnv *env, jobject thiz, jstring jpath, jbyteArray jkey) {
	const char* path = GetStringUTFChars(env, jpath);
	unsigned char * key  = (unsigned char *)env->GetByteArrayElements(jkey, NULL);
	int len_key = env->GetArrayLength(jkey);
	spo_set_database_path(path, key, len_key);
	ReleaseStringUTFChars(env, jpath, path);
	env->ReleaseByteArrayElements(jkey, reinterpret_cast<jbyte *>(key), JNI_ABORT);
}

JNIEXPORT void Java_org_pniei_portal_database_DBUtils_closeDB(JNIEnv * env, jobject) {
    spo_storage_close();
}

JNIEXPORT void Java_org_pniei_portal_database_DBUtils_saveDB(JNIEnv * env, jobject) {
    spo_storage_save();
}



/*** SpoContact ***/

static SpoContact createSpoContact(JNIEnv *env, jobject jcontact) {
	SpoContact contact;

	jclass spoClass = env->FindClass("org/pniei/portal/database/SpoContact");

	jfieldID id 		= env->GetFieldID(spoClass, "mId", "J");
	jfieldID id_user 	= env->GetFieldID(spoClass, "mIdUser", "Ljava/lang/String;");
	jfieldID full_name 	= env->GetFieldID(spoClass, "mFullName", "Ljava/lang/String;");
	jfieldID sip_number = env->GetFieldID(spoClass, "mSipNumber", "Ljava/lang/String;");
    jfieldID url_photo  = env->GetFieldID(spoClass, "mUriPhoto", "Ljava/lang/String;");

	contact.id = env->GetLongField(jcontact, id);
	contact.id_user = GetStringUTFChars(env, (jstring)env->GetObjectField(jcontact, id_user));
	contact.full_name = GetStringUTFChars(env, (jstring)env->GetObjectField(jcontact, full_name));
	contact.sip_number = GetStringUTFChars(env, (jstring)env->GetObjectField(jcontact, sip_number));
    contact.uri_photo = GetStringUTFChars(env, (jstring)env->GetObjectField(jcontact, url_photo));
	return contact;
}

static jobject createJavaSpoContact(JNIEnv *env, SpoContact * contact) {
	jmethodID constructor;
	jvalue args[5];
	jobject object;

	if (contact == NULL)
		return NULL;

	jclass cls = env->FindClass("org/pniei/portal/database/SpoContact");
	constructor = env->GetMethodID(cls, "<init>", "(JLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

	args[0].j = contact->id;
	args[1].l = env->NewStringUTF(contact->id_user);
	args[2].l = env->NewStringUTF(contact->full_name);
	args[3].l = env->NewStringUTF(contact->sip_number);
    args[4].l = env->NewStringUTF(contact->uri_photo);
	object = env->NewObjectA(cls, constructor, args);
	return object;
}


JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getContactList(JNIEnv *env, jobject thiz) {
	bctbx_list_t * contacts = spo_get_contacts();

    size_t contactsCount = bctbx_list_size(contacts);
    jclass cls = env->FindClass("org/pniei/portal/database/SpoContact");
    jobjectArray jContacts = env->NewObjectArray(contactsCount, cls, NULL);

    for (size_t i = 0; i < contactsCount; i++) {
        env->SetObjectArrayElement(jContacts, i, createJavaSpoContact(env, (SpoContact *) (contacts->data)));
        contacts = contacts->next;
    }

	bctbx_list_free_with_data(contacts, free);

	return jContacts;
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContact(JNIEnv *env, jobject, jlong id) {
	SpoContact * contact = spo_get_contact_by_id(id);
	return createJavaSpoContact(env, contact);
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContactForNumber(JNIEnv *env, jobject, jstring jnumber) {
	const char* number = GetStringUTFChars(env, jnumber);
	SpoContact * contact = spo_get_contact_by_number(number);
	ReleaseStringUTFChars(env, jnumber, number);
	return createJavaSpoContact(env, contact);
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getContactForIdUser(JNIEnv *env, jobject, jstring jidUser) {
	const char* idUser = GetStringUTFChars(env, jidUser);
	SpoContact * contact = spo_get_contact_by_id_user(idUser);
	ReleaseStringUTFChars(env, jidUser, idUser);
	return createJavaSpoContact(env, contact);
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSearchContact(JNIEnv *env, jobject, jstring js) {
	const char* s = GetStringUTFChars(env, js);
	bctbx_list_t * contacts = spo_get_contact_search(s);
	ReleaseStringUTFChars(env, js, s);

	size_t contactsCount = bctbx_list_size(contacts);
	jclass cls = env->FindClass("org/pniei/portal/database/SpoContact");
	jobjectArray jContacts = env->NewObjectArray(contactsCount, cls, NULL);

	for (size_t i = 0; i < contactsCount; i++) {
		env->SetObjectArrayElement(jContacts, i, createJavaSpoContact(env, (SpoContact *) (contacts->data)));
		contacts = contacts->next;
	}

	bctbx_list_free_with_data(contacts, free);

	return jContacts;
}

JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveContact(JNIEnv *env, jobject, jobject c) {
	SpoContact contact = createSpoContact(env, c);
	return spo_save_contact(&contact);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateContact(JNIEnv * env, jobject,  jobject c) {
	SpoContact contact = createSpoContact(env, c);
	spo_update_contact(&contact);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteContact(JNIEnv * env, jobject,  jobject c) {
	SpoContact contact = createSpoContact(env, c);
	spo_delete_contact(&contact);
}



/*** SpoChatRoom ***/

static SpoChatRoom createSpoChatRoom(JNIEnv *env, jobject jchat_room) {
	SpoChatRoom chat_room;

	jclass spoClass = env->FindClass("org/pniei/portal/database/SpoChatRoom");

	jfieldID id 				= env->GetFieldID(spoClass, "mId", "J");
	jfieldID chat_type 			= env->GetFieldID(spoClass, "mType", "I");
	jfieldID id_user 			= env->GetFieldID(spoClass, "mIdUsersStr", "Ljava/lang/String;");
	jfieldID name_chat 			= env->GetFieldID(spoClass, "mNameChat", "Ljava/lang/String;");
	jfieldID time_last_message 	= env->GetFieldID(spoClass, "mTimeLastMessage", "J");

	chat_room.id = env->GetLongField(jchat_room, id);
	chat_room.chat_type = env->GetIntField(jchat_room, chat_type);
	chat_room.name_chat = GetStringUTFChars(env, (jstring)env->GetObjectField(jchat_room, name_chat));
	chat_room.time_last_message = env->GetLongField(jchat_room, time_last_message);
	chat_room.id_user = GetStringUTFChars(env, (jstring)env->GetObjectField(jchat_room, id_user));
	return chat_room;
}

static jobject createJavaSpoChatRoom(JNIEnv *env, SpoChatRoom * chat_room) {
	jmethodID constructor;
	jvalue args[5];
	jobject object;

	if (chat_room == NULL)
		return NULL;

	jclass cls = env->FindClass("org/pniei/portal/database/SpoChatRoom");
	constructor = env->GetMethodID(cls, "<init>", "(JILjava/lang/String;Ljava/lang/String;J)V");

	args[0].j = chat_room->id;
	args[1].i = chat_room->chat_type;
	args[2].l = env->NewStringUTF(chat_room->id_user);
	args[3].l = env->NewStringUTF(chat_room->name_chat);
	args[4].j = chat_room->time_last_message;
	object = env->NewObjectA(cls, constructor, args);
	return object;
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getChatList(JNIEnv *env, jobject thiz) {
	bctbx_list_t * rooms = spo_get_chat_rooms();

	size_t roomsCount = bctbx_list_size(rooms);
	jclass cls = env->FindClass("org/pniei/portal/database/SpoChatRoom");
	jobjectArray jRooms = env->NewObjectArray(roomsCount, cls, NULL);

	for (size_t i = 0; i < roomsCount; i++) {
		env->SetObjectArrayElement(jRooms, i, createJavaSpoChatRoom(env, (SpoChatRoom *) (rooms->data)));
		rooms = rooms->next;
	}

	bctbx_list_free_with_data(rooms, free);

	return jRooms;
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatRoom(JNIEnv *env, jobject, jlong id) {
	SpoChatRoom * room = spo_get_chat_room_by_id(id);
	return createJavaSpoChatRoom(env, room);
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatRoomForIdUser(JNIEnv *env, jobject, jstring jidUser) {
	const char* idUser = GetStringUTFChars(env, jidUser);
	SpoChatRoom * room = spo_get_chat_room_by_id_user(idUser);
	ReleaseStringUTFChars(env, jidUser, idUser);
	return createJavaSpoChatRoom(env, room);
}

JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveChatRoom(JNIEnv *env, jobject, jobject c) {
	SpoChatRoom room = createSpoChatRoom(env, c);
	return spo_save_chat_room(&room);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateChatRoom(JNIEnv * env, jobject,  jobject c) {
    SpoChatRoom room = createSpoChatRoom(env, c);
	spo_update_chat_room(&room);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deletechatroom(JNIEnv * env, jobject,  jobject c) {
    SpoChatRoom room = createSpoChatRoom(env, c);
	spo_delete_chat_room(&room);
}



/*** SpoChatMessage ***/

static SpoChatMessage createSpoChatMessage(JNIEnv *env, jobject jchat_message) {
    SpoChatMessage chat_message;

    jclass spoClass = env->FindClass("org/pniei/portal/database/SpoChatMessage");

    jfieldID id 			= env->GetFieldID(spoClass, "mId", "J");
    jfieldID dir 			= env->GetFieldID(spoClass, "mDir", "I");
    jfieldID is_read 		= env->GetFieldID(spoClass, "mIsRead", "I");
    jfieldID type_content 	= env->GetFieldID(spoClass, "mTypeContent", "I");
    jfieldID status 	    = env->GetFieldID(spoClass, "mStatus", "I");
    jfieldID id_user 	    = env->GetFieldID(spoClass, "mIdUsersStr", "Ljava/lang/String;");
    jfieldID message 		= env->GetFieldID(spoClass, "mMessage", "Ljava/lang/String;");
    jfieldID id_chat_room 	= env->GetFieldID(spoClass, "mIdSpoChatRoom", "J");
    jfieldID date 		    = env->GetFieldID(spoClass, "mDate", "J");

    chat_message.id = env->GetLongField(jchat_message, id);
    chat_message.dir = env->GetIntField(jchat_message, dir);
    chat_message.is_read = env->GetIntField(jchat_message, is_read);
    chat_message.type_content = env->GetIntField(jchat_message, type_content);
    chat_message.status = env->GetIntField(jchat_message, status);
    chat_message.id_user = GetStringUTFChars(env, (jstring)env->GetObjectField(jchat_message, id_user));
    chat_message.message = GetStringUTFChars(env, (jstring)env->GetObjectField(jchat_message, message));
    chat_message.id_chat_room = env->GetLongField(jchat_message, id_chat_room);
    chat_message.date = env->GetLongField(jchat_message, date);

    return chat_message;
}

static jobject createJavaSpoChatMessage(JNIEnv *env, SpoChatMessage * chat_message) {
    jmethodID constructor;
    jvalue args[9];
    jobject object;

    if (chat_message == NULL)
        return NULL;

    jclass cls = env->FindClass("org/pniei/portal/database/SpoChatMessage");
    constructor = env->GetMethodID(cls, "<init>", "(JIIIILjava/lang/String;Ljava/lang/String;JJ)V");

    args[0].j = chat_message->id;
    args[1].i = chat_message->dir;
    args[2].i = chat_message->is_read;
    args[3].i = chat_message->type_content;
    args[4].i = chat_message->status;
    args[5].l = env->NewStringUTF(chat_message->id_user);
    args[6].l = env->NewStringUTF(chat_message->message);
    args[7].j = chat_message->id_chat_room;
    args[8].j = chat_message->date;
    object = env->NewObjectA(cls, constructor, args);
    return object;
}


JNIEXPORT jint JNICALL Java_org_pniei_portal_database_DBUtils_getUnreadMessagesCount (JNIEnv * env, jobject,  jlong idChatRoom) {
    return spo_get_unread_messages_count(idChatRoom);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_setReadStatusMessages (JNIEnv * env, jobject,  jlong idChatRoom) {
    spo_set_read_status_messages(idChatRoom);
}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getChatMessageById (JNIEnv * env, jobject,  jlong idMessage) {
    SpoChatMessage * mes = spo_get_chat_message_by_id(idMessage);
    return createJavaSpoChatMessage(env, mes);
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSpoChatMessageRange (JNIEnv * env, jobject,  jlong idChatRoom, jint startm, jint endm, jboolean isDescent) {
    bctbx_list_t * messages = spo_get_spo_chat_messages_range(idChatRoom, startm, endm, isDescent);

    size_t messagesCount = bctbx_list_size(messages);
    jclass cls = env->FindClass("org/pniei/portal/database/SpoChatMessage");
    jobjectArray jMessages = env->NewObjectArray(messagesCount, cls, NULL);

    for (size_t i = 0; i < messagesCount; i++) {
        env->SetObjectArrayElement(jMessages, i, createJavaSpoChatMessage(env, (SpoChatMessage*) (messages->data)));
        messages = messages->next;
    }

	bctbx_list_free_with_data(messages, free);

    return jMessages;
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getWaitingSpoChatMessages (JNIEnv * env, jobject) {
    bctbx_list_t * messages = spo_get_waiting_spo_chat_messages();

    size_t messagesCount = bctbx_list_size(messages);
    jclass cls = env->FindClass("org/pniei/portal/database/SpoChatMessage");
    jobjectArray jMessages = env->NewObjectArray(messagesCount, cls, NULL);

    for (size_t i = 0; i < messagesCount; i++) {
        env->SetObjectArrayElement(jMessages, i, createJavaSpoChatMessage(env, (SpoChatMessage *) (messages->data)));
        messages = messages->next;
    }

	bctbx_list_free_with_data(messages, free);

    return jMessages;
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getWaitingSpoChatMessagesForCharRoom (JNIEnv * env, jobject, jlong idChatRoom) {
    bctbx_list_t * messages = spo_get_waiting_spo_chat_messages_by_id_chat_room(idChatRoom);

    size_t messagesCount = bctbx_list_size(messages);
    jclass cls = env->FindClass("org/pniei/portal/database/SpoChatMessage");
    jobjectArray jMessages = env->NewObjectArray(messagesCount, cls, NULL);

    for (size_t i = 0; i < messagesCount; i++) {
        env->SetObjectArrayElement(jMessages, i, createJavaSpoChatMessage(env, (SpoChatMessage *) (messages->data)));
        messages = messages->next;
    }

	bctbx_list_free_with_data(messages, free);

    return jMessages;
}

JNIEXPORT jint JNICALL Java_org_pniei_portal_database_DBUtils_getNumMessage (JNIEnv * env, jobject, jlong idChatRoom) {
    return spo_get_num_message(idChatRoom);
}

JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveMessage (JNIEnv * env, jobject, jobject m) {
    SpoChatMessage message = createSpoChatMessage(env, m);
    return spo_save_chat_message(&message);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateMessage (JNIEnv * env, jobject, jobject m) {
	SpoChatMessage message = createSpoChatMessage(env, m);
	spo_update_chat_message(&message);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteMessage (JNIEnv * env, jobject, jobject m) {
	SpoChatMessage message = createSpoChatMessage(env, m);
	spo_delete_chat_message(&message);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteAllMessages (JNIEnv * env, jobject, jlong idChatRoom) {
	spo_delete_all_chat_messages(idChatRoom);
}


/*** SpoFile ***/

static SpoFile createSpoFile(JNIEnv *env, jobject jfile) {
    SpoFile file;

    jclass spoClass = env->FindClass("org/pniei/portal/database/SpoFile");

    jfieldID id 			= env->GetFieldID(spoClass, "mId", "J");
    jfieldID dir 			= env->GetFieldID(spoClass, "mDir", "I");
    jfieldID type 			= env->GetFieldID(spoClass, "mType", "I");
    jfieldID id_message 	= env->GetFieldID(spoClass, "mIdMessage", "J");
    jfieldID id_from_server = env->GetFieldID(spoClass, "mIdFile", "Ljava/lang/String;");
    jfieldID status 	    = env->GetFieldID(spoClass, "mStatus", "I");
    jfieldID uri 	        = env->GetFieldID(spoClass, "mUri", "Ljava/lang/String;");
    jfieldID url_download 	= env->GetFieldID(spoClass, "mUrlDownload", "Ljava/lang/String;");
    jfieldID full_name      = env->GetFieldID(spoClass, "mName", "Ljava/lang/String;");

    file.id         = env->GetLongField(jfile, id);
    file.dir        = env->GetIntField(jfile, dir);
    file.type       = env->GetIntField(jfile, type);
    file.id_message = env->GetLongField(jfile, id_message);
    file.id_from_server = GetStringUTFChars(env, (jstring)env->GetObjectField(jfile, id_from_server));
    file.status     = env->GetIntField(jfile, status);
    file.uri        = GetStringUTFChars(env, (jstring)env->GetObjectField(jfile, uri));
    file.url_download = GetStringUTFChars(env, (jstring)env->GetObjectField(jfile, url_download));
    file.full_name  = GetStringUTFChars(env, (jstring)env->GetObjectField(jfile, full_name));

    return file;
}

static jobject createJavaSpoFile(JNIEnv *env, SpoFile * file) {
    jmethodID constructor;
    jvalue args[9];
    jobject object;

    if (file == NULL)
        return NULL;

    jclass cls = env->FindClass("org/pniei/portal/database/SpoFile");
    constructor = env->GetMethodID(cls, "<init>", "(JIIJLjava/lang/String;ILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V");

    args[0].j = file->id;
    args[1].i = file->dir;
    args[2].i = file->type;
    args[3].j = file->id_message;
    args[4].l = env->NewStringUTF(file->id_from_server);
    args[5].i = file->status;
    args[6].l = env->NewStringUTF(file->uri);
    args[7].l = env->NewStringUTF(file->url_download);
    args[8].l = env->NewStringUTF(file->full_name);
    object = env->NewObjectA(cls, constructor, args);
    return object;
}

JNIEXPORT jobjectArray JNICALL Java_org_pniei_portal_database_DBUtils_getSpoFiles (JNIEnv * env, jobject,  jlong idMessage) {
    bctbx_list_t * messages = spo_get_files_for_id_message(idMessage);

    size_t messagesCount = bctbx_list_size(messages);
    jclass cls = env->FindClass("org/pniei/portal/database/SpoFile");
    jobjectArray jMessages = env->NewObjectArray(messagesCount, cls, NULL);

    for (size_t i = 0; i < messagesCount; i++) {
        env->SetObjectArrayElement(jMessages, i, createJavaSpoFile(env, (SpoFile*) (messages->data)));
        messages = messages->next;
    }

    bctbx_list_free_with_data(messages, free);

    return jMessages;

}

JNIEXPORT jobject JNICALL Java_org_pniei_portal_database_DBUtils_getSpoFile (JNIEnv * env, jobject,  jlong id) {
    SpoFile * file = spo_get_file(id);
    return createJavaSpoFile(env, file);
}

JNIEXPORT jlong JNICALL Java_org_pniei_portal_database_DBUtils_saveFile (JNIEnv * env, jobject, jobject f) {
    SpoFile file = createSpoFile(env, f);
    return spo_save_file(&file);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_updateFile (JNIEnv * env, jobject, jobject f) {
    SpoFile file = createSpoFile(env, f);
    spo_update_file(&file);
}

JNIEXPORT void JNICALL Java_org_pniei_portal_database_DBUtils_deleteFile (JNIEnv * env, jobject, jobject f) {
    SpoFile file = createSpoFile(env, f);
    spo_delete_file(&file);
}














