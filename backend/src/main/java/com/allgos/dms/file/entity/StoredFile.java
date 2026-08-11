package com.allgos.dms.file.entity;

import com.allgos.dms.common.entity.BaseEntity;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.folder.entity.Folder;
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
 * A document. Named StoredFile rather than File so that nothing in this codebase has to disambiguate
 * it from {@link java.io.File}.
 *
 * <p>Deletion is soft: {@code deleted} is set, the bytes stay in object storage, and a
 * {@link FileDeletion} row records who removed it and why. That is what makes an admin restore
 * possible, and it is why every read path filters on {@code deleted = false} rather than assuming
 * the row is gone.
 */
@Entity
@Table(name = "files")
@Getter
@Setter
@NoArgsConstructor
public class StoredFile extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id", nullable = false)
    private Folder folder;

    /** Denormalised from the folder so department filtering and reporting need no join. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /** The sanitised original name, shown to users and used for downloads. */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /** The content type the bytes actually are, detected server-side — never the client's claim. */
    @Column(name = "file_type", nullable = false)
    private String fileType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Opaque object-storage key; see StorageService for the layout. */
    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Column
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(nullable = false)
    private int version = 1;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    /**
     * Who may change this document, in one place: the member who uploaded it, or an admin.
     *
     * <p>Governs both deleting (rule 4) and replacing. The two share a rule deliberately — an admin
     * who can delete a document and upload another in its place gains nothing from being refused
     * the single-step version, and a member who may not delete someone else's document must not be
     * able to overwrite it either.
     *
     * <p>Callers must ask this of the row loaded from the database, never of anything the client
     * sent.
     */
    public boolean canBeModifiedBy(User user) {
        return user.isAdmin() || uploadedBy.getId().equals(user.getId());
    }
}
