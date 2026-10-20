package com.rag.assistant.thirdparty.minio;

import cn.hutool.core.util.IdUtil;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class MinioService {

    private final MinioClient minioClient;
    private final MinioClient publicMinioClient;
    private final MinioConfig minioConfig;

    public MinioService(MinioClient minioClient,
                        @Qualifier("publicMinioClient") MinioClient publicMinioClient,
                        MinioConfig minioConfig) {
        this.minioClient = minioClient;
        this.publicMinioClient = publicMinioClient;
        this.minioConfig = minioConfig;
    }

    public String upload(MultipartFile file, String originalName) {
        try {
            createBucketIfNotExists();

            String suffix = "";
            int dotIndex = originalName.lastIndexOf(".");
            if (dotIndex > 0) {
                suffix = originalName.substring(dotIndex);
            }
            String objectName = IdUtil.fastSimpleUUID() + suffix;

            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build();

            minioClient.putObject(args);

            return objectName;
        } catch (Exception e) {
            log.error("MinIO upload failed", e);
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        }
    }

    public InputStream download(String objectName) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build();
            return minioClient.getObject(args);
        } catch (Exception e) {
            log.error("MinIO download failed: {}", objectName, e);
            throw new RuntimeException("文件下载失败: " + e.getMessage());
        }
    }

    public void delete(String objectName) {
        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build();
            minioClient.removeObject(args);
        } catch (Exception e) {
            log.error("MinIO delete failed: {}", objectName, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage());
        }
    }

    public String getFileUrl(String objectName) {
        try {
            GetPresignedObjectUrlArgs args = GetPresignedObjectUrlArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .method(Method.GET)
                    .expiry(7, TimeUnit.DAYS)
                    .build();
            return publicMinioClient.getPresignedObjectUrl(args);
        } catch (Exception e) {
            String base = minioConfig.getPublicEndpoint();
            if (base == null || base.isBlank()) {
                base = minioConfig.getEndpoint();
            }
            return base.replaceAll("/+$", "") + "/" + minioConfig.getBucket() + "/" + objectName;
        }
    }

    private void createBucketIfNotExists() {
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(minioConfig.getBucket()).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(minioConfig.getBucket()).build()
                );
                log.info("Created MinIO bucket: {}", minioConfig.getBucket());
            }
        } catch (Exception e) {
            log.error("Failed to check/create MinIO bucket", e);
        }
    }
}
