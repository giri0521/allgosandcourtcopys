package com.allgos.dms.auth.repository;

import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.auth.entity.RegistrationStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegistrationRequestRepository extends JpaRepository<RegistrationRequest, UUID> {

    Page<RegistrationRequest> findByStatusOrderByCreatedAtDesc(RegistrationStatus status, Pageable pageable);

    Optional<RegistrationRequest> findFirstByUserIdOrderByCreatedAtDesc(UUID userId);

    /** Drives the "Pending Requests" tile on the admin dashboard. */
    long countByStatus(RegistrationStatus status);
}
