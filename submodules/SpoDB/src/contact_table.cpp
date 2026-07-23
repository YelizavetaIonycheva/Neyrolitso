#include "contact_table.h"

/**
 * Функция создания таблицы spo_contact
 * @param db - указатель на БД
 */
void create_contact_table(sqlite3* db) {
	char * errmsg = NULL;
	int ret;
	ret = sqlite3_exec(db,"CREATE TABLE IF NOT EXISTS spo_contact ("
							 "id             	INTEGER PRIMARY KEY AUTOINCREMENT,"
							 "id_user      		TEXT NOT NULL,"
							 "full_name      	TEXT NOT NULL,"
							 "sip_number    	TEXT NOT NULL,"
		                     "uri_photo    	    TEXT"
						");",
			0,0,&errmsg);
	if (ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}
}

void create_contact_table_portal(sqlite3* db) {
    char * errmsg = NULL;
    int ret;
    ret = sqlite3_exec(db,"CREATE TABLE IF NOT EXISTS spo_contact ("
                          "id             	INTEGER PRIMARY KEY AUTOINCREMENT,"
                          "id_user      	TEXT,"
                          "full_name      	TEXT NOT NULL,"
                          "sip_number    	TEXT NOT NULL,"
                          "uri_photo    	TEXT"
                          ");",
                       0,0,&errmsg);
    if (ret != SQLITE_OK) {
        sqlite3_free(errmsg);
    }
}



/* DB layout:
 * | 0  | id
 * | 1  | id_user
 * | 2  | full_name
 * | 3  | sip_number
 * | 4  | uri_photo
 */
static int callback(void *data, int argc, char **argv, char **colName) {
	StorageResult *clsres = (StorageResult *)data;
	SpoContact * contact;

	contact = (SpoContact *)malloc(sizeof(SpoContact));
	
	contact->id = (unsigned long long)atoll(argv[0]);
	contact->id_user = strdup(argv[1]);
	contact->full_name = strdup(argv[2]);
	contact->sip_number = strdup(argv[3]);
	contact->uri_photo = strdup(argv[4]);

	clsres->result = bctbx_list_append(clsres->result, contact);
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



bctbx_list_t * get_contacts() {
	char * buf;
	StorageResult clsres;
	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_contact ORDER BY full_name ASC");

	clsres.result = NULL;
	sql_request(dbContext->db, buf, &clsres);
	
	sqlite3_free(buf);

	return clsres.result;
}

SpoContact * get_contact_by_id(const unsigned long long call_id) {
	char * buf;
	StorageResult clsres;
	SpoContact * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_contact WHERE id = '%llu' ORDER BY id DESC LIMIT 1", call_id);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoContact *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

SpoContact * get_contact_by_number(const char * number) {
	char * buf;
	
	StorageResult clsres;
	SpoContact * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_contact WHERE sip_number = '%q' ORDER BY id DESC LIMIT 1", number);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoContact *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

SpoContact * get_contact_by_id_user(const char * id_user) {
	char * buf;
	StorageResult clsres;
	SpoContact * result = NULL;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_contact WHERE id_user = '%q' ORDER BY id DESC LIMIT 1", id_user);

	clsres.result = NULL;

	sql_request(dbContext->db, buf, &clsres);

	sqlite3_free(buf);

	if (clsres.result != NULL) {
		result = (SpoContact *)bctbx_list_get_data(clsres.result);
	}

	return result;
}

bctbx_list_t * get_contact_search(const char * s) {
	char * buf;
	StorageResult clsres;

	if (!dbContext || dbContext->db == NULL) return NULL;

	buf = sqlite3_mprintf("SELECT * FROM spo_contact WHERE full_name LIKE '%%%q%%' OR sip_number LIKE '%%%q%%' ORDER BY full_name ASC", s, s);
	clsres.result = NULL;
	sql_request(dbContext->db, buf, &clsres);
	sqlite3_free(buf);

	return clsres.result;
}

unsigned long long save_contact(SpoContact * contact) {
	char * buf;
	if (!dbContext || dbContext->db != NULL){
		buf = sqlite3_mprintf("INSERT INTO spo_contact VALUES(NULL,%Q,%Q,%Q,%Q);",
						contact->id_user,
						contact->full_name,
						contact->sip_number,
						contact->uri_photo
					);
		sql_request_generic(dbContext->db, buf);
		sqlite3_free(buf);

		contact->id = (unsigned long long)sqlite3_last_insert_rowid(dbContext->db);
	}

	return contact->id;
}

void update_contact(SpoContact * contact){
	char *buf;
	
	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("UPDATE spo_contact SET full_name=%Q, uri_photo=%Q  WHERE (id = %llu);",
							  contact->full_name,
						      contact->uri_photo,
							  contact->id);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}

void delete_contact(SpoContact * contact) {
	char *buf;

	if (!dbContext || dbContext->db == NULL) return ;

	buf = sqlite3_mprintf("DELETE FROM spo_contact WHERE id = %llu", contact->id);
	sql_request_generic(dbContext->db, buf);
	sqlite3_free(buf);
}



