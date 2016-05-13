typedef struct Object_vtable Object_vtable;
typedef struct Object_struct {Object_vtable* _vtable;}
Object_struct;
char Object_equals(Object_struct*, Object_struct*);
int Object_code(Object_struct*);
struct Object_vtable {
    char (*equals)(Object_struct*, Object_struct*);
    int (*code)(Object_struct*);
};
Object_vtable Object_static_vtable = {Object_equals, Object_code};
Object_struct* Object_init__(Object_struct* self) {
    self->_vtable = &Object_static_vtable;
    return self;
}
char Object_equals(Object_struct* a, Object_struct *b) {
    return a == b;
}

int Object_code(Object_struct* self) {

    unsigned h = (unsigned) self;
    h ^= (h >> 20) ^ (h >> 12);
    return (int) (h ^ (h >> 7) ^ (h >> 4));

}


#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#define HT_INITIAL_CAPACITY     16
#define HT_LOAD_FACTOR          0.75f

struct HashTable;
struct HashTableEntry;
typedef struct HashTable HashTable;
typedef struct HashTableEntry HashTableEntry;


struct HashTableEntry {
    int hash;
    Object_struct *key;
    Object_struct *value;
    HashTableEntry *next;
};

struct HashTable {

    HashTableEntry *entries;
    int size;
    int threshold;
    float loadFactor;
    int capacity;

};


static inline int hash_table_index(int hash, int capacity) {
    return hash & (capacity - 1);
}

static HashTable *new_hash_table() {
    HashTable *hashTable = malloc(sizeof(HashTable));
    if (!hashTable) return NULL;

    hashTable->entries = NULL;
    hashTable->size = 0;
    hashTable->capacity = 0;
    hashTable->threshold = HT_INITIAL_CAPACITY;
    hashTable->loadFactor = HT_LOAD_FACTOR;
    return hashTable;
}

static void hash_table_inflate(HashTable *hashTable, int size) {

    int capacity;
    if (size > 1) {
        // Next power of 2 capacity
        capacity = (size - 1) <<  1;
        capacity |= (capacity >>  1);
        capacity |= (capacity >>  2);
        capacity |= (capacity >>  4);
        capacity |= (capacity >>  8);
        capacity |= (capacity >> 16);
        capacity -= (capacity >>  1);
    } else {
        capacity = 1;
    }

    hashTable->entries = calloc(capacity, sizeof(HashTableEntry));
    hashTable->threshold = (int) (capacity * hashTable->loadFactor);
    hashTable->capacity = capacity;


}

static void hash_table_double(HashTable *hashTable) {

    int oldCapacity = hashTable->capacity;
    int newCapacity = oldCapacity * 2;
    int i;

    HashTableEntry *entries = calloc(newCapacity, sizeof(HashTableEntry));
    for (i = 0; i < oldCapacity; ++i) {
        HashTableEntry *e = &hashTable->entries[i];
        int first = 1;
        while (e && e->key != NULL) {
            int j = hash_table_index(e->hash, newCapacity);

            if (entries[j].key) {
                if (first) {
                    HashTableEntry *c = malloc(sizeof(HashTableEntry));
                    c->next = entries[j].next;
                    c->value = e->value;
                    c->hash = e->hash;
                    c->key = e->key;
                    entries[j].next = c;
                } else {
                    e->next = entries[j].next;
                    entries[j].next = e;
                }

                e = e->next;
            } else {
                entries[j].key = e->key;
                entries[j].hash = e->hash;
                entries[j].value = e->value;
                entries[j].next = NULL;
                HashTableEntry *next = e->next;
                if (!first) {
                    free(e);
                }
                e = next;
            }

            first = 0;
        }
    }

    free(hashTable->entries);
    hashTable->entries = entries;
    hashTable->capacity = newCapacity;
    hashTable->threshold = (int) (newCapacity * hashTable->loadFactor);

}


static void hash_table_insert_object_to_value(HashTable *hashTable, Object_struct *k, Object_struct *value) {

    HashTableEntry *e;
    int hashValue, i;

    if (hashTable->entries == NULL) {
        hash_table_inflate(hashTable, hashTable->threshold);
    }

    hashValue = k->_vtable->code(k);
    i = hash_table_index(hashValue, hashTable->capacity);
    printf("[i = %d, hash = %d]\n", i, hashValue);
    for (e = &hashTable->entries[i]; e != NULL && e->key != NULL; e = e->next) {
        if (k->_vtable->equals(k, e->key)) {
            e->value = value;
            return;
        }
    }
    if (hashTable->size > hashTable->threshold && hashTable->entries[i].key) {
        printf("REHASH\n");
        hash_table_double(hashTable);
        i = hash_table_index(hashValue, hashTable->capacity);
        printf("[i = %d, hash = %d]\n", i, hashValue);
    }

    if (hashTable->entries[i].key == NULL) {
        e = &hashTable->entries[i];
        e->next = NULL;
    } else {
        e = malloc(sizeof(HashTableEntry));
        e->next = hashTable->entries[i].next;
        hashTable->entries[i].next = e;
    }
    e->hash = hashValue;
    e->key = k;
    e->value = value;

    ++hashTable->size;
}

static Object_struct *hash_table_get_value(HashTable *hashTable, Object_struct *k) {
    HashTableEntry *e;
    int hashValue, i;

    if (hashTable->entries == NULL) {
        return NULL;
    }

    hashValue = k->_vtable->code(k);
    i = hash_table_index(hashValue, hashTable->capacity);
    for (e = &hashTable->entries[i]; e != NULL && e->key != NULL; e = e->next) {
        if (k->_vtable->equals(k, e->key)) {
            return e->value;
        }
    }
    return NULL;
}

int main(int argc, char **argv) {

    Object_struct K;
    Object_struct V;

    Object_struct *k = Object_init__(&K);
    Object_struct *v = Object_init__(&V);


    HashTable *hashTable = new_hash_table();
    hash_table_insert_object_to_value(hashTable, k, v);
    hash_table_insert_object_to_value(hashTable, k, v);
    hash_table_insert_object_to_value(hashTable, k, v);
    hash_table_insert_object_to_value(hashTable, k, v);
    hash_table_insert_object_to_value(hashTable, v, k);

    int i;
    for (i = 0; i < 70; ++i) {
        hash_table_insert_object_to_value(hashTable, Object_init__(malloc(sizeof(Object_struct))), v);
    }


    Object_struct *r = hash_table_get_value(hashTable, v);

    if (r->_vtable->equals(r, v)) {
        printf("Eq V\n");
    } else if (r->_vtable->equals(r, k)) {
        printf("Eq K\n");
    } else {
        printf("NEQ\n");
    }

    printf("%d\n", hashTable->size);

    return 0;
}
