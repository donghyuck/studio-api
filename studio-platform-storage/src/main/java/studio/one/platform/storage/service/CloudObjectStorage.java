package studio.one.platform.storage.service;

import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.Nullable;

/**
 * CloudObjectStorage 인터페이스는 다양한 클라우드 객체 스토리지(S3, OCI 등)와 상호작용하기 위한 공통 메서드를
 * 정의.이 인터페이스를 구현함으로써 스토리지 유형에 관계없이 일관된 방식으로 객체를 관리.
 *
 * 기본 정보 조회: 스토리지의 이름, 유형, 엔드포인트, 리전 정보를 제공.
 * 버킷 관리: 버킷 목록을 조회.
 * 객체 관리: 객체 업로드(put), 다운로드(download), 삭제(delete), 메타데이터 조회(head) 기능을 제공.
 * 객체 목록 조회: 특정 버킷 내의 객체 목록을 다양한 조건(prefix, delimiter, pagination)으로 조회. 커서
 * 기반 페이징과 페이지 번호 기반 페이징을 모두 지원.
 * Presigned URL 생성: 객체에 임시로 접근할 수 있는 URL을 생성.
 * 
 * @author donghyuck, son
 * @since 2025-11-03
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-03  donghyuck, son: 최초 생성.
 *          </pre>
 */

public interface CloudObjectStorage {

    /**
     * 스토리지 제공자의 고유 이름을 반환합니다.
     *
     * @return 스토리지 이름
     */
    public String name();

    /**
     * Returns the type of object storage.
     * support types : S3, OCI
     *
     * @return The object storage type.
     */
    ObjectStorageType getObjectStorageType();

    /**
     * 스토리지 서비스의 엔드포인트 주소를 반환합니다.
     *
     * @return 엔드포인트 URL 문자열
     */
    String getEndpoint();

    /**
     * 스토리지 서비스가 위치한 리전(region)을 반환합니다.
     *
     * @return 리전 이름
     */
    String getRegion();

    /**
     * Common API for Object Storage
     *******************************/

    /**
     * 스토리지에 존재하는 버킷 목록을 반환합니다.
     *
     * @return 버킷 정보 리스트
     */
    public List<BucketInfo> listBuckets();

    /**
     * 물리적 파일을 추가한다.
     * 
     * @param bucketName
     * @param key
     * @param filePath
     */
    public void put(String bucketName, String key, String filePath);

    /**
     * 물리적 파일을 추가한다.
     * @param bucket
     * @param key
     * @param in
     * @param contentLength
     * @param contentType
     * @param metadata
     */
    public void put(String bucket, String key, InputStream in, long contentLength, String contentType, Map<String,String> metadata);

    /**
     * 객체를 스토리지에서 로컬 파일 시스템으로 다운로드.
     *
     * @param bucketName   버킷 이름
     * @param key          다운로드할 객체의 키
     * @param downloadPath 다운로드 받을 로컬 파일 경로
     */
    public void download(String bucketName, String key, String downloadPath);
    
    InputStream get(String bucket, String key);
    
    /**
     * 스토리지에서 객체를 삭제.
     * 
     * @param bucketName 버킷 이름
     * @param key        삭제할 객체의 키
     */
    public void delete(String bucketName, String key);

    /**
     * 객체의 메타 데이터를 조회.
     * 
     * @param bucketName
     * @param key
     * @return ObjectInfo
     */
    public ObjectInfo head(String bucketName, String key);

    /**
     * 커서 기반 페이징을 사용하여 객체 목록을 조회합니다.
     * 
     * @param bucket            버킷 이름
     * @param prefix            객체 키 접두사 필터
     * @param delimiter         계층 구조를 나타내는 구분자 (예: "/")
     * @param continuationToken 이전 응답에서 받은 다음 페이지 시작 토큰
     * @param maxKeys           한 번에 가져올 최대 객체 수
     * @return 페이징 처리된 객체 목록 결과
     */
    PageResult<ObjectInfo> list(String bucket,
            @Nullable String prefix,
            @Nullable String delimiter,
            @Nullable String continuationToken,
            int maxKeys);

    /**
     * 객체 조회를 위한 사전 서명된 URL(Presigned URL)을 생성합니다.
     * 
     * @param bucket             버킷 이름
     * @param key                객체 키
     * @param ttl                URL의 유효 시간
     * @param contentType        응답의 Content-Type을 지정 (선택 사항)
     * @param contentDisposition 응답의 Content-Disposition을 지정 (예: 'inline',
     *                           'attachment; filename="filename.jpg"') (선택 사항)
     * @return 생성된 Presigned URL의 URI
     */
    URL presignedGetUrl(
            String bucket,
            String key,
            Duration ttl,
            @Nullable String contentType,
            @Nullable String contentDisposition);


    URL presignedPut(String bucket,
            String key,
            Duration ttl,
            @Nullable String contentType,
            @Nullable String contentDisposition);

    /**
     * List objects in a bucket.
     * 
     * @param bucketName
     * @param prefix
     * @return 객체 정보 리스트
     */
    public List<ObjectInfo> list(String bucketName, @Nullable String prefix);

    /**
     * List objects in a bucket.
     * 
     * @param bucketName
     * @param prefix
     * @param pageable
     * @return 페이징 처리된 객체 정보
     */
    public Page<ObjectInfo> list(String bucketName, @Nullable String prefix, Pageable pageable);

}