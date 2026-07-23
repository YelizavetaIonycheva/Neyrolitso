#ifndef _CHAT_FILE_TABLE_H
#define _CHAT_FILE_TABLE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "spo_db_context.h"
#include <bctoolbox/list.h>
#include <sqlite3.h>

extern DB_Context * dbContext;

typedef struct _SpoFile {
	unsigned long long id;
	unsigned int dir;
	unsigned int type;
	unsigned long long id_message;
	const char * id_from_server;
	unsigned int status;
	const char * uri;
	const char * url_download;
	const char * full_name;
} SpoFile;

void create_file_table(sqlite3* db);
bctbx_list_t * get_files_for_id_message(unsigned long long id_message);
SpoFile * get_file(unsigned long long id);
unsigned long long save_file(SpoFile * f);
void update_file(SpoFile * f);
void delete_file(SpoFile * f);


#ifdef __cplusplus
}
#endif


#endif