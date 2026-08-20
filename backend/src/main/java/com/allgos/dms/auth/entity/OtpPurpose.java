package com.allgos.dms.auth.entity;

/**
 * What an issued code is for.
 *
 * <p>One value, deliberately kept as an enum: the column and its CHECK constraint already carry a
 * purpose, and a second use for a code is the kind of thing that arrives later. Sign-in is not among
 * them — a session is started with a password.
 */
public enum OtpPurpose {
    PASSWORD_RESET
}
