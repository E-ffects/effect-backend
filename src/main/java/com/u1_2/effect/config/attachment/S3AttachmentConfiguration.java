package com.u1_2.effect.config.attachment;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("prod")
class S3AttachmentConfiguration {

    @Bean(destroyMethod = "close")
    S3Client attachmentS3Client(@Value("${app.attachments.s3.region}") String region,
                                @Value("${app.attachments.s3.endpoint:}") String endpoint) {
        // 운영 자격 증명은 SDK 기본 체인에서 Pod에 부여된 AWS 역할을 사용함
        var builder = S3Client.builder().region(Region.of(region))
                .overrideConfiguration(config -> config.apiCallAttemptTimeout(Duration.ofSeconds(10))
                        .apiCallTimeout(Duration.ofSeconds(30)));
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
        }
        return builder.build();
    }
}
