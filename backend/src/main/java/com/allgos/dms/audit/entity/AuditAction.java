package com.allgos.dms.audit.entity;

/** The action vocabulary written to audit_logs.action. */
public final class AuditAction {

    // authentication
    public static final String REGISTER = "register";
    public static final String OTP_SENT = "otp_sent";
    public static final String OTP_VERIFIED = "otp_verified";
    public static final String OTP_FAILED = "otp_failed";
    public static final String LOGIN_PASSWORD = "login_password";
    public static final String LOGIN_FAILED = "login_failed";
    public static final String LOGIN_BLOCKED_STATUS = "login_blocked_status";
    public static final String LOGIN_BLOCKED_OTP_REQUIRED = "login_blocked_otp_required";
    public static final String LOGOUT = "logout";
    public static final String LOGOUT_ALL = "logout_all";
    public static final String PASSWORD_CHANGED = "password_changed";

    // approval workflow
    public static final String REGISTRATION_APPROVED = "registration_approved";
    public static final String REGISTRATION_REJECTED = "registration_rejected";
    public static final String USER_STATUS_CHANGED = "user_status_changed";
    public static final String USER_UPDATED = "user_updated";

    // content
    public static final String DEPARTMENT_CREATED = "department_created";
    public static final String DEPARTMENT_UPDATED = "department_updated";
    public static final String FOLDER_CREATED = "folder_created";
    public static final String FOLDER_UPDATED = "folder_updated";
    public static final String FOLDER_DELETED = "folder_deleted";
    public static final String FILE_UPLOADED = "file_uploaded";
    public static final String FILE_PREVIEWED = "file_previewed";
    public static final String FILE_DOWNLOADED = "file_downloaded";
    public static final String FILE_REPLACED = "file_replaced";
    public static final String FILE_DELETED = "file_deleted";
    public static final String FILE_RESTORED = "file_restored";

    private AuditAction() {}
}
