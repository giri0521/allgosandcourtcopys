package com.allgos.dms.notification.repository;

import com.allgos.dms.notification.entity.Notification;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    /** The unread badge on the notification bell. */
    long countByUserIdAndReadFalse(UUID userId);
}
