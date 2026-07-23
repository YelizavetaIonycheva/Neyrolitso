
#ifndef _CONTACT_TABLE_TABLE_H
#define _CONTACT_TABLE_TABLE_H

#ifdef __cplusplus
extern "C" {
#endif

#include "spo_db_context.h"
#include <bctoolbox/list.h>
#include <sqlite3.h>

extern DB_Context * dbContext;

typedef struct _SpoContact {
	unsigned long long id;
	const char * id_user;
	const char * full_name;
	const char * sip_number;
	const char * uri_photo;
} SpoContact;


void create_contact_table(sqlite3* db);
void create_contact_table_portal(sqlite3* db);
bctbx_list_t * get_contacts();
SpoContact * get_contact_by_id(const unsigned long long call_id);
SpoContact * get_contact_by_number(const char * number);
SpoContact * get_contact_by_id_user(const char * id_user);
bctbx_list_t * get_contact_search(const char * s);
unsigned long long save_contact(SpoContact *contact);
void update_contact(SpoContact * contact);
void delete_contact(SpoContact * contact);


#ifdef __cplusplus
}
#endif

#endif