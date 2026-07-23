
#ifndef _SPODB_H
#define _SPODB_H

#include "chat_message_table.h"
#include "chat_file_table.h"

#ifdef __cplusplus
extern "C" {
#endif

#include "spo_db_context.h"
#include "contact_table.h"
#include "chat_room_table.h"

extern DB_Context * dbContext;

void spo_set_database_path(const char *path, unsigned char * key, int len_key);
void spo_storage_close();
void spo_storage_save();

void spo_set_database_path_portal(const char *path, unsigned char * key, int len_key);
void spo_storage_close_portal();
void spo_storage_save_portal();





/*** SpoContact ***/
void spo_create_contact_table(sqlite3* db);
void spo_create_contact_table_portal(sqlite3* db);
bctbx_list_t * spo_get_contacts();
SpoContact * spo_get_contact_by_id(const unsigned long long call_id);
SpoContact * spo_get_contact_by_number(const char * number);
SpoContact * spo_get_contact_by_id_user(const char * id_user);
bctbx_list_t * spo_get_contact_search(const char * s);
unsigned long long spo_save_contact(SpoContact *contact);
void spo_update_contact(SpoContact * contact);
void spo_delete_contact(SpoContact * contact);

/*** SpoChatRoom ***/
void spo_create_chat_room_table(sqlite3* db);
bctbx_list_t * spo_get_chat_rooms();
SpoChatRoom * spo_get_chat_room_by_id(const unsigned long long room_id);
SpoChatRoom * spo_get_chat_room_by_id_user(const char * id_user);
unsigned long long spo_save_chat_room(SpoChatRoom * room);
void spo_update_chat_room(SpoChatRoom * room);
void spo_delete_chat_room(SpoChatRoom * room);

/*** SpoChatMessage ***/
void spo_create_chat_message_table(sqlite3* db);
unsigned int spo_get_unread_messages_count(unsigned long long id_chat_room);
void spo_set_read_status_messages(unsigned long long id_chat_room);
SpoChatMessage * spo_get_chat_message_by_id(unsigned long long id_message);
bctbx_list_t * spo_get_spo_chat_messages_range(unsigned long long id_chat_room, int startm, int endm, unsigned char is_descent);
bctbx_list_t * spo_get_waiting_spo_chat_messages();
bctbx_list_t * spo_get_waiting_spo_chat_messages_by_id_chat_room(unsigned long long id_chat_room);
int spo_get_num_message(unsigned long long id_chat_room);
unsigned long long spo_save_chat_message(SpoChatMessage * m);
void spo_update_chat_message(SpoChatMessage * m);
void spo_delete_chat_message(SpoChatMessage * m);
void spo_delete_all_chat_messages(unsigned long long id_chat_room);

/*** SpoFile***/
void spo_create_file_table(sqlite3* db);
bctbx_list_t * spo_get_files_for_id_message(unsigned long long id_message);
SpoFile * spo_get_file(unsigned long long id_file);
unsigned long long spo_save_file(SpoFile * f);
void spo_update_file(SpoFile * f);
void spo_delete_file(SpoFile * f);


#ifdef __cplusplus
}
#endif

#endif