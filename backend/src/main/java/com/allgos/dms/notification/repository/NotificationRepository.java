package com.allgos.dms.notification.repository;

import com.allgos.dms.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** The "Unread" filter on the notifications screen. */
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** The unread badge on the notification bell. */
    long countByUserIdAndReadFalse(UUID userId);

    /**
     * Marks everything unread as read in one statement.
     *
     * <p>Loading a user's whole backlog to flip a boolean on each row would be pointless work, so
     * this is a bulk update. It bypasses the persistence context, which is why the caller must not
     * be holding stale Notification entities — none of them do.
     */
    @Modifying(clearAutomatically = true)
    @Query("update Notification n set n.read = true where n.user.id = :userId and n.read = false")
    int markAllReadFor(@Param("userId") UUID userId);
}
