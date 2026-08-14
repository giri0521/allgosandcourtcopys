package com.allgos.dms.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.allgos.dms.admin.dto.AdminReports.MonthlyActivity;
import com.allgos.dms.audit.repository.AuditLogRepository;
import com.allgos.dms.auth.repository.RegistrationRequestRepository;
import com.allgos.dms.common.config.AppProperties;
import com.allgos.dms.common.exception.ApiException;
import com.allgos.dms.department.entity.Department;
import com.allgos.dms.department.repository.DepartmentRepository;
import com.allgos.dms.file.repository.DownloadRepository;
import com.allgos.dms.file.repository.FileDeletionRepository;
import com.allgos.dms.file.repository.StoredFileRepository;
import com.allgos.dms.file.repository.StoredFileRepository.DepartmentCount;
import com.allgos.dms.file.repository.StoredFileRepository.MonthCount;
import com.allgos.dms.folder.repository.FolderRepository;
import com.allgos.dms.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The shaping a report does after the database has answered.
 *
 * <p>Two of these would be invisible in a screenshot and wrong in a meeting: a month with no
 * activity has to appear as a zero rather than vanish, and a department with no activity has to
 * appear at all. Both are the kind of omission that reads as "nothing happened" when it actually
 * means "nothing was asked".
 */
@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private UserRepository userRepository;
    @Mock private DepartmentRepository departmentRepository;
    @Mock private FolderRepository folderRepository;
    @Mock private StoredFileRepository fileRepository;
    @Mock private DownloadRepository downloadRepository;
    @Mock private FileDeletionRepository deletionRepository;
    @Mock private RegistrationRequestRepository registrationRequestRepository;

    private AdminReportService service;

    @BeforeEach
    void setUp() {
        AppProperties properties =
                new AppProperties("Asia/Kolkata", null, null, null, null, null, null);

        service = new AdminReportService(
                auditLogRepository,
                userRepository,
                departmentRepository,
                folderRepository,
                fileRepository,
                downloadRepository,
                deletionRepository,
                registrationRequestRepository,
                properties);
    }

    @Test
    @DisplayName("a month with no activity is a zero, not a missing column")
    void fillsEmptyMonths() {
        givenNoDepartments();
        // January and March have rows; February has none, which is what the database returns.
        when(fileRepository.countUploadsByMonth(any(), any()))
                .thenReturn(List.of(month("2026-01", 4), month("2026-03", 2)));
        when(downloadRepository.countByMonth(any(), any())).thenReturn(List.of(month("2026-02", 7)));

        var report = service.report(instant(2026, 1, 1), instant(2026, 4, 1));

        assertThat(report.monthly())
                .extracting(MonthlyActivity::month)
                .containsExactly("2026-01", "2026-02", "2026-03", "2026-04");

        assertThat(report.monthly())
                .containsExactly(
                        new MonthlyActivity("2026-01", 4, 0),
                        new MonthlyActivity("2026-02", 0, 7),
                        new MonthlyActivity("2026-03", 2, 0),
                        new MonthlyActivity("2026-04", 0, 0));
    }

    @Test
    @DisplayName("a department with no activity still appears, showing zeroes")
    void includesQuietDepartments() {
        Department busy = department("Department of Agriculture");
        Department quiet = department("Department of Fisheries");
        when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(busy, quiet));

        when(fileRepository.countLiveByDepartment()).thenReturn(List.of(count(busy.getId(), 9)));
        when(fileRepository.countUploadsByDepartment(any(), any())).thenReturn(List.of(count(busy.getId(), 3)));
        when(downloadRepository.countByDepartment(any(), any())).thenReturn(List.of(count(busy.getId(), 5)));
        givenNoMonthlyRows();

        var report = service.report(instant(2026, 1, 1), instant(2026, 2, 1));

        assertThat(report.departments()).hasSize(2);
        // Busiest first, which is the order a reader wants.
        assertThat(report.departments().getFirst().departmentName()).isEqualTo("Department of Agriculture");
        assertThat(report.departments().getFirst().documents()).isEqualTo(9);
        assertThat(report.departments().getFirst().uploads()).isEqualTo(3);
        assertThat(report.departments().getFirst().downloads()).isEqualTo(5);

        var fisheries = report.departments().getLast();
        assertThat(fisheries.departmentName()).isEqualTo("Department of Fisheries");
        assertThat(fisheries.documents()).isZero();
        assertThat(fisheries.uploads()).isZero();
    }

    @Test
    @DisplayName("a period that ends before it starts is refused rather than returning nothing")
    void refusesABackwardsRange() {
        assertThatThrownBy(() -> service.report(instant(2026, 3, 1), instant(2026, 1, 1)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo("RANGE_INVALID");
    }

    @Test
    @DisplayName("omitting the period reports the last twelve months")
    void defaultsToAYear() {
        givenNoDepartments();
        givenNoMonthlyRows();

        var report = service.report(null, null);

        // Twelve months inclusive of the current one.
        assertThat(report.monthly()).hasSize(12);
        assertThat(report.from()).isBefore(report.to());
    }

    // ------------------------------------------------------------------------ helpers

    private void givenNoDepartments() {
        when(departmentRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());
        lenient().when(fileRepository.countLiveByDepartment()).thenReturn(List.of());
        lenient().when(fileRepository.countUploadsByDepartment(any(), any())).thenReturn(List.of());
        lenient().when(downloadRepository.countByDepartment(any(), any())).thenReturn(List.of());
    }

    private void givenNoMonthlyRows() {
        lenient().when(fileRepository.countUploadsByMonth(any(), any())).thenReturn(List.of());
        lenient().when(downloadRepository.countByMonth(any(), any())).thenReturn(List.of());
    }

    private static Instant instant(int year, int month, int day) {
        return ZonedDateTime.of(year, month, day, 0, 0, 0, 0, IST).toInstant();
    }

    private static Department department(String name) {
        Department department = new Department();
        department.setId(UUID.randomUUID());
        department.setName(name);
        return department;
    }

    private static DepartmentCount count(UUID departmentId, long total) {
        return new DepartmentCount() {
            @Override
            public UUID getDepartmentId() {
                return departmentId;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private static MonthCount month(String month, long total) {
        return new MonthCount() {
            @Override
            public String getMonth() {
                return month;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }
}
