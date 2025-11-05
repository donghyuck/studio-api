package studio.one.platform.storage.service;

import java.net.URL;
import java.util.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The CloudObjectStorage interface provides methods for interacting with an object storage service.
 * @author  donghyuck, son
 * @since 2025-11-03
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-03  donghyuck, son: 최초 생성.
 * </pre>
 */

public interface CloudObjectStorage {

    public String name();
    /**
     * Returns the type of object storage.
     * support types : S3, GOOGLE, OCI
     *
     * @return The object storage type.
     */
    ObjectStorageType getObjectStorageType();
    
    String getEndpoint();
    
    String getRegion();

    /**
	 * Common API for Object Storage
	 *******************************/

    public void put(String bucketName, String key, String filePath);
    
    public void download(String bucketName, String key, String downloadPath);
    
    public void delete(String bucketName, String key);

    public URL generateAccessUrl(String bucketName, String objectKey, Date expiration) ;

    /**
     * get object metadata.
     * @param bucketName
     * @param key
     * @return ObjectInfo
     */
    public ObjectInfo head(String bucketName, String key);

    /**
     * List objects in a bucket.
     * @param bucketName
     * @return
     */
    public List<ObjectInfo> list(String bucketName);

        /**
     * List objects in a bucket.
     * @param bucketName
     * @param prefix
     * @return
     */
    public List<ObjectInfo> list(String bucketName, String prefix);

    /**
     * List objects in a bucket.
     * @param bucketName
     * @param pageable
     * @return
     */
    public Page<ObjectInfo> list(String bucketName, Pageable pageable);

    /**
     * List objects in a bucket.
     * @param bucketName
     * @param prefix
     * @param pageable
     * @return
     */
    public Page<ObjectInfo> list(String bucketName, String prefix, Pageable pageable);

    /**
     * List buckets.
     * @return
     */
    public List<BucketInfo> listBuckets();

}  