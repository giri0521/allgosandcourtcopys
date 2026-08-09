package com.allgos.dms.user.entity;

/**
 * Account lifecycle. Admin approval is the only gate in the system, so this single field decides
 * whether a person can use the application at all.
 *
 * <pre>
 *   PENDING --approve--> ACTIVE  &lt;--&gt;  INACTIVE
 *      |
 *      +---reject----> REJECTED   (terminal)
 * </pre>
 */
public enum UserStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    INACTIVE;

    /** Only ACTIVE accounts may ever receive a token. */
    public boolean canSignIn() {
        return this == ACTIVE;
    }
}
