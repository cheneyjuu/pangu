package com.pangu.infrastructure.oss;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import com.aliyun.oss.model.OSSObject;
import com.pangu.domain.repository.RepairEvidenceObjectStorage;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

@Component
public class AliyunRepairEvidenceObjectStorage implements RepairEvidenceObjectStorage {

    private final OSS client;
    private final String bucketName;

    public AliyunRepairEvidenceObjectStorage(
            @Value("${platform.ali-oss.endpoint}") String endpoint,
            @Value("${platform.ali-oss.region}") String region,
            @Value("${platform.ali-oss.bucket-name}") String bucketName,
            @Value("${platform.ali-oss.access-key-id}") String accessKeyId,
            @Value("${platform.ali-oss.access-key-secret}") String accessKeySecret) {
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        configuration.setSignatureVersion(SignVersion.V4);
        this.client = OSSClientBuilder.create()
                .endpoint(endpoint)
                .region(region)
                .credentialsProvider(new DefaultCredentialProvider(accessKeyId, accessKeySecret))
                .clientConfiguration(configuration)
                .build();
        this.bucketName = bucketName;
    }

    @Override
    public StoredObjectMetadata put(
            String objectKey, byte[] content, String contentType, String contentMd5Base64) {
        ObjectMetadata metadata = new ObjectMetadata();
        metadata.setContentLength(content.length);
        metadata.setContentType(contentType);
        metadata.setContentMD5(contentMd5Base64);
        PutObjectRequest request = new PutObjectRequest(
                bucketName, objectKey, new ByteArrayInputStream(content), metadata);
        PutObjectResult result = client.putObject(request);
        return new StoredObjectMetadata(content.length, contentType, result.getETag());
    }

    @Override
    public byte[] read(String objectKey) {
        try (OSSObject object = client.getObject(bucketName, objectKey)) {
            return object.getObjectContent().readAllBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("读取 OSS 对象失败", ex);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        return client.doesObjectExist(bucketName, objectKey);
    }

    @Override
    public URL createDownloadUrl(String objectKey, Duration validity) {
        return client.generatePresignedUrl(bucketName, objectKey, Date.from(Instant.now().plus(validity)));
    }

    @Override
    public URL createPreviewUrl(String objectKey, String originalFileName, Duration validity) {
        GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(
                bucketName, objectKey, HttpMethod.GET);
        request.setExpiration(Date.from(Instant.now().plus(validity)));
        ResponseHeaderOverrides responseHeaders = new ResponseHeaderOverrides();
        responseHeaders.setContentDisposition("inline; filename=\"preview\"; filename*=UTF-8''"
                + encodeFileName(originalFileName));
        request.setResponseHeaders(responseHeaders);
        return client.generatePresignedUrl(request);
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(bucketName, objectKey);
    }

    private String encodeFileName(String fileName) {
        return URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
    }

    @PreDestroy
    public void shutdown() {
        client.shutdown();
    }
}
