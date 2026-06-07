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
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

@Service
@RequiredArgsConstructor
public class S3StorageService {

    private static final Duration DEFAULT_DOWNLOAD_URL_TTL = Duration.ofMinutes(10);
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final int MAX_SLUG_LENGTH = 80;

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket:}")
    private String bucket;

    public StoredDocumentObject uploadDocument(
            MultipartFile file,
            DocumentStorageContext context,
            int version) {
        validateBucket();

        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        if (context == null) {
            throw new IllegalArgumentException("El contexto de almacenamiento es obligatorio.");
        }

        String originalFileName = resolveOriginalFileName(file);
        String safeOriginalFileName = sanitizeFileName(originalFileName);
        String contentType = StringUtils.hasText(file.getContentType())
                ? file.getContentType()
                : DEFAULT_CONTENT_TYPE;
        String s3Key = buildS3Key(
                context,
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

    public StoredDocumentObject uploadDocumentBytes(
            byte[] bytes,
            String originalFileName,
            String contentType,
            DocumentStorageContext context,
            int version) {
        validateBucket();

        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("El archivo es obligatorio.");
        }

        if (context == null) {
            throw new IllegalArgumentException("El contexto de almacenamiento es obligatorio.");
        }

        String resolvedOriginalFileName = StringUtils.hasText(originalFileName)
                ? originalFileName
                : "document";
        String resolvedContentType = StringUtils.hasText(contentType)
                ? contentType
                : DEFAULT_CONTENT_TYPE;
        String s3Key = buildS3Key(
                context,
                version,
                sanitizeFileName(resolvedOriginalFileName));

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .contentType(resolvedContentType)
                .contentLength((long) bytes.length)
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromBytes(bytes));

        return new StoredDocumentObject(
                bucket,
                s3Key,
                resolvedContentType,
                bytes.length,
                resolvedOriginalFileName);
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

    public DownloadedDocumentObject downloadDocument(String s3Key) {
        validateBucket();

        if (!StringUtils.hasText(s3Key)) {
            throw new IllegalArgumentException("El s3Key es obligatorio.");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucket)
                .key(s3Key)
                .build();

        ResponseBytes<GetObjectResponse> responseBytes = s3Client.getObjectAsBytes(getObjectRequest);
        GetObjectResponse response = responseBytes.response();
        return new DownloadedDocumentObject(
                responseBytes.asByteArray(),
                response.contentType(),
                response.contentLength());
    }

    public String buildS3Key(
            DocumentStorageContext context,
            int version,
            String safeOriginalFileName) {
        return String.format(
                Locale.ROOT,
                "flowroad/organizations/%s/%s/%s/%s/%s/v%d/%s-%s",
                buildNamedIdSegment(context.orgName(), "organization", context.orgId()),
                buildNamedIdSegment(context.diagramName(), "diagram", context.diagramId()),
                buildNamedIdSegment(context.clientName(), "client", context.clientId()),
                buildProcessSegment(context.processCode(), context.processInstanceId()),
                buildNamedIdSegment(context.requirementName(), "requirement", context.documentRequirementId()),
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

    public String slugify(String value, String fallback) {
        String candidate = StringUtils.hasText(value) ? value.trim() : fallback;
        String normalized = Normalizer.normalize(candidate, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String slug = normalized
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\\\/]+", "-")
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-+|-+$", "");

        if (!StringUtils.hasText(slug)) {
            slug = StringUtils.hasText(fallback) ? fallback : "item";
        }

        if (slug.length() > MAX_SLUG_LENGTH) {
            slug = slug.substring(0, MAX_SLUG_LENGTH)
                    .replaceAll("-+$", "");
        }

        return StringUtils.hasText(slug) ? slug : "item";
    }

    private String buildNamedIdSegment(String name, String fallbackSlug, String id) {
        String safeId = sanitizePathSegment(id);
        String slug = slugify(name, fallbackSlug);
        return slug + "-" + safeId;
    }

    private String buildProcessSegment(String processCode, String processInstanceId) {
        String safeProcessCode = sanitizePathSegment(processCode);
        String safeProcessInstanceId = sanitizePathSegment(processInstanceId);
        return safeProcessCode + "-" + safeProcessInstanceId;
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

    public record DownloadedDocumentObject(
            byte[] bytes,
            String contentType,
            Long contentLength) {
    }

    public record DocumentStorageContext(
            String orgId,
            String orgName,
            String diagramId,
            String diagramName,
            String clientId,
            String clientName,
            String processCode,
            String processInstanceId,
            String documentRequirementId,
            String requirementName) {
    }
}
