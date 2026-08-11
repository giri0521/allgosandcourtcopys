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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByMobileNumber(String mobileNumber);

    boolean existsByMobileNumber(String mobileNumber);

    Page<User> findByStatus(UserStatus status, Pageable pageable);

    Page<User> findByRole(UserRole role, Pageable pageable);

    /** Recipients for the delete-with-reason fan-out and the pending-registration alert. */
    List<User> findByRoleAndStatus(UserRole role, UserStatus status);

    /** One count per tab on the admin members screen. */
    long countByStatus(UserStatus status);

    /**
     * Members list, filtered by the search box.
     *
     * <p>{@code term} is a pre-lowercased LIKE pattern, and is {@code %} when the box is empty, so
     * the search and the plain listing are the same query rather than two that can drift apart.
     */
    @Query("""
            select u from User u
            where lower(u.fullName) like :term or u.mobileNumber like :term
            """)
    Page<User> search(@Param("term") String term, Pageable pageable);

    @Query("""
            select u from User u
            where u.status = :status
              and (lower(u.fullName) like :term or u.mobileNumber like :term)
            """)
    Page<User> searchByStatus(
            @Param("status") UserStatus status, @Param("term") String term, Pageable pageable);
}
