//
// Created by root on 21-6-28.
//

#ifndef MIRRORCAST_MEDIACLIENT_H
#define MIRRORCAST_MEDIACLIENT_H

#endif //MIRRORCAST_MEDIACLIENT_H

bool client_connect();

void client_disconnect();

ssize_t send_data(uint8_t *data_buffer, ssize_t length);

void set_client_info(const char *info, int length);
