package studio.one.platform.storage.service.impl;



import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListBucketsResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;

import studio.one.platform.storage.service.BucketInfo;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectInfo;
import studio.one.platform.storage.service.ObjectStorageType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class S3ObjectStorage implements CloudObjectStorage {

    private ObjectStorageType objectStorageType = ObjectStorageType.S3 ;
    
    private String name;

    private String regionName ;

    private String endpoint; 

    private S3Client s3Client;

    // S3 Presigner
    private S3Presigner presigner;

    public S3ObjectStorage(String name,  S3Client client, String regionName, String endpoint) {
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

    public String name(){
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
     * @param bucketName
     * @param key key of the object.  (ex. myfile.txt, folder1/myfile.txt, folder1/subfolder2/myfile.txt)
     * @param filePath file path to upload
     */
    public void put(String bucketName, String key, String filePath) {
        Path path = Paths.get(filePath);
        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();   
        PutObjectResponse res = s3Client.putObject(putObjectRequest, RequestBody.fromFile(path))
        ;
    }

    /**
     * Download a file from a bucket.
     * @param bucketName
     * @param key key of the object.  (ex. myfile.txt, folder1/myfile.txt, folder1/subfolder2/myfile.txt)
     * @param downloadPath file path to download the object (ex. if downloadPath is /download and key is folder1/myfile.txt, downloadPath are /download/myfile.txt)
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
        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();
        HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);
        return ObjectInfo.builder()
        .objectStorageType(objectStorageType)
        .bucket(bucketName)
        .key(key)
        .name(getNameFromKey(key))
        .eTag(headObjectResponse.eTag())
        .metadata(headObjectResponse.metadata())
        .size(headObjectResponse.contentLength())
        .modifiedDate(getDateFromInstant(headObjectResponse.lastModified()))
        .build();
    }

    public List<ObjectInfo> list(String bucketName) {
        ListObjectsV2Request listObjectsReq = ListObjectsV2Request.builder().bucket(bucketName).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsReq);
        return response.contents().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .key(object.key())
                    .name(getNameFromKey(object.key()))
                    .size(object.size())
                    .modifiedDate(getDateFromInstant(object.lastModified()))
                    .eTag(object.eTag())
                    .build();
        }).collect(Collectors.toList());
    }

    public List<ObjectInfo> list(String bucketName, String prefix) {
        ListObjectsV2Request listObjectsReq = ListObjectsV2Request.builder().bucket(bucketName).prefix(prefix).build();
        ListObjectsV2Response response = s3Client.listObjectsV2(listObjectsReq);
        return response.contents().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .key(object.key())
                    .name(getNameFromKey(object.key()))
                    .size(object.size())
                    .modifiedDate(getDateFromInstant(object.lastModified()))
                    .eTag(object.eTag())
                    .build();
        }).collect(Collectors.toList());
    }

    private Date getDateFromInstant(java.time.Instant instant) {
        return Date.from(instant);
    }
    private String getNameFromKey(String key) {
        return Paths.get(key).getFileName().toString();
    }

    /**
     * List objects in a bucket.
     * Limits : does not support total object count in bucket.
     */
    public Page<ObjectInfo> list(String bucketName, Pageable pageable) {
        String continuationToken = null;
        List<ObjectInfo> objects = new ArrayList<>();
        int pageSize = pageable.getPageSize();
        int offset = (int) pageable.getOffset();
        int totalFetched = 0;
        boolean hasMoreResults = true;
        while (hasMoreResults && objects.size() < pageSize) {
            ListObjectsV2Request listObjectsReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .maxKeys(pageSize)
                    .continuationToken(continuationToken)
                    .build();
            ListObjectsV2Response listObjectsRes = s3Client.listObjectsV2(listObjectsReq);
            List<ObjectInfo> pageObjects = listObjectsRes.contents().stream().map(object -> {
                return ObjectInfo.builder()
                .objectStorageType(objectStorageType)
                .bucket(bucketName)
                .key(object.key() )
                .name(getNameFromKey(object.key()))
                .size(object.size())
                .modifiedDate(getDateFromInstant(object.lastModified()))
                .eTag(object.eTag())
                .build();
            }).collect(Collectors.toList());
            totalFetched += pageObjects.size();
            if (totalFetched > offset) {
                int fromIndex = Math.max(0, offset - (totalFetched - pageObjects.size()));
                int toIndex = Math.min(pageObjects.size(), fromIndex + pageSize - objects.size());
                objects.addAll(pageObjects.subList(fromIndex, toIndex));
            }
            if (listObjectsRes.isTruncated()) {
                continuationToken = listObjectsRes.nextContinuationToken();
            } else {
                hasMoreResults = false;
            }
        }
        // 전체 객체 수 추정을 위해 마지막 continuationToken을 사용하지 않도록 한다.
        long total = hasMoreResults ? offset + objects.size() : offset + totalFetched;
        return new PageImpl<ObjectInfo>(objects, pageable, total);
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
            ListObjectsV2Request listObjectsReq = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .maxKeys(pageSize)
                    .continuationToken(continuationToken)
                    .build();
            ListObjectsV2Response listObjectsRes = s3Client.listObjectsV2(listObjectsReq);
            List<ObjectInfo> pageObjects = listObjectsRes.contents().stream().map(object -> {
                return ObjectInfo.builder()
                .objectStorageType(objectStorageType)
                .bucket(bucketName)
                .key(object.key() )
                .name(getNameFromKey(object.key()))
                .size(object.size())
                .modifiedDate(getDateFromInstant(object.lastModified()))
                .eTag(object.eTag())
                .build();
            }).collect(Collectors.toList());
            totalFetched += pageObjects.size();
            if (totalFetched > offset) {
                int fromIndex = Math.max(0, offset - (totalFetched - pageObjects.size()));
                int toIndex = Math.min(pageObjects.size(), fromIndex + pageSize - objects.size());
                objects.addAll(pageObjects.subList(fromIndex, toIndex));
            }
            if (listObjectsRes.isTruncated()) {
                continuationToken = listObjectsRes.nextContinuationToken();
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

    public URL generateAccessUrl(String bucketName, String objectKey, Date expiration) {
        // GetObjectRequest 생성
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .build();
        // 현재 시간과 expiration 시간의 차이를 계산하여 Duration 생성
        Instant now = Instant.now();
        Instant expirationInstant = expiration.toInstant();
        Duration duration = Duration.between(now, expirationInstant);

        // PresignedGetObjectRequest 생성
        GetObjectPresignRequest getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(duration) // 1시간 유효
                .getObjectRequest(getObjectRequest)
                .build();
        // Pre-signed URL 생성
        PresignedGetObjectRequest presignedGetObjectRequest = presigner.presignGetObject(getObjectPresignRequest);
        return presignedGetObjectRequest.url();
    }

}
