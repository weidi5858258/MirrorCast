package com.sony.p2plib.activity;

public interface Errno {
    int EPERM = 1;
    int ENOENT = 2;
    int ESRCH = 3;
    int EINTR = 4;
    int EIO = 5;
    int ENXIO = 6;
    int E2BIG = 7;
    int ENOEXEC = 8;
    int EBADF = 9;
    int ECHILD = 10;
    int EAGAIN = 11;
    int ENOMEM = 12;
    int EACCES = 13;
    int EFAULT = 14;
    int ENOTBLK = 15;
    int EBUSY = 16;
    int EEXIST = 17;
    int EXDEV = 18;
    int ENODEV = 19;
    int ENOTDIR = 20;
    int EISDIR = 21;
    int EINVAL = 22;
    int ENFILE = 23;
    int EMFILE = 24;
    int ENOTTY = 25;
    int ETXTBSY = 26;
    int EFBIG = 27;
    int ENOSPC = 28;
    int ESPIPE = 29;
    int EROFS = 30;
    int EMLINK = 31;
    int EPIPE = 32;
    int EDOM = 33;
    int ERANGE = 34;

    int EDEADLK = 35;
    int ENAMETOOLONG = 36;
    int ENOLCK = 37;
    int ENOSYS = 38;
    int ENOTEMPTY = 39;
    int ELOOP = 40;
    int EWOULDBLOCK = EAGAIN;
    int ENOMSG = 42;
    int EIDRM = 43;
    int ECHRNG = 44;
    int EL2NSYNC = 45;
    int EL3HLT = 46;
    int EL3RST = 47;
    int ELNRNG = 48;
    int EUNATCH = 49;
    int ENOCSI = 50;
    int EL2HLT = 51;
    int EBADE = 52;
    int EBADR = 53;
    int EXFULL = 54;
    int ENOANO = 55;
    int EBADRQC = 56;
    int EBADSLT = 57;
    int EDEADLOCK = EDEADLK;
    int EBFONT = 59;
    int ENOSTR = 60;
    int ENODATA = 61;
    int ETIME = 62;
    int ENOSR = 63;
    int ENONET = 64;
    int ENOPKG = 65;
    int EREMOTE = 66;
    int ENOLINK = 67;
    int EADV = 68;
    int ESRMNT = 69;
    int ECOMM = 70;
    int EPROTO = 71;
    int EMULTIHOP = 72;
    int EDOTDOT = 73;
    int EBADMSG = 74;
    int EOVERFLOW = 75;
    int ENOTUNIQ = 76;
    int EBADFD = 77;
    int EREMCHG = 78;
    int ELIBACC = 79;
    int ELIBBAD = 80;
    int ELIBSCN = 81;
    int ELIBMAX = 82;
    int ELIBEXEC = 83;
    int EILSEQ = 84;
    int ERESTART = 85;
    int ESTRPIPE = 86;
    int EUSERS = 87;
    int ENOTSOCK = 88;
    int EDESTADDRREQ = 89;
    int EMSGSIZE = 90;
    int EPROTOTYPE = 91;
    int ENOPROTOOPT = 92;
    int EPROTONOSUPPORT = 93;
    int ESOCKTNOSUPPORT = 94;
    int EOPNOTSUPP = 95;
    int EPFNOSUPPORT = 96;
    int EAFNOSUPPORT = 97;
    int EADDRINUSE = 98;
    int EADDRNOTAVAIL = 99;
    int ENETDOWN = 100;
    int ENETUNREACH = 101;
    int ENETRESET = 102;
    int ECONNABORTED = 103;
    int ECONNRESET = 104;
    int ENOBUFS = 105;
    int EISCONN = 106;
    int ENOTCONN = 107;
    int ESHUTDOWN = 108;
    int ETOOMANYREFS = 109;
    int ETIMEDOUT = 110;
    int ECONNREFUSED = 111;
    int EHOSTDOWN = 112;
    int EHOSTUNREACH = 113;
    int EALREADY = 114;
    int EINPROGRESS = 115;
    int ESTALE = 116;
    int EUCLEAN = 117;
    int ENOTNAM = 118;
    int ENAVAIL = 119;
    int EISNAM = 120;
    int EREMOTEIO = 121;
    int EDQUOT = 122;
    int ENOMEDIUM = 123;
    int EMEDIUMTYPE = 124;
    int ECANCELED = 125;
    int ENOKEY = 126;
    int EKEYEXPIRED = 127;
    int EKEYREVOKED = 128;
    int EKEYREJECTED = 129;
    int EOWNERDEAD = 130;
    int ENOTRECOVERABLE = 131;
    int ERFKILL = 132;
    int EHWPOISON = 133;


    int OK = 0;    // Everything's swell.
    int NO_ERROR = 0;    // No errors.

    int UNKNOWN_ERROR = (-2147483647 - 1); // INT32_MIN value

    int NO_MEMORY = -ENOMEM;
    int INVALID_OPERATION = -ENOSYS;
    int BAD_VALUE = -EINVAL;
    int BAD_TYPE = (UNKNOWN_ERROR + 1);
    int NAME_NOT_FOUND = -ENOENT;
    int PERMISSION_DENIED = -EPERM;
    int NO_INIT = -ENODEV;
    int ALREADY_EXISTS = -EEXIST;
    int DEAD_OBJECT = -EPIPE;
    int FAILED_TRANSACTION = (UNKNOWN_ERROR + 2);
    int BAD_INDEX = -EOVERFLOW;
    int NOT_ENOUGH_DATA = -ENODATA;
    int WOULD_BLOCK = -EWOULDBLOCK;
    int TIMED_OUT = -ETIMEDOUT;
    int UNKNOWN_TRANSACTION = -EBADMSG;
    int FDS_NOT_ALLOWED = (UNKNOWN_ERROR + 7);
    int UNEXPECTED_NULL = (UNKNOWN_ERROR + 8);

    // Media errors
    int MEDIA_ERROR_BASE = -1000;
    int ERROR_ALREADY_CONNECTED = MEDIA_ERROR_BASE;
    int ERROR_NOT_CONNECTED = MEDIA_ERROR_BASE - 1;
    int ERROR_UNKNOWN_HOST = MEDIA_ERROR_BASE - 2;
    int ERROR_CANNOT_CONNECT = MEDIA_ERROR_BASE - 3;
    int ERROR_IO = MEDIA_ERROR_BASE - 4;
    int ERROR_CONNECTION_LOST = MEDIA_ERROR_BASE - 5;
    int ERROR_MALFORMED = MEDIA_ERROR_BASE - 7;
    int ERROR_OUT_OF_RANGE = MEDIA_ERROR_BASE - 8;
    int ERROR_BUFFER_TOO_SMALL = MEDIA_ERROR_BASE - 9;
    int ERROR_UNSUPPORTED = MEDIA_ERROR_BASE - 10;
    int ERROR_END_OF_STREAM = MEDIA_ERROR_BASE - 11;
}
