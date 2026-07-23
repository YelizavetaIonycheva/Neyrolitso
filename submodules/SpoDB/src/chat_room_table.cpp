#include "chat_room_table.h"


void create_chat_room_table(sqlite3* db) {
	char* errmsg=NULL;
	int ret;
	ret=sqlite3_exec(db,"CREATE TABLE IF NOT EXISTS spo_chat_room ("
							 "id             	INTEGER PRIMARY KEY AUTOINCREMENT,"
							 "chat_type      	INTEGER,"
							 "id_user       	TEXT NOT NULL,"
							 "name_chat     	TEXT NOT NULL,"
							 "time_last_message INTEGER"
						");",
			0,0,&errmsg);
	if(ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
}


/*
 * DB layout:
 * | 0  | id
 * | 1  | chat_type
 * | 2  | id_user
 * | 3  | name_chat
 * | 4  | time_last_message
 */
static int callback(void *data, int argc, char **argv, char **colName) {
	StorageResult *clsres = (StorageResult *)data;
	SpoChatRoom * chat_room;

	chat_room = (SpoChatRoom *) malloc(sizeof(SpoChatRoom));

	chat_room->id 			= (unsigned long long)atoll(argv[0]);
	chat_room->chat_type 	= (unsigned int)atoi(argv[1]);
	chat_room->id_user 		= strdup(argv[2]);
	chat_room->name_chat 	= strdup(argv[3]);
	chat_room->time_last_message = (unsigned long long)atoll(argv[4]);
	clsres->result = bctbx_list_append(clsres->result, chat_room);
	return 0;
}

static void sql_request(sqlite3 *db, const char *stmt, StorageResult *clsres) {
	char* errmsg = NULL;
	int ret;
	ret = sqlite3_exec(db, stmt, callback, clsres, &errmsg);
	if (ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
}

static int sql_request_generic(sqlite3* db, const char *stmt) {
	char* errmsg = NULL;
	int ret;
	ret = sqlite3_exec(db, stmt, NULL, NULL, &errmsg);
	if (ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
	return ret;
}







bctbx_list_t * get_chat_rooms() {
	char * buf;
	StorageResult clsres;
	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_chat_room ORDER BY time_last_message DESC");

	clsres.result = NULL;
	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	return clsres.result;
}

SpoChatRoom * get_chat_room_by_id(const unsigned long long id_chat_room) {
	char * buf;
	StorageResult clsres;
	SpoChatRoom * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_chat_room WHERE id = '%llu' ORDER BY id DESC LIMIT 1", id_chat_room);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoChatRoom *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

SpoChatRoom * get_chat_room_by_id_user(const char * id_user) {
	char * buf;
	StorageResult clsres;
	SpoChatRoom * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_chat_room WHERE id_user = '%q' ORDER BY id DESC LIMIT 1", id_user);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoChatRoom *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

unsigned long long save_chat_room(SpoChatRoom * room) {
	char * buf;
	if (!dbContext || dbContext->db != NULL){
		buf = sqlite3_mprintf("INSERT INTO spo_chat_room VALUES(NULL,%u,%Q,%Q,%llu);",
							  room->chat_type,
							  room->id_user,
							  room->name_chat,
							  room->time_last_message
		);
		sql_request_generic(dbContext->db, buf);
		sqlite3_free(buf);

		room->id = (unsigned long long)sqlite3_last_insert_rowid(dbContext->db);
	}

	return room->id;
}

void update_chat_room(SpoChatRoom * room) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("UPDATE spo_chat_room SET chat_type=%u, id_user=%Q, name_chat=%Q, time_last_message=%llu WHERE (id = %llu);",
						  room->chat_type,
						  room->id_user,
						  room->name_chat,
						  room->time_last_message,
						  room->id
	);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

void delete_chat_room(SpoChatRoom * room) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return;

	buf = sqlite3_mprintf("DELETE FROM spo_chat_room WHERE id = %llu", room->id);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}
