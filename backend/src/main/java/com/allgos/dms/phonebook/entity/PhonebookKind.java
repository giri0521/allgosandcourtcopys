package com.allgos.dms.phonebook.entity;

/** Which book a contact belongs to. */
public enum PhonebookKind {
    /** Hangs from a department, and carries a designation rather than a role. */
    DEPARTMENT,
    /** Hangs from a taluk, and is either its tahsildar or one of its group members. */
    TALUK
}
