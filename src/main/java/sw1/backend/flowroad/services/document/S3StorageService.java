package sw1.backend.flowroad.services.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.text.Normalizer;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final Duration DEFAULT_DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    public StoredDocumentObject uploadDocument(
            MultipartFile file,
            String orgId,
            String processInstanceId,
            String nodeId,
            String documentRequirementId,
            int version) {
        validateBucket();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        String originalFileName = resolveOriginalFileName(file);
        String safeOriginalFileName = sanitizeFileName(originalFileName);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : DEFAULT_CONTENT_TYPE;
        String s3Key = buildS3Key(
                orgId,
                processInstanceId,
                nodeId,
                documentRequirementId,
                version,
                safeOriginalFileName);

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(contentType)
                .contentLength(file.getSize())
                .build();

        try {
            s3Client.putObject(
                    putObjectRequest,
                    RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException ex) {
            throw new UncheckedIOException("No se pudo leer el archivo para subirlo a S3.", ex);
        }

        return new StoredDocumentObject(
                bucket,
                s3Key,
                contentType,
                file.getSize(),
                originalFileName);
    }

    public URL generateDownloadUrl(String s3Key) {
        return generateDownloadUrl(s3Key, DEFAULT_DOWNLOAD_URL_TTL);
    }

    public URL generateDownloadUrl(String s3Key, Duration ttl) {
        validateBucket();

        if (!StringUtils.hasText(s3Key)) {
            throw new IllegalArgumentException("El s3Key es obligatorio.");
        }

        Duration safeTtl = ttl != null && !ttl.isNegative() && !ttl.isZero()
                ? ttl
                : DEFAULT_DOWNLOAD_URL_TTL;

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(safeTtl)
                .getObjectRequest(getObjectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);
        return presignedRequest.url();
    }

    public String buildS3Key(
            String orgId,
            String processInstanceId,
            String nodeId,
            String documentRequirementId,
            int version,
            String safeOriginalFileName) {
        return String.format(
                Locale.ROOT,
                "organizations/%s/process-instances/%s/nodes/%s/requirements/%s/v%d/%s-%s",
                sanitizePathSegment(orgId),
                sanitizePathSegment(processInstanceId),
                sanitizePathSegment(nodeId),
                sanitizePathSegment(documentRequirementId),
                Math.max(1, version),
                UUID.randomUUID(),
                sanitizeFileName(safeOriginalFileName));
    }

    public String sanitizeFileName(String fileName) {
        String candidate = StringUtils.hasText(fileName) ? fileName.trim() : "document";
        String normalized = Normalizer.normalize(candidate, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String sanitized = normalized
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");

        return StringUtils.hasText(sanitized) ? sanitized : "document";
    }

    private String sanitizePathSegment(String value) {
        String candidate = StringUtils.hasText(value) ? value.trim() : "unknown";
        String sanitized = candidate
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("[^A-Za-z0-9._-]", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^[-.]+|[-.]+$", "");

        return StringUtils.hasText(sanitized) ? sanitized : "unknown";
    }

    private String resolveOriginalFileName(MultipartFile file) {
        String originalFileName = file.getOriginalFilename();
        return StringUtils.hasText(originalFileName) ? originalFileName : "document";
    }

    private void validateBucket() {
        if (!StringUtils.hasText(bucket)) {
            throw new IllegalStateException("AWS_S3_BUCKET no esta configurado.");
        }
    }

    public record StoredDocumentObject(
            String bucket,
            String key,
            String contentType,
            long size,
            String originalFileName) {
    }
}
