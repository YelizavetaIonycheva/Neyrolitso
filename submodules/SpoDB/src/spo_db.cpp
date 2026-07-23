
#include <cstring>
#include <time.h>
#include "spo_db.h"
#include "spmemvfs.h"
#include "cypher.h"

#include "chat_file_table.h"
#include "chat_message_table.h"
#include "chat_room_table.h"
#include "contact_table.h"

void spo_storage_init(unsigned char * key, int len_key);
void spo_storage_init_portal(unsigned char * key, int len_key);
int spo_sqlite3_open(const char *db_file, sqlite3 **db, unsigned char * key, int len_key);

DB_Context * dbContext = NULL;

char * strdup(const char *tmp){
	size_t sz;
	char *ret;
	if (tmp == NULL)
	  return NULL;
	sz = strlen(tmp)+1;
	ret = (char*)malloc(sz);
	strcpy(ret,tmp);
	ret[sz-1]='\0';
	return ret;
}


void spo_set_database_path(const char *path, unsigned char * key, int len_key) {
	if (dbContext == NULL) {
		dbContext = (DB_Context *)malloc(sizeof(DB_Context));
		dbContext->db_file = NULL;
		dbContext->db = NULL;
		dbContext->mem = NULL;
		dbContext->utf8_filename = NULL;
		dbContext->key_db = NULL;
	}

	if (path) {
		dbContext->db_file = strdup(path);
		spo_storage_init(key, len_key);
	}
}


void spo_storage_init(unsigned char * key, int len_key) {
	int ret;
	const char *errmsg;
	sqlite3 *db;

	//spo_storage_close();

	ret = spo_sqlite3_open(dbContext->db_file, &db, key, len_key);
	if(ret != SQLITE_OK) {
		errmsg = sqlite3_errmsg(db);
		sqlite3_close(db);
		return;
	}
	
	spo_create_contact_table(db);
	spo_create_chat_room_table(db);
	spo_create_chat_message_table(db);
	spo_create_file_table(db);

	dbContext->db = db;
}

void spo_storage_init_portal(unsigned char * key, int len_key) {
	int ret;
	const char *errmsg;
	sqlite3 *db;

	//spo_storage_close();

	ret = spo_sqlite3_open(dbContext->db_file, &db, key, len_key);
	if(ret != SQLITE_OK) {
		errmsg = sqlite3_errmsg(db);
		sqlite3_close(db);
		return;
	}

	spo_create_contact_table_portal(db);

	dbContext->db = db;
}

static int readFile( const char * path, spmembuffer_t * mem, unsigned char * key_bd) {
	int ret = -1;
	int i;
	int st_size;
	unsigned char *buffer;
	int len_close_data;
	int num0;
	unsigned char IMZ[LEN_IMZ_8], imz_db[8];
	unsigned int SP[2];

	FILE * fp = fopen( path, "r" );
	if( NULL != fp ) {
		fseek(fp, 0, SEEK_END);
		st_size = ftell(fp);
		fseek(fp, 0, SEEK_SET);
		if(st_size <= LEN_IMZ_8+LEN_SP_8) {
			return 1;
		}
		ret = 0;
		len_close_data = mem->total = mem->used = st_size - (LEN_IMZ_8+LEN_SP_8);
		mem->data = (char*)malloc( mem->used + 1 );
		fread( mem->data, mem->used, 1, fp );
		fread(imz_db, LEN_IMZ_8, 1, fp);
		fread((unsigned char *)SP, kBlockLen89, 1, fp);
		fclose(fp);


		// Расшифрование БД
		unsigned char ctx[kCfb89ContextLen];
		if(!cypher_magma_cfb_init((unsigned char *)key_bd, ctx, kBlockLen89, (unsigned char *)SP, LEN_SP_8))
			return 1;
		unsigned char * temp_buffer = (unsigned char * )malloc(len_close_data);
		if(!cypher_decr_cfb(ctx, (unsigned char *)mem->data, temp_buffer, len_close_data))
			return 1;
		memcpy(mem->data, temp_buffer, len_close_data);
		free(temp_buffer);
		free_cfb(ctx);

		num0 = 0;
		if (len_close_data % SIZE_CRYPT_BLOCK != 0) {
			num0 = ((len_close_data / SIZE_CRYPT_BLOCK + 1) * SIZE_CRYPT_BLOCK) - len_close_data;
		}
		buffer = (unsigned char *) malloc(len_close_data + num0);
		memcpy(buffer, mem->data, len_close_data);
		for (i = len_close_data; i < len_close_data + num0; i++) {
			buffer[i] = 0;
		}

		unsigned char imit_ctx[kImit89ContextLen];
		if (!cypher_magma_imit_init((unsigned char *)key_bd, LEN_IMZ_8, imit_ctx))
			return 1;
		if(!cypher_imit(imit_ctx, buffer, IMZ, len_close_data + num0))
			return 1;
		free_imit(imit_ctx);

		if (memcmp(imz_db, IMZ, LEN_IMZ_8)) {
			free(mem->data);
			mem->data = NULL;
			mem->total = mem->used = 0;
			free(buffer);
			return 2;
		}
		free(buffer);
		(mem->data)[len_close_data] = '\0';
	} else {
		ret = 1;
	}

	return ret;
}

