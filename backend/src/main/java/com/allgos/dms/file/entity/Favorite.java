package com.allgos.dms.file.entity;

import com.allgos.dms.common.entity.BaseCreatedEntity;
import com.allgos.dms.user.entity.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A document one user has starred.
 *
 * <p>Private to the user who created it — a star says nothing to anyone else, and no screen shows
 * whose favourites a document is on. The table's {@code UNIQUE (user_id, file_id)} is what makes
 * starring idempotent rather than something the service has to police.
 */
@Entity
@Table(name = "favorites")
@Getter
@Setter
@NoArgsConstructor
public class Favorite extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false)
    private StoredFile file;
}
