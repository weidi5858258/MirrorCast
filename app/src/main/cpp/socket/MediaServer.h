//
// Created by root on 21-6-28.
//

#ifndef MIRRORCAST_MEDIASERVER_H
#define MIRRORCAST_MEDIASERVER_H

#endif //MIRRORCAST_MEDIASERVER_H

void setIP(const char *ip);

void server_accept();

void server_close();

void close_all_clients();

void close_client(int which_client);

void server_accept_udp();