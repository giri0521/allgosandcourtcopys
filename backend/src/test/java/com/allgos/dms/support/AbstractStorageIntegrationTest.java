package com.allgos.dms.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Base for tests that need real object storage as well as a real database.
 *
 * <p>The container is static and started once for the whole JVM, so every test class that extends
 * this shares a single MinIO — a container per class would add tens of seconds to the build for no
 * extra confidence.
 *
 * <p>Presigned URLs are the reason this cannot be a mock. Signing, expiry and content-disposition
 * are decided by the S3 protocol rather than by our code, so a stubbed client would prove only that
 * we called it.
 */
public abstract class AbstractStorageIntegrationTest extends AbstractIntegrationTest {

    protected static final String BUCKET = "allgos-documents";

    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-17T01-24-54Z");

    static {
        MINIO.start();
    }

    @Autowired protected S3Client s3Client;

    @DynamicPropertySource
    static void storageProperties(DynamicPropertyRegistry registry) {
        registry.add("app.storage.endpoint", MINIO::getS3URL);
        registry.add("app.storage.access-key", MINIO::getUserName);
        registry.add("app.storage.secret-key", MINIO::getPassword);
    }

    /** The compose file creates the bucket in development; a fresh container needs it made here. */
    protected void ensureBucket() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(BUCKET).build());
        } catch (NoSuchBucketException ex) {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        }
    }

    protected boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(BUCKET).key(key).build());
            return true;
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
