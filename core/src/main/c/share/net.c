/*******************************************************************************
 *    ___                  _   ____  ____
 *   / _ \ _   _  ___  ___| |_|  _ \| __ )
 *  | | | | | | |/ _ \/ __| __| | | |  _ \
 *  | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *   \__\_\\__,_|\___||___/\__|____/|____/
 *
 * Copyright (C) 2014-2018 Appsicle
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 ******************************************************************************/

#include <jni.h>
#include <sys/socket.h>
#include <sys/fcntl.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <sys/errno.h>
#include <stdlib.h>
#include "net.h"

JNIEXPORT jlong JNICALL Java_com_questdb_std_Net_socketTcp
        (JNIEnv *e, jclass cl, jboolean blocking) {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    if (fd > 0 && !blocking) {
        if (fcntl(fd, F_SETFL, O_NONBLOCK) < 0) {
            close(fd);
            return -1;
        }

        int oni = 1;
        if (setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, (char *) &oni, sizeof(oni)) < 0) {
            close(fd);
            return -1;
        }
    }
    return fd;
}

JNIEXPORT jlong JNICALL Java_com_questdb_std_Net_socketUdp0
        (JNIEnv *e, jclass cl) {
    int fd = socket(AF_INET, SOCK_DGRAM, 0);

    if (fd > 0 && fcntl(fd, F_SETFL, O_NONBLOCK) < 0) {
        close(fd);
        return -1;
    }

    return fd;
}

JNIEXPORT jlong JNICALL Java_com_questdb_std_Net_sockaddr
        (JNIEnv *e, jclass cl, jint address, jint port) {
    struct sockaddr_in *addr = calloc(1, sizeof(struct sockaddr_in));
    addr->sin_family = AF_INET;
    addr->sin_addr.s_addr = htonl((uint32_t) address);
    addr->sin_port = htons((uint16_t) port);
    return (jlong) addr;
}

JNIEXPORT void JNICALL Java_com_questdb_std_Net_freeSockAddr
        (JNIEnv *e, jclass cl, jlong address) {
    if (address != 0) {
        free((void *) address);
    }
}


JNIEXPORT jint JNICALL Java_com_questdb_std_Net_sendTo
        (JNIEnv *e, jclass cl, jlong fd, jlong ptr, jint len, jlong sockaddr) {
    return (jint) sendto((int) fd, (const void *) ptr, (size_t) len, 0, (const struct sockaddr *) sockaddr,
                         sizeof(struct sockaddr_in));
}

JNIEXPORT jboolean JNICALL Java_com_questdb_std_Net_bindTcp
        (JNIEnv *e, jobject cl, jlong fd, jint address, jint port) {
    struct sockaddr_in addr;

    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl((uint32_t) address);
    addr.sin_port = htons((uint16_t) port);

    return (jboolean) (bind((int) fd, (struct sockaddr *) &addr, sizeof(addr)) == 0);
}

JNIEXPORT jboolean JNICALL Java_com_questdb_std_Net_bindUdp
        (JNIEnv *e, jobject cl, jlong fd, jint address, jint port) {
    return Java_com_questdb_std_Net_bindTcp(e, cl, fd, address, port);
}

JNIEXPORT jboolean JNICALL Java_com_questdb_std_Net_join
        (JNIEnv *e, jclass cl, jlong fd, jint bindAddress, jint groupAddress) {
    struct ip_mreq mreq;
    mreq.imr_interface.s_addr = htonl((uint32_t) bindAddress);
    mreq.imr_multiaddr.s_addr = htonl((uint32_t) groupAddress);
    return (jboolean) (setsockopt((int) fd, IPPROTO_IP, IP_ADD_MEMBERSHIP, &mreq, sizeof(mreq)) < 0 ? JNI_FALSE
                                                                                                    : JNI_TRUE);
}


JNIEXPORT jlong JNICALL Java_com_questdb_std_Net_accept
        (JNIEnv *e, jobject cl, jlong fd) {
    return accept((int) fd, NULL, NULL);
}

