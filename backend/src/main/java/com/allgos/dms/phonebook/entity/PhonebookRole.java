package com.allgos.dms.phonebook.entity;

/**
 * A taluk contact's standing.
 *
 * <p>The two are kept apart rather than left to a free-text designation because the listing groups
 * by them: somebody looking for a taluk is nearly always looking for its tahsildar first.
 */
public enum PhonebookRole {
    TAHSILDAR,
    GROUP_MEMBER
}
