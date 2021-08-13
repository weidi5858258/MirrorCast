//
// Created by root on 2021/8/11.
//

#ifndef MIRRORCAST_MEDIADATA_H
#define MIRRORCAST_MEDIADATA_H


void setOrientation(int which_client, int orientation);

void putData(int which_client, unsigned char *encodedData, ssize_t size);

void *startDecoder(void *arg);

void free1();

void free2();

void freeAll();


#endif //MIRRORCAST_MEDIADATA_H