static int writeFile( const char * path, spmembuffer_t * mem, unsigned char * key_bd) {
	int num0;
	unsigned char *buffer;
	int i, lenBuf;
	unsigned char IMZ[LEN_IMZ_8];
	int ret = -1;
	unsigned int SP[2];
	FILE * fp;
	unsigned long long t;

	if(mem->used > 0) {
		t = time(NULL);
		memcpy((unsigned char *)SP, (unsigned char *)&t, LEN_SP_8);
		fp = fopen( path, "w" );
		if( NULL != fp ) {
			ret = 0;
			lenBuf = mem->used;
			num0 = 0;
			if (lenBuf % SIZE_CRYPT_BLOCK != 0) {
				num0 = ((lenBuf / SIZE_CRYPT_BLOCK + 1) * SIZE_CRYPT_BLOCK) - lenBuf;
			}
			buffer = (unsigned char *) malloc(lenBuf + num0 + LEN_IMZ_8 + LEN_SP_8);
			memcpy(buffer, mem->data, lenBuf);
			for (i = lenBuf; i < lenBuf + num0; i++) {
				buffer[i] = 0;
			}

			//Расчет имитовставки
			unsigned char imit_ctx[kImit89ContextLen];
			if (!cypher_magma_imit_init(key_bd, LEN_IMZ_8, imit_ctx))
				return 1;
			if(!cypher_imit(imit_ctx, buffer, IMZ, lenBuf + num0))
				return 1;
			free_imit(imit_ctx);

			//Шифрование
			unsigned char ctx[kCfb89ContextLen];
			if(!cypher_magma_cfb_init(key_bd, ctx, kBlockLen89, (unsigned char *)SP, LEN_SP_8))
				return 1;
			unsigned char * temp_buffer = (unsigned char * )malloc(lenBuf + num0);
			if(!cypher_encr_cfb(ctx, buffer, temp_buffer,lenBuf + num0))
				return 1;
			memcpy(buffer, temp_buffer, lenBuf + num0);
			free(temp_buffer);
			free_cfb(ctx);

			memcpy(buffer + lenBuf, IMZ, LEN_IMZ_8);
			memcpy(buffer + lenBuf + LEN_IMZ_8, (unsigned char *)SP, LEN_SP_8);
			fwrite(buffer, lenBuf + LEN_IMZ_8 + LEN_SP_8, 1, fp);
			fclose(fp);
		}
	}
	return ret;
}

