package com.allgos.dms.user.repository;

import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByRole(UserRole role, Pageable pageable);

    /** Recipients for the delete-with-reason fan-out and the pending-registration alert. */
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);
}
