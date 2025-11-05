package studio.one.platform.storage.service.impl;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import org.apache.commons.io.FileUtils;

import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageType;
import studio.one.platform.storage.service.BucketInfo;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectInfo;
import studio.one.platform.storage.service.ObjectStorageType;

import com.oracle.bmc.objectstorage.ObjectStorage;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.ListObjects;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.ListBucketsRequest;
import com.oracle.bmc.objectstorage.requests.ListObjectsRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.DeleteObjectResponse;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.ListBucketsResponse;
import com.oracle.bmc.objectstorage.responses.ListObjectsResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import com.oracle.bmc.objectstorage.transfer.UploadConfiguration;
import com.oracle.bmc.objectstorage.transfer.UploadManager;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadRequest;
import com.oracle.bmc.objectstorage.transfer.UploadManager.UploadResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class OciObjectStorage implements CloudObjectStorage {

    private ObjectStorageType objectStorageType = ObjectStorageType.OCI;

    private String name;

    private ObjectStorage client;

    private String namespaceName;

    private String compartmentId;

    private String regionName;

    private String endpoint;

    public OciObjectStorage(ObjectStorage client, String region) {
        this.client = client;
        this.regionName = region;
    }

    public String name(){return this.name;}
    
    public ObjectStorageType getObjectStorageType() {
        return objectStorageType;
    }

    public String getEndpoint() {
        return client.getEndpoint();
    }

    public String getRegion() {
        return regionName;
    }

    public ObjectInfo head(String bucketName, String key) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .objectName(key)
                .build();
        GetObjectResponse getObjectResponse = client.getObject(getObjectRequest);
        getObjectResponse.getOpcMeta();
        return ObjectInfo.builder()
                .objectStorageType(objectStorageType)
                .bucket(bucketName)
                .key(key)
                .name(getNameFromKey(key))
                .eTag(getObjectResponse.getETag())
                .metadata(getObjectResponse.getOpcMeta())
                .size(getObjectResponse.getContentLength())
                .modifiedDate(getObjectResponse.getLastModified())
                .build();
    }

    /**
     * 객체를 업로드합니다.
     * 파일 크기가 100MB 이상인 경우 Multipart Upload 기술을 사용 성능을 개선합니다.
     * 
     * @param bucketName 버킷 이름
     * @param key        객체 키
     * @param filePath   파일 경로
     */
    public void put(String bucketName, String key, String filePath) {
        Path path = Paths.get(filePath);
        File file = path.toFile();
        try (FileInputStream fileStream = new FileInputStream(file)) {
            long filesize = FileUtils.sizeOf(file);
            // 100MB 이상인 경우 Multipart Upload 사용
            if (filesize > 100 * 1024 * 1024) {
                // Multipart Upload
                UploadManager uploadManager = new UploadManager(client,
                        UploadConfiguration.builder().allowMultipartUploads(true).allowParallelUploads(true).build());
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(key)
                        .build();
                UploadRequest uploadRequest = UploadRequest.builder(fileStream, file.length()).build(putObjectRequest);
                UploadResponse uploadResponse = uploadManager.upload(uploadRequest);
            } else {
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .namespaceName(namespaceName)
                        .bucketName(bucketName)
                        .objectName(key)
                        .contentLength(filesize)
                        .putObjectBody(fileStream)
                        .build();
                PutObjectResponse response = client.putObject(putObjectRequest);
            }
        } catch (IOException e) {
            log.error("Failed to upload object to OCI: {}", e.getMessage(), e);
        }
    }

    public void delete(String bucketName, String key) {
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .objectName(key)
                .build();
        DeleteObjectResponse deleteObjectResponse = client.deleteObject(deleteObjectRequest);
    }

    public void download(String bucketName, String key, String downloadPath) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .objectName(key)
                .build();
        GetObjectResponse getObjectResponse = client.getObject(getObjectRequest);
        InputStream inputStream = getObjectResponse.getInputStream();
        File downloadFile = Paths.get(downloadPath).toFile();
        try (OutputStream outputStream = new FileOutputStream(downloadFile)) {
            FileUtils.copyInputStreamToFile(inputStream, downloadFile);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        }
    }

    public List<BucketInfo> listBuckets() {
        if (!StringUtils.hasText(compartmentId))
            log.warn("compartmenId is not set.");

        if (!StringUtils.hasText(namespaceName))
            log.warn("namespaceName is not set.");
        
        if (!StringUtils.hasText(compartmentId) || !StringUtils.hasText(namespaceName))
            return Collections.<BucketInfo>emptyList();
        
        var req = ListBucketsRequest.builder()
                .namespaceName(namespaceName)
                .compartmentId(compartmentId)
                .build();
        ListBucketsResponse listBucketsResponse = client.listBuckets(req);
        return listBucketsResponse.getItems().stream().map(b -> {
            return BucketInfo.builder()
                    .objectStorageType(objectStorageType)
                    .providerId(this.name)
                    .bucket(b.getName())
                    .createdDate(b.getTimeCreated() != null ? b.getTimeCreated().toInstant() : null)
                    .build();
        }).toList();
    }

    @Override
    public List<ObjectInfo> list(String bucketName) {
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        ListObjects listObjects = listObjectsResponse.getListObjects();
        List<ObjectInfo> list = listObjects.getObjects().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .name(getNameFromKey(object.getName()))
                    .key(object.getName())
                    .size(object.getSize())
                    .eTag(object.getEtag())
                    .modifiedDate(object.getTimeModified())
                    .createdDate(object.getTimeCreated())
                    .bucket(bucketName)
                    .build();
        }).collect(Collectors.toList());
        return list;
    }

    @Override
    public List<ObjectInfo> list(String bucketName, String prefix) {
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .prefix(prefix)
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        ListObjects listObjects = listObjectsResponse.getListObjects();
        List<ObjectInfo> list = listObjects.getObjects().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .name(getNameFromKey(object.getName()))
                    .key(object.getName())
                    .size(object.getSize())
                    .eTag(object.getEtag())
                    .modifiedDate(object.getTimeModified())
                    .createdDate(object.getTimeCreated())
                    .bucket(bucketName)
                    .build();
        }).collect(Collectors.toList());
        return list;
    }

    public Page<ObjectInfo> list(String bucketName, Pageable pageable) {
        String nextPageToken = null;
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .limit(pageable.getPageSize())
                .start(nextPageToken)
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        List<ObjectInfo> objects = listObjectsResponse.getListObjects().getObjects().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .name(getNameFromKey(object.getName()))
                    .key(object.getName())
                    .size(object.getSize())
                    .eTag(object.getEtag())
                    .modifiedDate(object.getTimeModified())
                    .createdDate(object.getTimeCreated())
                    .bucket(bucketName)
                    .build();
        }).collect(Collectors.toList());
        return new PageImpl<>(objects, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                listObjectsResponse.getListObjects().getObjects().size());
    }


    @Override
    public Page<ObjectInfo> list(String bucketName, String prefix, Pageable pageable) {
        String nextPageToken = null;
        ListObjectsRequest listObjectsRequest = ListObjectsRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .prefix(prefix)
                .limit(pageable.getPageSize())
                .start(nextPageToken)
                .build();
        ListObjectsResponse listObjectsResponse = client.listObjects(listObjectsRequest);
        List<ObjectInfo> objects = listObjectsResponse.getListObjects().getObjects().stream().map(object -> {
            return ObjectInfo.builder()
                    .objectStorageType(objectStorageType)
                    .bucket(bucketName)
                    .name(getNameFromKey(object.getName()))
                    .key(object.getName())
                    .size(object.getSize())
                    .eTag(object.getEtag())
                    .modifiedDate(object.getTimeModified())
                    .createdDate(object.getTimeCreated())
                    .bucket(bucketName)
                    .build();
        }).collect(Collectors.toList());
        return new PageImpl<>(objects, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize()),
                listObjectsResponse.getListObjects().getObjects().size());
    }

    public URL generateAccessUrl(String bucketName, String objectKey, Date expiration) {

        CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                .name(objectKey)
                .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                .objectName(objectKey)
                .timeExpires(expiration)
                .build();

        CreatePreauthenticatedRequestRequest request = CreatePreauthenticatedRequestRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .createPreauthenticatedRequestDetails(details)
                .opcClientRequestId(UUID.randomUUID().toString())
                .build();

        CreatePreauthenticatedRequestResponse response = client.createPreauthenticatedRequest(request);

        try {
            return new URL(endpoint + response.getPreauthenticatedRequest().getAccessUri());
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private String getNameFromKey(String key) {
        return Paths.get(key).getFileName().toString();
    }
}
