package com.allgos.dms.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.allgos.dms.audit.entity.AuditAction;
import com.allgos.dms.audit.service.AuditService;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.user.dto.ProfileRequests;
import com.allgos.dms.user.entity.User;
import com.allgos.dms.user.entity.UserRole;
import com.allgos.dms.user.entity.UserStatus;
import com.allgos.dms.user.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * What a user may and may not change about themselves.
 *
 * <p>The boundary is the point of these: a member may correct their name and password, and there is
 * deliberately no path from here to their department, role or status. A real BCrypt encoder is used
 * rather than a mock, because "the current password must actually match" is the assertion.
 */
@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final String CURRENT_PASSWORD = "Str0ngPassword!";

    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private ProfileService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new ProfileService(userRepository, passwordEncoder, auditService);

        user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Meena Rajan");
        user.setMobileNumber("9876543210");
        user.setEmail("meena@example.gov.in");
        user.setDesignation("Section Officer");
        user.setRole(UserRole.MEMBER);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(CURRENT_PASSWORD));

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
    }

    @Nested
    @DisplayName("editing a profile")
    class Update {

        @Test
        void appliesTheChangesAndAuditsOnlyWhatMoved() {
            var view = service.update(
                    user, new ProfileRequests.UpdateProfile("  Meena R  ", "meena@example.gov.in", "Superintendent", null));

            assertThat(user.getFullName()).isEqualTo("Meena R");
            assertThat(user.getDesignation()).isEqualTo("Superintendent");
            assertThat(view.fullName()).isEqualTo("Meena R");

            ArgumentCaptor<Map<String, Object>> changes = captureAuditMetadata();
            // The email was submitted unchanged, so it is not in the record of what changed.
            assertThat(changes.getValue()).containsOnlyKeys("fullName", "designation");
        }

        @Test
        @DisplayName("a blank email clears it rather than storing an empty string")
        void treatsBlankAsCleared() {
            service.update(user, new ProfileRequests.UpdateProfile("Meena Rajan", "   ", "Section Officer", null));

            assertThat(user.getEmail()).isNull();
        }

        @Test
        @DisplayName("submitting the form unchanged writes no audit row")
        void doesNotAuditANoOp() {
            service.update(
                    user,
                    new ProfileRequests.UpdateProfile("Meena Rajan", "meena@example.gov.in", "Section Officer", null));

            verifyNoInteractions(auditService);
        }

        @Test
        @DisplayName("nothing here can change a role, a status or a department")
        void cannotEscalate() {
            service.update(user, new ProfileRequests.UpdateProfile("Meena R", null, null, null));

            assertThat(user.getRole()).isEqualTo(UserRole.MEMBER);
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getDepartment()).isNull();
        }
    }

    @Nested
    @DisplayName("changing a password")
    class ChangePassword {

        @Test
        @DisplayName("every session ends, including the one making the request")
        void bumpsTheTokenVersion() {
            int before = user.getTokenVersion();

            service.changePassword(user, new ProfileRequests.ChangePassword(CURRENT_PASSWORD, "N3wPassword!"));

            assertThat(user.getTokenVersion()).isEqualTo(before + 1);
            assertThat(passwordEncoder.matches("N3wPassword!", user.getPasswordHash())).isTrue();
            verify(auditService).record(
                    eq(user), eq(AuditAction.PASSWORD_CHANGED), eq("user"), eq(user.getId()), any());
        }

        @Test
        @DisplayName("a wrong current password is refused and recorded durably")
        void refusesAWrongCurrentPassword() {
            String originalHash = user.getPasswordHash();

            assertThatThrownBy(() -> service.changePassword(
                            user, new ProfileRequests.ChangePassword("not-my-password", "N3wPassword!")))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("CURRENT_PASSWORD_INVALID");

            assertThat(user.getPasswordHash()).isEqualTo(originalHash);
            assertThat(user.getTokenVersion()).isZero();
            // Durable: the exception rolls the request back, and a rejected attempt on a signed-in
            // account is exactly what a security review needs to still be there.
            verify(auditService).recordDurable(
                    eq(user), eq(AuditAction.LOGIN_FAILED), eq("user"), eq(user.getId()), any());
        }

        @Test
        @DisplayName("re-setting the same password is refused, so the change is never a no-op")
        void refusesAnUnchangedPassword() {
            assertThatThrownBy(() -> service.changePassword(
                            user, new ProfileRequests.ChangePassword(CURRENT_PASSWORD, CURRENT_PASSWORD)))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("PASSWORD_UNCHANGED");

            assertThat(user.getTokenVersion()).isZero();
        }

        @Test
        @DisplayName("an account with no password yet cannot use this path")
        void refusesWhenNoPasswordIsSet() {
            user.setPasswordHash(null);

            assertThatThrownBy(() -> service.changePassword(
                            user, new ProfileRequests.ChangePassword("anything", "N3wPassword!")))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("CURRENT_PASSWORD_INVALID");

            verify(auditService, never()).record(any(), eq(AuditAction.PASSWORD_CHANGED), any(), any(), any());
        }
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> captureAuditMetadata() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(auditService).record(eq(user), eq(AuditAction.USER_UPDATED), eq("user"), eq(user.getId()),
                captor.capture());
        return captor;
    }
}