JNIEXPORT void JNICALL Java_com_questdb_std_Net_listen
        (JNIEnv *e, jclass cl, jlong fd, jint backlog) {
    listen((int) fd, backlog);
}

jint convert_error(ssize_t n) {
    if (n > 0) {
        return (jint) n;
    }

    switch (n) {
        case 0:
            return com_questdb_std_Net_EPEERDISCONNECT;
        default:
            return (jint) (errno == EWOULDBLOCK ? com_questdb_std_Net_ERETRY : com_questdb_std_Net_EOTHERDISCONNECT);
    }
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_send
        (JNIEnv *e, jclass cl, jlong fd, jlong ptr, jint len) {
    return convert_error(send((int) fd, (const void *) ptr, (size_t) len, 0));
}


JNIEXPORT jint JNICALL Java_com_questdb_std_Net_recv
        (JNIEnv *e, jclass cl, jlong fd, jlong ptr, jint len) {
    return convert_error(recv((int) fd, (void *) ptr, (size_t) len, 0));
}

JNIEXPORT jboolean JNICALL Java_com_questdb_std_Net_isDead
        (JNIEnv *e, jclass cl, jlong fd) {
    int c;
    return (jboolean) (recv((int) fd, &c, 1, 0) == 0);
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_abortAccept
        (JNIEnv *e, jclass cl, jlong fd) {
    return shutdown((int) fd, SHUT_RDWR); 
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_configureNonBlocking
        (JNIEnv *e, jclass cl, jlong fd) {
    int flags;


    if ((flags = fcntl((int) fd, F_GETFL, 0)) < 0) {
        return flags;
    }


    if ((flags = fcntl((int) fd, F_SETFL, flags | O_NONBLOCK)) < 0) {
        return flags;
    }

    return 0;
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_configureNoLinger
        (JNIEnv *e, jclass cl, jlong fd) {
    struct linger sl;
    sl.l_onoff = 1;
    sl.l_linger = 0;
    return setsockopt((int) fd, SOL_SOCKET, SO_LINGER, &sl, sizeof(sl));
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_connect
        (JNIEnv *e, jclass cl, jlong fd, jlong sockAddr) {
    return connect((int) fd, (const struct sockaddr *) sockAddr, sizeof(struct sockaddr));
}


JNIEXPORT jint JNICALL Java_com_questdb_std_Net_setSndBuf
        (JNIEnv *e, jclass cl, jlong fd, jint size) {
    jint sz = size;
    return setsockopt((int) fd, SOL_SOCKET, SO_SNDBUF, (char *) &sz, sizeof(sz));
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_setRcvBuf
        (JNIEnv *e, jclass cl, jlong fd, jint size) {
    jint sz = size;
    return setsockopt((int) fd, SOL_SOCKET, SO_RCVBUF, (char *) &sz, sizeof(sz));
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_getEwouldblock
        (JNIEnv *e, jclass cl) {
    return EWOULDBLOCK;
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_getPeerIP
        (JNIEnv *e, jclass cl, jlong fd) {
    struct sockaddr peer;
    socklen_t nameLen = sizeof(peer);

    if (getpeername((int) fd, &peer, &nameLen) == 0) {
        if (peer.sa_family == AF_INET) {
            return ntohl(((struct sockaddr_in *) &peer)->sin_addr.s_addr);
        }
        return -2;
    }
    return -1;
}

JNIEXPORT jint JNICALL Java_com_questdb_std_Net_getPeerPort
        (JNIEnv *e, jclass cl, jlong fd) {
    struct sockaddr peer;
    socklen_t nameLen = sizeof(peer);

    if (getpeername((int) fd, &peer, &nameLen) == 0) {
        if (peer.sa_family == AF_INET) {
            return ntohs(((struct sockaddr_in *) &peer)->sin_port);
        } else {
            return -2;
        }
    }
    return -1;
}
