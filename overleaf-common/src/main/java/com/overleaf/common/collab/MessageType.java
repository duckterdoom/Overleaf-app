package com.overleaf.common.collab;

/**
 * Shared Module: Định nghĩa các loại thông điệp trao đổi TCP Socket giữa Client và Server.
 */
public enum MessageType {
    JOIN_ROOM,
    LEAVE_ROOM,
    USER_LIST,
    TEXT_UPDATE,
    CURSOR_MOVE,
    COMPILE_REQUEST,
    COMPILE_NOTIFY,
    CHAT_MESSAGE,
    ERROR
}
