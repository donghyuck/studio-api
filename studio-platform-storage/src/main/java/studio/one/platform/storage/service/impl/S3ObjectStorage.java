package studio.one.platform.storage.service.impl;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CommonPrefix;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import studio.one.platform.storage.service.BucketInfo;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectInfo;
import studio.one.platform.storage.service.ObjectStorageType;
import studio.one.platform.storage.service.PageResult;

@Slf4j
public class S3ObjectStorage implements CloudObjectStorage {

    private ObjectStorageType objectStorageType = ObjectStorageType.S3;

    private String name;

    private String regionName;

    private String endpoint;

    private S3Client s3Client;

    // S3 Presigner
    private S3Presigner presigner;

    public S3ObjectStorage(String name, S3Client client, String regionName, String endpoint) {
        this.name = name;
        this.s3Client = client;
        this.regionName = regionName;
        this.endpoint = endpoint;
        this.presigner = S3Presigner.builder()
                .region(Region.of(regionName))
                .credentialsProvider(ProfileCredentialsProvider.create())
                .build();
    }

    public S3ObjectStorage(String name, S3Client client, String regionName, String endpoint, S3Presigner presigner) {
        this.name = name;
        this.s3Client = client;
        this.regionName = regionName;
        this.endpoint = endpoint;
        this.presigner = presigner;
    }

    @PostConstruct
    public void initialize() {

    }

    public String name() {
        return name;
    }

    public ObjectStorageType getObjectStorageType() {
        return objectStorageType;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getRegion() {
        return regionName;
    }

    /**
     * Upload a file to a bucket.
     * 
     * @param bucketName
     * @param key        key of the object. (ex. myfile.txt, folder1/myfile.txt,
     *                   folder1/subfolder2/myfile.txt)
     * @param filePath   file path to upload
     */
    public void put(String bucketName, String key, String filePath) {
        Path path = Paths.get(filePath);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.putObject(putObjectRequest, RequestBody.fromFile(path));
    }

    @Override
    public void put(String bucket, String key, InputStream in, long contentLength, String contentType,
            Map<String, String> metadata) {
        key = normalizeKey(key);
        PutObjectRequest.Builder b = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentLength(contentLength);

        if (contentType != null && !contentType.isBlank()) {
            b = b.contentType(contentType);
        }
        if (metadata != null && !metadata.isEmpty()) {
            // AWS S3 메타데이터 키는 소문자 권장 (S3가 자동 소문자 처리)
            b = b.metadata(metadata);
        }
        s3Client.putObject(b.build(), RequestBody.fromInputStream(in, contentLength));
        log.debug("put: s3://{}/{} (len={}, contentType={})", bucket, key, contentLength, contentType);
    }

    /**
     * Download a file from a bucket.
     * 
     * @param bucketName
     * @param key          key of the object. (ex. myfile.txt, folder1/myfile.txt,
     *                     folder1/subfolder2/myfile.txt)
     * @param downloadPath file path to download the object (ex. if downloadPath is
     *                     /download and key is folder1/myfile.txt, downloadPath are
     *                     /download/myfile.txt)
     */
    public void download(String bucketName, String key, String downloadPath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.getObject(getObjectRequest, java.nio.file.Paths.get(downloadPath));
    }

    public void delete(String bucketName, String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        s3Client.deleteObject(deleteObjectRequest);
    }

    public ObjectInfo head(String bucketName, String key) {
        key = normalizeKey(key);
        var req = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        HeadObjectResponse h = s3Client.headObject(req);
        return ObjectInfo.builder()
                .objectStorageType(objectStorageType)
                .bucket(bucketName)
                .key(key)
                .name(extractName(key))
                .eTag(stripQuotes(h.eTag()))
                .metadata(h.metadata())
                .contentType(h.contentType())
                .size(h.contentLength())
                .createdDate(null)
                .modifiedDate(h.lastModified())
                .build();
    }

    @Override
    public URL presignedGetUrl(String bucketName, String objectKey, Duration ttl, String contentType,
            String contentDisposition) {

        GetObjectRequest.Builder b = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey);

        if (contentType != null && !contentType.isBlank()) {
            b = b.responseContentType(contentType);
        }
        if (contentDisposition != null && !contentDisposition.isBlank()) {
            b = b.responseContentDisposition(contentDisposition);
        }

        GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(b.build())
                .build();

        PresignedGetObjectRequest signed = presigner.presignGetObject(req);
        URL url = signed.url();
        log.debug("presigned PUT: s3://{}/{} (ttl={}s, contentType={}) -> {}",
                bucketName, objectKey, ttl.getSeconds(), contentType, url);
        return url;
    }

