package com.allgos.dms.admin.service;

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
import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.auth.entity.RegistrationStatus;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.notification.service.NotificationService;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The approval gate from the admin's side, in isolation.
 *
 * <p>These assert the decisions rather than the plumbing: which transitions are allowed, which are
 * refused and with what code, and that no transition happens silently — every one leaves an audit
 * entry and tells the person it affects. {@code AdminApprovalIT} proves the same rules over HTTP.
 */
@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock private RegistrationRequestRepository registrationRequestRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;

    private AdminUserService service;

    private User admin;
    private User applicant;
    private RegistrationRequest request;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(
                registrationRequestRepository, userRepository, auditService, notificationService);

        admin = user(UserRole.ADMIN, UserStatus.ACTIVE);
        applicant = user(UserRole.MEMBER, UserStatus.PENDING);

        request = new RegistrationRequest();
        request.setId(UUID.randomUUID());
        request.setUser(applicant);
        request.setRequestedRole(UserRole.MEMBER);
        request.setStatus(RegistrationStatus.PENDING);
    }

    @Nested
    @DisplayName("approve")
    class Approve {

        @Test
        void activatesTheAccountAndRecordsTheDecision() {
            givenTheRequestExists();

            var view = service.approve(request.getId(), admin);

            assertThat(applicant.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(request.getStatus()).isEqualTo(RegistrationStatus.APPROVED);
            assertThat(request.getReviewedBy()).isSameAs(admin);
            assertThat(request.getReviewedAt()).isNotNull();
            assertThat(view.accountStatus()).isEqualTo(UserStatus.ACTIVE);

            verify(auditService).record(
                    eq(admin), eq(AuditAction.REGISTRATION_APPROVED), eq("user"), eq(applicant.getId()), any());
            verify(notificationService).notifyRegistrationApproved(applicant);
        }

        @Test
        @DisplayName("leaves the role alone, so approving can never hand out admin access")
        void doesNotChangeTheRole() {
            givenTheRequestExists();

            service.approve(request.getId(), admin);

            assertThat(applicant.getRole()).isEqualTo(UserRole.MEMBER);
        }

        @ParameterizedTest
        @EnumSource(value = RegistrationStatus.class, names = {"APPROVED", "REJECTED"})
        void refusesToReviewTheSameRequestTwice(RegistrationStatus alreadyReviewed) {
            request.setStatus(alreadyReviewed);
            givenTheRequestExists();

            assertThatThrownBy(() -> service.approve(request.getId(), admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("REQUEST_ALREADY_REVIEWED");

            assertThat(applicant.getStatus()).isEqualTo(UserStatus.PENDING);
            verifyNoInteractions(notificationService);
        }

        @Test
        void reportsAnUnknownRequestAsNotFound() {
            UUID unknown = UUID.randomUUID();
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(registrationRequestRepository.findById(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.approve(unknown, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("NOT_FOUND");
        }
    }

    @Nested
    @DisplayName("reject")
    class Reject {

        @Test
        void marksTheAccountRejectedAndPassesTheReasonOn() {
            givenTheRequestExists();

            var view = service.reject(request.getId(), "  Not a member of this office  ", admin);

            assertThat(applicant.getStatus()).isEqualTo(UserStatus.REJECTED);
            assertThat(request.getStatus()).isEqualTo(RegistrationStatus.REJECTED);
            assertThat(view.reviewNote()).isEqualTo("Not a member of this office");

            verify(notificationService).notifyRegistrationRejected(applicant, "Not a member of this office");
            verify(auditService).record(
                    eq(admin), eq(AuditAction.REGISTRATION_REJECTED), eq("user"), eq(applicant.getId()), any());
        }
    }

    @Nested
    @DisplayName("enable and disable")
    class ChangeStatus {

        @Test
        void disablingNotifiesTheMemberAndIsAudited() {
            applicant.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            var view = service.changeStatus(applicant.getId(), UserStatus.INACTIVE, admin);

            assertThat(view.status()).isEqualTo(UserStatus.INACTIVE);
            assertThat(applicant.getStatus()).isEqualTo(UserStatus.INACTIVE);
            verify(notificationService).notifyAccountDisabled(applicant);
            verify(auditService).record(
                    eq(admin),
                    eq(AuditAction.USER_STATUS_CHANGED),
                    eq("user"),
                    eq(applicant.getId()),
                    eq(Map.of("from", "ACTIVE", "to", "INACTIVE")));
        }

        @Test
        @DisplayName("re-enabling does not tell the member their account was disabled")
        void enablingDoesNotSendTheDisabledNotice() {
            applicant.setStatus(UserStatus.INACTIVE);
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            service.changeStatus(applicant.getId(), UserStatus.ACTIVE, admin);

            assertThat(applicant.getStatus()).isEqualTo(UserStatus.ACTIVE);
            verifyNoInteractions(notificationService);
        }

        @Test
        @DisplayName("setting the status it already has changes nothing and audits nothing")
        void isANoOpWhenNothingChanges() {
            applicant.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            service.changeStatus(applicant.getId(), UserStatus.ACTIVE, admin);

            verifyNoInteractions(auditService, notificationService);
        }

        @Test
        @DisplayName("an admin cannot lock themselves out")
        void refusesToChangeTheCallersOwnStatus() {
            assertThatThrownBy(() -> service.changeStatus(admin.getId(), UserStatus.INACTIVE, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("CANNOT_MODIFY_SELF");

            verify(userRepository, never()).findById(any());
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED"})
        @DisplayName("an unreviewed account must go through the queue, not this screen")
        void refusesAnAccountThatWasNeverApproved(UserStatus current) {
            applicant.setStatus(current);
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            assertThatThrownBy(() -> service.changeStatus(applicant.getId(), UserStatus.ACTIVE, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("REGISTRATION_NOT_REVIEWED");

            assertThat(applicant.getStatus()).isEqualTo(current);
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED"})
        @DisplayName("only ACTIVE and INACTIVE can be set here")
        void refusesAStatusThatIsNotEnableOrDisable(UserStatus target) {
            assertThatThrownBy(() -> service.changeStatus(applicant.getId(), target, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("STATUS_NOT_ALLOWED");
        }
    }

    @Nested
    @DisplayName("changeRole")
    class ChangeRole {

        @Test
        @DisplayName("promoting retires the member's tokens so the new role cannot wait behind an old one")
        void promotingIsAuditedAnnouncedAndForcesReauthentication() {
            applicant.setStatus(UserStatus.ACTIVE);
            applicant.setTokenVersion(4);
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            var view = service.changeRole(applicant.getId(), UserRole.ADMIN, admin);

            assertThat(view.role()).isEqualTo(UserRole.ADMIN);
            assertThat(applicant.getRole()).isEqualTo(UserRole.ADMIN);
            assertThat(applicant.getTokenVersion()).isEqualTo(5);
            verify(notificationService).notifyRoleChanged(applicant, UserRole.ADMIN);
            verify(auditService).record(
                    eq(admin),
                    eq(AuditAction.USER_ROLE_CHANGED),
                    eq("user"),
                    eq(applicant.getId()),
                    eq(Map.of("from", "MEMBER", "to", "ADMIN")));
        }

        @Test
        @DisplayName("withdrawing access signs the former admin out immediately")
        void demotingIsAuditedAndForcesReauthentication() {
            applicant.setStatus(UserStatus.ACTIVE);
            applicant.setRole(UserRole.ADMIN);
            when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            service.changeRole(applicant.getId(), UserRole.MEMBER, admin);

            assertThat(applicant.getRole()).isEqualTo(UserRole.MEMBER);
            assertThat(applicant.getTokenVersion()).isEqualTo(1);
            verify(notificationService).notifyRoleChanged(applicant, UserRole.MEMBER);
            verify(auditService).record(
                    eq(admin),
                    eq(AuditAction.USER_ROLE_CHANGED),
                    eq("user"),
                    eq(applicant.getId()),
                    eq(Map.of("from", "ADMIN", "to", "MEMBER")));
        }

        @Test
        @DisplayName("setting the role it already has changes nothing, and does not sign anyone out")
        void isANoOpWhenNothingChanges() {
            applicant.setStatus(UserStatus.ACTIVE);
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            service.changeRole(applicant.getId(), UserRole.MEMBER, admin);

            assertThat(applicant.getTokenVersion()).isZero();
            verifyNoInteractions(auditService, notificationService);
        }

        @Test
        @DisplayName("an admin cannot demote themselves, which is the only way to strand an installation")
        void refusesToChangeTheCallersOwnRole() {
            assertThatThrownBy(() -> service.changeRole(admin.getId(), UserRole.MEMBER, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("CANNOT_MODIFY_SELF");

            verify(userRepository, never()).findById(any());
        }

        @ParameterizedTest
        @EnumSource(value = UserStatus.class, names = {"PENDING", "REJECTED", "INACTIVE"})
        @DisplayName("only an active account can be promoted")
        void refusesAnAccountThatIsNotActive(UserStatus current) {
            applicant.setStatus(current);
            when(userRepository.findById(applicant.getId())).thenReturn(Optional.of(applicant));

            assertThatThrownBy(() -> service.changeRole(applicant.getId(), UserRole.ADMIN, admin))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo("MEMBER_NOT_ACTIVE");

            assertThat(applicant.getRole()).isEqualTo(UserRole.MEMBER);
            assertThat(applicant.getTokenVersion()).isZero();
        }
    }

    // ------------------------------------------------------------------------ helpers

    private void givenTheRequestExists() {
        when(userRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
        when(registrationRequestRepository.findById(request.getId())).thenReturn(Optional.of(request));
    }

    private static User user(UserRole role, UserStatus status) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName(role == UserRole.ADMIN ? "System Administrator" : "Meena Rajan");
        user.setMobileNumber(role == UserRole.ADMIN ? "9999999999" : "9876543211");
        user.setRole(role);
        user.setStatus(status);
        return user;
    }
}
