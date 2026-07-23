#ifndef _CHAT_ROOM_TABLE_H
#define _CHAT_ROOM_TABLE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "spo_db_context.h"
#include <bctoolbox/list.h>
#include <sqlite3.h>

extern DB_Context * dbContext;

typedef struct _SpoChatRoom {
	unsigned long long id;
	unsigned int chat_type;
	const char * id_user;
	const char * name_chat;
	unsigned long long time_last_message;
} SpoChatRoom;

void create_chat_room_table(sqlite3* db);
bctbx_list_t * get_chat_rooms();
SpoChatRoom * get_chat_room_by_id(const unsigned long long id_chat_room);
SpoChatRoom * get_chat_room_by_id_user(const char * id_user);
unsigned long long save_chat_room(SpoChatRoom * room);
void update_chat_room(SpoChatRoom * room);
void delete_chat_room(SpoChatRoom * room);

#ifdef __cplusplus
}
#endif

#endif