    @Override
    public URL presignedPut(String bucket, String key, Duration ttl, String contentType, String contentDisposition) {
        key = normalizeKey(key);
        PutObjectPresignRequest req = PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build())
                .build();
        PresignedPutObjectRequest signed = presigner.presignPutObject(req);
        URL url = signed.url();
        log.debug("presigned PUT: s3://{}/{} (ttl={}s, contentType={}) -> {}",
                bucket, key, ttl.getSeconds(), contentType, url);
        return url;
    }

    @Override
    public PageResult<ObjectInfo> list(String bucket, String prefix, String delimiter, String continuationToken,
            int maxKeys) {
        var req = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter(delimiter)
                .continuationToken(continuationToken)
                .maxKeys(maxKeys)
                .build();
        var res = s3Client.listObjectsV2(req);
        var items = res.contents().stream().map(o -> ObjectInfo.builder()
                .objectStorageType(objectStorageType)
                .bucket(bucket)
                .name(extractName(o.key()))
                .key(o.key())
                .size(o.size())
                .eTag(o.eTag())
                .modifiedDate(o.lastModified()) // Instant
                .build()).toList();
        var prefixes = res.commonPrefixes() == null ? List.<String>of()
                : res.commonPrefixes().stream().map(CommonPrefix::prefix).toList();
        return PageResult.<ObjectInfo>builder()
                .items(items)
                .commonPrefixes(prefixes)
                .nextToken(res.nextContinuationToken())
                .truncated(res.isTruncated())
                .build();
    }

    @Override
    public InputStream get(String bucket, String key) {
        key = normalizeKey(key); 
        ResponseInputStream<GetObjectResponse> is = s3Client.getObject(GetObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .build()); 
        return is;
    }


    public List<ObjectInfo> list(String bucketName, @Nullable String prefix) { 
        var b = ListObjectsV2Request.builder().bucket(bucketName);
        if (prefix != null && !prefix.isBlank()) 
            b.prefix(prefix); 
        ListObjectsV2Response res = s3Client.listObjectsV2(b.build());
        return res.contents().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .key(object.key())
                    .name(extractName(object.key()))
                    .size(object.size())
                    .modifiedDate(object.lastModified())
                    .eTag(object.eTag())
                    .build();
        }).toList();
    }

    @Override
    public Page<ObjectInfo> list(String bucketName, String prefix, Pageable pageable) {
        String continuationToken = null;
        List<ObjectInfo> objects = new ArrayList<>();
        int pageSize = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        int totalFetched = 0;
        boolean hasMoreResults = true;
        while (hasMoreResults && objects.size() < pageSize) {
            var b = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(pageSize)
                    .continuationToken(continuationToken);
            if (prefix != null && !prefix.isBlank()) 
                b.prefix(prefix);  
            ListObjectsV2Response res = s3Client.listObjectsV2(b.build());
            List<ObjectInfo> pageObjects = res.contents().stream().map(object -> {
                return ObjectInfo.builder()
                        .objectStorageType(objectStorageType)
                        .bucket(bucketName)
                        .key(object.key())
                        .name(extractName(object.key()))
                        .size(object.size())
                        .modifiedDate(object.lastModified())
                        .eTag(object.eTag())
                        .build();
            }).toList();
            totalFetched += pageObjects.size();
            if (totalFetched > offset) {
                int fromIndex = Math.max(0, offset - (totalFetched - pageObjects.size()));
                int toIndex = Math.min(pageObjects.size(), fromIndex + pageSize - objects.size());
                objects.addAll(pageObjects.subList(fromIndex, toIndex));
            }
            if (res.isTruncated()) {
                continuationToken = res.nextContinuationToken();
            } else {
                hasMoreResults = false;
            }
        }
        // 전체 객체 수 추정을 위해 마지막 continuationToken을 사용하지 않도록 한다.
        long total = hasMoreResults ? offset + objects.size() : offset + totalFetched;
        return new PageImpl<ObjectInfo>(objects, pageable, total);
    }

    public List<BucketInfo> listBuckets() {
        ListBucketsResponse response = s3Client.listBuckets();
        return response.buckets().stream().map(b -> {
            return BucketInfo.builder()
                    .objectStorageType(objectStorageType)
                    .providerId(this.name)
                    .bucket(b.name())
                    .createdDate(b.creationDate())
                    .build();
        }).toList();
    }
 
    /*
     * -----------------------------------------
     * Helpers
     * -----------------------------------------
     */
    private static String normalizeKey(String key) {
        if (key == null || key.isEmpty())
            return "";
        return key.startsWith("/") ? key.substring(1) : key;
    }

    static String extractName(String key) {
        if (key == null || key.isEmpty())
            return "";
        String s = key.endsWith("/") ? key.substring(0, key.length() - 1) : key;
        int i = s.lastIndexOf('/');
        return (i >= 0) ? s.substring(i + 1) : s;
    }

    private static String stripQuotes(String eTag) {
        if (eTag == null)
            return null;
        // S3 eTag는 보통 쿼트로 감싸져 옴
        if (eTag.length() >= 2 && eTag.startsWith("\"") && eTag.endsWith("\"")) {
            return eTag.substring(1, eTag.length() - 1);
        }
        return eTag;
    }
}