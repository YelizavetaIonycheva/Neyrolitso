#include "chat_message_table.h"

void create_chat_message_table(sqlite3* db) {
	char* errmsg=NULL;
	int ret;
	ret=sqlite3_exec(db,"CREATE TABLE IF NOT EXISTS spo_message ("
							 "id             	INTEGER PRIMARY KEY AUTOINCREMENT,"
							 "dir         		INTEGER,"
							 "is_read      		INTEGER,"
							 "type_content      INTEGER,"
							 "status     	    INTEGER,"
							 "id_user 			TEXT NOT NULL,"
							 "message 			TEXT NOT NULL,"
							 "id_chat_room 		INTEGER,"
							 "date 				INTEGER"
						");",
			0,0,&errmsg);
	if(ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
}

/*
 * DB layout:
 * | 0  | id
 * | 1  | dir
 * | 2  | is_read
 * | 3  | type_content
 * | 4  | status
 * | 5  | id_user
 * | 6  | message
 * | 7  | id_chat_room
 * | 8  | date
 */
static int callback(void *data, int argc, char **argv, char **colName) {
	StorageResult *clsres = (StorageResult *)data;
	SpoChatMessage * chat_message;

	chat_message = (SpoChatMessage *) malloc(sizeof(SpoChatMessage));

	chat_message->id 			= (unsigned long long) atoll(argv[0]);
	chat_message->dir 			= (unsigned int) atoi(argv[1]);
	chat_message->is_read 		= (unsigned int) atoi(argv[2]);
	chat_message->type_content 	= (unsigned int) atoi(argv[3]);
	chat_message->status 	    = (unsigned int) atoi(argv[4]);
	chat_message->id_user 		= strdup(argv[5]);
	chat_message->message 		= strdup(argv[6]);
	chat_message->id_chat_room 	= (unsigned long long) atoll(argv[7]);
	chat_message->date 			= (unsigned long long) atoll(argv[8]);

	clsres->result = bctbx_list_append(clsres->result, chat_message);
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

static int callback_count(void *data, int argc, char **argv, char **colName) {
    StorageResult *clsres = (StorageResult *)data;
    int num = atoi(argv[0]);
    if (argc > 0)
        clsres->count = num;
    return 0;
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









unsigned int get_unread_messages_count(unsigned long long id_chat_room) {
	char * buf;
	char* errmsg = NULL;
	StorageResult clsres;
	int ret;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT COUNT(*) FROM spo_message WHERE id_chat_room = '%llu' AND  is_read = '%i'", id_chat_room, 0);

	clsres.count = 0;
	clsres.result = NULL;
	ret = sqlite3_exec(dbContext->db, buf, callback_count, &clsres, &errmsg);
	if (ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
	sqlite3_free(buf);

	return clsres.count;
}

void set_read_status_messages(unsigned long long id_chat_room) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("UPDATE spo_message SET is_read=%i WHERE id_chat_room = '%llu' AND is_read = '0'",
						  1,
						  id_chat_room
	);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

SpoChatMessage * get_chat_message_by_id(unsigned long long id_message) {
	char * buf;
	StorageResult clsres;
	SpoChatMessage * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE id = '%llu' ORDER BY id DESC LIMIT 1", id_message);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoChatMessage *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

bctbx_list_t * get_spo_chat_messages_range(unsigned long long id_chat_room, int startm, int endm, unsigned char is_descent) {
	char * buf;
	StorageResult clsres;

	if (!dbContext || dbContext->db == NULL) return NULL;

	if ((endm >0 && endm >= startm) || (startm == 0 && endm == 0) ){
		if (startm > 0){
			if (is_descent == 0) {
				buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date ASC LIMIT %i OFFSET %i",
						id_chat_room,
						endm+1-startm,
						startm
				);
			} else {
				buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date DESC LIMIT %i OFFSET %i",
						id_chat_room,
						endm+1-startm,
						startm
				);
			}
		} else {
			if (is_descent == 0) {
				buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date ASC LIMIT %i",
									  id_chat_room,
									  endm+1-startm,
									  startm
				);
			} else {
				buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date DESC LIMIT %i",
									  id_chat_room,
									  endm+1-startm,
									  startm
				);
			}
		}
	} else {
		if(startm>0) {
			if (is_descent == 0) {
				buf = sqlite3_mprintf(
						"SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date ASC LIMIT -1 OFFSET %i",
						id_chat_room,
						endm + 1 - startm,
						startm
				);
			} else {
				buf = sqlite3_mprintf(
						"SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date DESC LIMIT -1 OFFSET %i",
						id_chat_room,
						endm + 1 - startm,
						startm
				);
			}
		} else {
			if (is_descent == 0) {
				buf = sqlite3_mprintf(
						"SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date ASC LIMIT -1",
						id_chat_room,
						endm + 1 - startm,
						startm
				);
			} else {
				buf = sqlite3_mprintf(
						"SELECT * FROM spo_message WHERE id_chat_room = %llu ORDER BY date DESC LIMIT -1",
						id_chat_room,
						endm + 1 - startm,
						startm
				);
			}
		}
	}


	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	return clsres.result;
}

bctbx_list_t * get_waiting_spo_chat_messages() {
	char * buf;
	StorageResult clsres;
	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE status = '0' ORDER BY id");

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	return clsres.result;
}

bctbx_list_t * get_waiting_spo_chat_messages_by_id_chat_room(unsigned long long  id_chat_room) {
	char * buf;
	StorageResult clsres;
	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_message WHERE status = '0' AND id_chat_room = '%llu' ORDER BY id", id_chat_room);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	return clsres.result;
}

int get_num_message(unsigned long long  id_chat_room) {
	char * buf;
	char* errmsg = NULL;
	int ret;
    StorageResult clsres;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT COUNT(*) FROM spo_message WHERE id_chat_room = '%llu'",
			id_chat_room
	);

	clsres.count = 0;
    clsres.result = NULL;
    ret = sqlite3_exec(dbContext->db, buf, callback_count, &clsres, &errmsg);
    if (ret != SQLITE_OK) {
        sqlite3_free(errmsg);
    }

	sqlite3_free(buf);

	return clsres.count;
}

unsigned long long save_message(SpoChatMessage * m) {
	char * buf;
	if (!dbContext || dbContext->db != NULL){
		buf = sqlite3_mprintf("INSERT INTO spo_message VALUES(NULL,%u,%u,%u,%u,%Q,%Q,%llu,%llu);",
							  m->dir,
							  m->is_read,
							  m->type_content,
							  m->status,
						      m->id_user,
							  m->message,
							  m->id_chat_room,
							  m->date
		);
		sql_request_generic(dbContext->db, buf);
		sqlite3_free(buf);

		m->id = (unsigned long long)sqlite3_last_insert_rowid(dbContext->db);
	}

	return m->id;
}

void update_message(SpoChatMessage * m) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("UPDATE spo_message SET dir=%u, is_read=%u, type_content=%u, status=%u, id_user=%Q, message=%Q, id_chat_room=%llu, date=%llu WHERE (id = %llu);",
						  m->dir,
						  m->is_read,
						  m->type_content,
						  m->status,
						  m->id_user,
						  m->message,
						  m->id_chat_room,
						  m->date,
						  m->id
	);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

void delete_message(SpoChatMessage * m) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return;

	buf = sqlite3_mprintf("DELETE FROM spo_message WHERE id = %llu", m->id);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

void delete_all_messages(unsigned long long id_chat_room) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return;

	buf = sqlite3_mprintf("DELETE FROM spo_message WHERE id_chat_room = %llu", id_chat_room);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}
