package com.allgos.dms.folder.entity;

/**
 * The document categories the office files under, matching the CHECK constraint on
 * {@code folders.category}.
 *
 * <p>GENERAL is the default for a folder that is simply a container — a year, a subject — rather
 * than one of the five document classes the client names in the specification.
 */
public enum FolderCategory {
    CONTRACT,
    GOVT_ORDER,
    COURT_ORDER,
    CIRCULAR,
    ACT_RULE,
    GENERAL
}