int spo_sqlite3_open(const char *db_file, sqlite3 **db, unsigned char * key, int len_key) {
	char* errmsg = NULL;
	int ret;
    spmemvfs_db_t dbs;
	
	if (dbContext == NULL)
		return -1;

	if (key == NULL)
		return -1;

	if (dbContext->key_db == NULL) {
		dbContext->key_db = (unsigned char *)malloc(32);
	}

	if(len_key > 32) {
		memcpy((unsigned char *) dbContext->key_db, key, 32);
	} else {
		memcpy((unsigned char *) dbContext->key_db, key, len_key);
	}

	dbContext->utf8_filename = strdup(db_file);
	dbContext->mem = (spmembuffer_t*)calloc(sizeof( spmembuffer_t ), 1 );
	spmemvfs_env_init();
	ret = readFile(dbContext->utf8_filename, dbContext->mem, (unsigned char *)dbContext->key_db);
	if(ret == 2){
		free(dbContext->mem);
		dbContext->mem = (spmembuffer_t*)calloc(sizeof( spmembuffer_t ), 1 );
	}
	ret = spmemvfs_open_db(&dbs, "123.db", dbContext->mem);
	*db = dbs.handle;

	if (ret != SQLITE_OK) return ret;
	ret = sqlite3_exec(*db, "PRAGMA temp_store=MEMORY", NULL, NULL, &errmsg);
	if (ret != SQLITE_OK) {
		sqlite3_free(errmsg);
	}

    ret = sqlite3_exec(*db, "PRAGMA case_sensitive_like=OFF", NULL, NULL, &errmsg);
    if (ret != SQLITE_OK) {
        sqlite3_free(errmsg);
    }

	return ret;
}

void spo_storage_close() {
	if (dbContext == NULL)
		return;
	
	if (dbContext->db){
		sqlite3_close(dbContext->db);
		dbContext->db=NULL;
	}

	if(dbContext->mem != NULL) {
		writeFile(dbContext->utf8_filename, dbContext->mem, (unsigned char *)dbContext->key_db);
		if(dbContext->mem->data != NULL) {
			free(dbContext->mem->data);
			dbContext->mem->data = NULL;
		}
		free(dbContext->mem);
		dbContext->mem = NULL;
	}

	if (dbContext->utf8_filename != NULL) {
		free(dbContext->utf8_filename);
		dbContext->utf8_filename = NULL;
	}
	if (dbContext != NULL)
		free(dbContext);
	dbContext = NULL;
}

void spo_storage_save() {
	if (dbContext == NULL)
		return;

	if(dbContext->mem != NULL && dbContext->utf8_filename != NULL && dbContext->key_db != NULL) {
		writeFile(dbContext->utf8_filename, dbContext->mem, (unsigned char *)dbContext->key_db);
	}

}

void spo_set_database_path_portal(const char *path, unsigned char * key, int len_key) {
	if (dbContext == NULL) {
		dbContext = (DB_Context *)malloc(sizeof(DB_Context));
		dbContext->db_file = NULL;
		dbContext->db = NULL;
		dbContext->mem = NULL;
		dbContext->utf8_filename = NULL;
		dbContext->key_db = NULL;
	}

	if (path) {
		dbContext->db_file = strdup(path);
		spo_storage_init_portal(key, len_key);
	}
}

void spo_storage_close_portal() {
	if (dbContext == NULL)
		return;

	if (dbContext->db){
		sqlite3_close(dbContext->db);
		dbContext->db=NULL;
	}

	if(dbContext->mem != NULL) {
		writeFile(dbContext->utf8_filename, dbContext->mem, (unsigned char *)dbContext->key_db);
		if(dbContext->mem->data != NULL) {
			free(dbContext->mem->data);
			dbContext->mem->data = NULL;
		}
		free(dbContext->mem);
		dbContext->mem = NULL;
	}

	if (dbContext->utf8_filename != NULL) {
		free(dbContext->utf8_filename);
		dbContext->utf8_filename = NULL;
	}
	if (dbContext != NULL)
		free(dbContext);
	dbContext = NULL;

}

void spo_storage_save_portal() {
	if (dbContext == NULL)
		return;

	if(dbContext->mem != NULL && dbContext->utf8_filename != NULL && dbContext->key_db != NULL) {
		writeFile(dbContext->utf8_filename, dbContext->mem, (unsigned char *)dbContext->key_db);
	}
}


