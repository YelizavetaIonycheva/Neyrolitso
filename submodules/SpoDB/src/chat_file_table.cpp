#include "chat_file_table.h"

void create_file_table(sqlite3* db) {
	char* errmsg = NULL;
	int ret;
	ret=sqlite3_exec(db,"CREATE TABLE IF NOT EXISTS spo_file ("
							 "id             	INTEGER PRIMARY KEY AUTOINCREMENT,"
							 "dir      			INTEGER,"
		                     "type				INTEGER,"
							 "id_message      	INTEGER,"
							 "id_from_server    TEXT,"
							 "status 			INTEGER,"
							 "uri 				TEXT,"
							 "url_download 		TEXT,"
							 "full_name 		TEXT NOT NULL"
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
 * | 2  | type
 * | 3  | id_message
 * | 4  | id_from_server
 * | 5  | status
 * | 6  | uri
 * | 7  | uri_download
 * | 8  | full_name
 */
static int callback(void *data, int argc, char **argv, char **colName) {
	StorageResult *clsres = (StorageResult *)data;
	SpoFile * file;

	file = (SpoFile *) malloc(sizeof(SpoFile));

	file->id 			= (unsigned long long) atoll(argv[0]);
	file->dir 			= (unsigned int) atoi(argv[1]);
    file->type 			= (unsigned int) atoi(argv[2]);
	file->id_message 	= (unsigned long long) atoll(argv[3]);
	file->id_from_server= strdup(argv[4]);
	file->status 	    = (unsigned int) atoi(argv[5]);
	file->uri 			= strdup(argv[6]);
	file->url_download 	= strdup(argv[7]);
	file->full_name 	= strdup(argv[8]);

	clsres->result = bctbx_list_append(clsres->result, file);
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





bctbx_list_t * get_files_for_id_message(unsigned long long id_message) {
	char * buf;
	StorageResult clsres;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_file WHERE id_message = '%llu'", id_message);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	return clsres.result;
}

SpoFile * get_file(unsigned long long id) {
	char * buf;
	StorageResult clsres;
	SpoFile * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_file WHERE id = '%llu' ORDER BY full_name DESC LIMIT 1", id);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoFile *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

unsigned long long save_file(SpoFile * f) {
	char * buf;
	if (!dbContext || dbContext->db != NULL){
		buf = sqlite3_mprintf("INSERT INTO spo_file VALUES(NULL,%u,%u,%llu,%Q,%u,%Q,%Q,%Q);",
							  f->dir,
							  f->type,
							  f->id_message,
							  f->id_from_server,
							  f->status,
							  f->uri,
							  f->url_download,
							  f->full_name
		);
		sql_request_generic(dbContext->db, buf);
		sqlite3_free(buf);

		f->id = (unsigned long long)sqlite3_last_insert_rowid(dbContext->db);
	}

	return f->id;
}

void update_file(SpoFile * f) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("UPDATE spo_file SET dir=%u, type=%u, id_message=%llu, id_from_server=%Q, status=%u, uri=%Q, url_download=%Q, full_name=%Q WHERE (id = %llu);",
						  f->dir,
						  f->type,
						  f->id_message,
						  f->id_from_server,
						  f->status,
						  f->uri,
						  f->url_download,
						  f->full_name,
						  f->id
	);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

void delete_file(SpoFile * f) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return;

	buf = sqlite3_mprintf("DELETE FROM spo_file WHERE id = %lu", f->id);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}
