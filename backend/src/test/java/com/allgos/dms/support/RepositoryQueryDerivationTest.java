package com.allgos.dms.support;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.allgos.dms.auth.entity.RegistrationRequest;
import com.allgos.dms.file.entity.Download;
import com.allgos.dms.file.entity.Favorite;
import com.allgos.dms.file.entity.FileDeletion;
import com.allgos.dms.file.entity.StoredFile;
import com.allgos.dms.notification.entity.Notification;
import com.allgos.dms.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * Proves every derived repository method name resolves against its entity.
 *
 * <p>Spring Data builds these queries from the method name when the application context starts, so a
 * misspelled property — {@code FileDeletedFalse} against an entity whose field is not called
 * {@code deleted} — compiles perfectly and then fails at boot, or in an integration test that needs
 * Docker to run at all. On a machine without Docker that means the mistake is invisible until
 * someone else's build breaks.
 *
 * <p>{@link PartTree} is the same parser Spring Data itself uses, and it resolves property paths
 * against the domain type without a database. So these run in the ordinary unit suite and catch the
 * error where it is cheapest to fix.
 *
 * <p>Add a row here whenever a derived finder is added. Methods carrying an explicit
 * {@code @Query} are not derived and do not belong in this list — their SQL is checked by the
 * integration tests instead.
 */
class RepositoryQueryDerivationTest {

    @ParameterizedTest(name = "{1}.{0}")
    @CsvSource({
        // Phase 4 — discovery
        "findByUserIdAndFileDeletedFalseOrderByCreatedAtDesc, Favorite",
        "findByUserIdAndFileId, Favorite",
        "countByUserIdAndFileDeletedFalse, Favorite",
        "findByUserIdOrderByCreatedAtDesc, Download",
        "countByUserId, Download",
        "findByUserIdAndReadFalseOrderByCreatedAtDesc, Notification",
        "findByUserIdOrderByCreatedAtDesc, Notification",
        "countByUserIdAndReadFalse, Notification",
        "findByDeletedFalse, StoredFile",
        "countByDeletedFalse, StoredFile",

        // Earlier phases, so a rename to an entity is caught here too
        "findByIdAndDeletedFalse, StoredFile",
        "findByFolderIdAndDeletedFalse, StoredFile",
        "findByDepartmentIdAndDeletedFalse, StoredFile",
        "findByUploadedByIdAndDeletedFalse, StoredFile",
        "countByFolderIdAndDeletedFalse, StoredFile",
        "findByRestoredAtIsNull, FileDeletion",
        "findFirstByFileIdAndRestoredAtIsNullOrderByDeletedAtDesc, FileDeletion",
        "findByStatus, RegistrationRequest",
        "findFirstByUserIdOrderByCreatedAtDesc, RegistrationRequest",
        "countByStatus, RegistrationRequest",
        "findByMobileNumber, User",
        "findByRoleAndStatus, User",
        "countByStatus, User",
    })
    @DisplayName("the derived query resolves every property it names")
    void derivedQueryNamesResolve(String methodName, String entity) {
        assertThatCode(() -> new PartTree(methodName, entityFor(entity)))
                .as("%s does not resolve against %s", methodName, entity)
                .doesNotThrowAnyException();
    }

    private static Class<?> entityFor(String name) {
        return switch (name) {
            case "Favorite" -> Favorite.class;
            case "Download" -> Download.class;
            case "Notification" -> Notification.class;
            case "StoredFile" -> StoredFile.class;
            case "FileDeletion" -> FileDeletion.class;
            case "RegistrationRequest" -> RegistrationRequest.class;
            case "User" -> User.class;
            default -> throw new IllegalArgumentException("Unknown entity in test data: " + name);
        };
    }
}
