package com.allgos.dms.common.storage;

import com.allgos.dms.common.config.AppProperties;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * The S3 client, pointed at MinIO locally and at real S3 in production.
 *
 * <p>The only difference between the two is {@code app.storage.endpoint} and path-style access:
 * MinIO serves buckets as a path segment, whereas AWS uses a virtual host. Leaving the endpoint
 * blank selects the AWS default for the configured region.
 */
@Configuration
public class StorageConfig {

    @Bean
    public S3Client s3Client(AppProperties properties) {
        AppProperties.Storage storage = properties.storage();
        var builder = S3Client.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccess())
                        .build());

        if (hasEndpoint(storage)) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        return builder.build();
    }

    /**
     * Presigning is a separate client in the AWS SDK v2. It shares the endpoint so that the URLs it
     * signs are ones the browser can actually reach.
     */
    @Bean
    public S3Presigner s3Presigner(AppProperties properties) {
        AppProperties.Storage storage = properties.storage();
        var builder = S3Presigner.builder()
                .region(Region.of(storage.region()))
                .credentialsProvider(credentials(storage))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(storage.pathStyleAccess())
                        .build());

        if (hasEndpoint(storage)) {
            builder.endpointOverride(URI.create(storage.endpoint()));
        }
        return builder.build();
    }

    private static boolean hasEndpoint(AppProperties.Storage storage) {
        return storage.endpoint() != null && !storage.endpoint().isBlank();
    }

    private static StaticCredentialsProvider credentials(AppProperties.Storage storage) {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(storage.accessKey(), storage.secretKey()));
    }
}
