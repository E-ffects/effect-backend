package com.u1_2.effect.config.attachment;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.ContentStreamProvider;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Component
@Profile("prod")
class S3AttachmentStorage implements AttachmentStore {

    private static final String PREFIX = "attachments/";
    private final S3Client client;
    private final String bucket;

    S3AttachmentStorage(S3Client attachmentS3Client, @Value("${app.attachments.s3.bucket}") String bucket) {
        Assert.hasText(bucket, "운영 첨부용 S3 버킷이 필요함");
        this.client = attachmentS3Client;
        this.bucket = bucket;
    }

    @Override
    public void write(String key, Resource source, long size) throws IOException {
        // SDK가 업로드를 재시도할 때 파일을 처음부터 다시 읽고, 파일 전체를 메모리에 올리지 않음
        var streams = ContentStreamProvider.fromInputStreamSupplier(() -> {
            try {
                return source.getInputStream();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        try {
            client.putObject(request -> request.bucket(bucket).key(PREFIX + key),
                    RequestBody.fromContentProvider(streams, size, MediaType.APPLICATION_OCTET_STREAM_VALUE));
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    @Override
    public Optional<Resource> find(String key) {
        try {
            // Spring 응답을 쓸 때 S3 본문을 스트림으로 넘기고 응답을 닫을 때 같이 닫음
            return Optional.of(new InputStreamResource(client.getObject(request -> request.bucket(bucket).key(PREFIX + key))));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String key) {
        client.deleteObject(request -> request.bucket(bucket).key(PREFIX + key));
    }
}