/*** SpoContact ***/
void spo_create_contact_table(sqlite3* db) {
	return create_contact_table(db);
}

void spo_create_contact_table_portal(sqlite3* db) {
	return create_contact_table_portal(db);
}
	
bctbx_list_t * spo_get_contacts() {
	return get_contacts();
}

SpoContact * spo_get_contact_by_id(const unsigned long long call_id) {
	return get_contact_by_id(call_id);
}

SpoContact * spo_get_contact_by_number(const char * number) {
	return get_contact_by_number(number);
}
	
SpoContact * spo_get_contact_by_id_user(const char * id_user) {
	return get_contact_by_id_user(id_user);
}

bctbx_list_t * spo_get_contact_search(const char * s) {
	return get_contact_search(s);
}

unsigned long long spo_save_contact(SpoContact *contact) {
	return save_contact(contact);
}

void spo_update_contact(SpoContact * contact) {
	update_contact(contact);
}

void spo_delete_contact(SpoContact * contact) {
	delete_contact(contact);
}


/*** SpoChatRoom ***/
void spo_create_chat_room_table(sqlite3* db) {
	create_chat_room_table(db);
}

bctbx_list_t * spo_get_chat_rooms() {
	return get_chat_rooms();
}

SpoChatRoom * spo_get_chat_room_by_id(const unsigned long long room_id) {
	return get_chat_room_by_id(room_id);
}

SpoChatRoom * spo_get_chat_room_by_id_user(const char * id_user) {
	return get_chat_room_by_id_user(id_user);
}

unsigned long long spo_save_chat_room(SpoChatRoom * room) {
	return save_chat_room(room);
}

void spo_update_chat_room(SpoChatRoom * room) {
	update_chat_room(room);
}

void spo_delete_chat_room(SpoChatRoom * room){
	delete_chat_room(room);
}



/*** SpoChatMessage ***/

void spo_create_chat_message_table(sqlite3* db) {
    create_chat_message_table(db);
}

unsigned int spo_get_unread_messages_count(unsigned long long id_chat_room) {
    return get_unread_messages_count(id_chat_room);
}

void spo_set_read_status_messages(unsigned long long id_chat_room) {
    set_read_status_messages(id_chat_room);
}

SpoChatMessage * spo_get_chat_message_by_id(unsigned long long id_message) {
    return get_chat_message_by_id(id_message);
}

bctbx_list_t * spo_get_spo_chat_messages_range(unsigned long long id_chat_room, int startm, int endm, unsigned char is_descent) {
    return get_spo_chat_messages_range(id_chat_room, startm, endm, is_descent);
}

bctbx_list_t * spo_get_waiting_spo_chat_messages() {
    return get_waiting_spo_chat_messages();
}

bctbx_list_t * spo_get_waiting_spo_chat_messages_by_id_chat_room(unsigned long long id_chat_room) {
    return get_waiting_spo_chat_messages_by_id_chat_room(id_chat_room);
}

int spo_get_num_message(unsigned long long id_chat_room) {
    return get_num_message(id_chat_room);
}

unsigned long long spo_save_chat_message(SpoChatMessage * m) {
    return save_message(m);
}

void spo_update_chat_message(SpoChatMessage * m) {
    update_message(m);
}

void spo_delete_chat_message(SpoChatMessage * m) {
    delete_message(m);
}

void spo_delete_all_chat_messages(unsigned long long id_chat_room) {
    delete_all_messages(id_chat_room);
}


/*** SpoFile ***/
void spo_create_file_table(sqlite3* db) {
	create_file_table(db);
}

bctbx_list_t * spo_get_files_for_id_message(unsigned long long id_message) {
	return get_files_for_id_message(id_message);
}

SpoFile * spo_get_file(unsigned long long id_file) {
	return  get_file(id_file);
}

unsigned long long spo_save_file(SpoFile * f) {
	return save_file(f);
}

void spo_update_file(SpoFile * f) {
	update_file(f);
}
void spo_delete_file(SpoFile * f) {
	delete_file(f);
}














