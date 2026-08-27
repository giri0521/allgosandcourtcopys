package com.allgos.dms.notification.entity;

/** Stored as a plain string on the notification row; centralised here so the values stay consistent. */
public final class NotificationType {

    public static final String REGISTRATION_APPROVED = "registration_approved";
    public static final String REGISTRATION_REJECTED = "registration_rejected";
    public static final String REGISTRATION_SUBMITTED = "registration_submitted";
    public static final String ACCOUNT_DISABLED = "account_disabled";
    /** Promoted to admin, or put back to member. Either way the person is signed out. */
    public static final String ROLE_CHANGED = "role_changed";
    public static final String FILE_UPLOADED = "file_uploaded";
    /** Somebody telling the office something. Carries no subject — the message is the point. */
    public static final String ANNOUNCEMENT = "announcement";
    /** Fanned out to every admin, carrying the reason the member gave. */
    public static final String FILE_DELETED = "file_deleted";
    public static final String FILE_RESTORED = "file_restored";
    /** An admin removed an empty folder. Carries the reason, same as {@link #FILE_DELETED}. */
    public static final String FOLDER_DELETED = "folder_deleted";

    private NotificationType() {}
}
