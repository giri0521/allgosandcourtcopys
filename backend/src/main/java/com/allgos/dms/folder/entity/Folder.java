package com.allgos.dms.folder.entity;

import com.allgos.dms.common.entity.BaseEntity;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A folder inside a department, optionally nested under another folder in the same department.
 *
 * <p>Folders carry no permissions: rule 2 says any active user may read and upload anywhere, so a
 * folder is an organising device rather than an access boundary. {@code fileCount} is denormalised
 * so a department listing does not need a count query per row.
 */
@Entity
@Table(name = "folders")
@Getter
@Setter
@NoArgsConstructor
public class Folder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** Null for a folder at the root of its department. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_folder_id")
    private Folder parent;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private FolderCategory category = FolderCategory.GENERAL;

    /** Live files only; a soft delete decrements it and a restore puts it back. */
    @Column(name = "file_count", nullable = false)
    private int fileCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
