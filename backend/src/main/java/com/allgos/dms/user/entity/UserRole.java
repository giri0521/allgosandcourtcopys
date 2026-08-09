package com.allgos.dms.user.entity;

/**
 * An admin can do everything a member can, plus user management, department and folder management,
 * deleting anyone's file, and monitoring. There are no other roles.
 */
public enum UserRole {
    ADMIN,
    MEMBER
}
