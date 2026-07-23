
#ifndef _SPO_DB_CONTEXT_H
#define _SPO_DB_CONTEXT_H



#ifdef __cplusplus
extern "C" {
#endif

#include <bctoolbox/list.h>
#include "sqlite3.h"
#include "spmemvfs.h"
#include <stdlib.h>

typedef struct _DB_Context {
    unsigned char * key_db;
    int len_key;
    char * db_file = NULL;
    sqlite3 *db = NULL;
    spmembuffer_t * mem = NULL;
    char * utf8_filename = NULL;
} DB_Context;

typedef struct _StorageResult {
    int count;
    bctbx_list_t *result;
} StorageResult;

#ifdef __cplusplus
}
#endif

#endif