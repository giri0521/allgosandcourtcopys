package com.allgos.dms.notification.entity;

import com.allgos.dms.common.entity.BaseCreatedEntity;
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

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseCreatedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** See NotificationType for the values in use. */
    @Column(nullable = false)
    private String type;

    @Column(nullable = false)
    private String title;

    @Column
    private String body;

    /** Free-form pointer back to the subject, e.g. "file:{uuid}", for deep-linking from the bell. */
    @Column(name = "entity_ref")
    private String entityRef;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;
}
