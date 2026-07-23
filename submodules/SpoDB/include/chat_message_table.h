#ifndef _CHAT_MESSAGE_TABLE_H
#define _CHAT_MESSAGE_TABLE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "spo_db_context.h"
#include <bctoolbox/list.h>
#include <sqlite3.h>

extern DB_Context * dbContext;

typedef struct _SpoChatMessage {
	unsigned long long id;
	unsigned int dir;
	unsigned int is_read;
	unsigned int type_content;
	unsigned int status;
	const char * id_user;
	const char * message;
	unsigned long long id_chat_room;
	unsigned long long date;
} SpoChatMessage;


void create_chat_message_table(sqlite3 *db);
unsigned int get_unread_messages_count(unsigned long long id_chat_room);
void set_read_status_messages(unsigned long long id_chat_room);
SpoChatMessage * get_chat_message_by_id(unsigned long long id_message);
bctbx_list_t * get_spo_chat_messages_range(unsigned long long id_chat_room, int startm, int endm, unsigned char is_descent);
bctbx_list_t * get_waiting_spo_chat_messages();
bctbx_list_t * get_waiting_spo_chat_messages_by_id_chat_room(unsigned long long id_chat_room);
int get_num_message(unsigned long long id_chat_room);
unsigned long long save_message(SpoChatMessage * m);
void update_message(SpoChatMessage * m);
void delete_message(SpoChatMessage * m);
void delete_all_messages(unsigned long long id_chat_room);



#ifdef __cplusplus
}
#endif

#endif