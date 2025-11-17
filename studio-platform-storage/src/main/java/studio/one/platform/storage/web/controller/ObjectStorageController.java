/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ObjectStorageController.java
 *      @date 2025
 *
 */

package studio.one.platform.storage.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.storage.service.BucketInfo;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.ProviderCatalog;
import studio.one.platform.storage.web.dto.ObjectInfoDto;
import studio.one.platform.storage.web.dto.ObjectListItemDto;
import studio.one.platform.storage.web.dto.ObjectListResponse;
import studio.one.platform.storage.web.dto.PresignedUrlDto;
import studio.one.platform.storage.web.dto.ProviderInfoDto;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Object Storage Controller
 * 
 * @author donghyuck, son
 * @since 2025-11-04
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-04  donghyuck, son: 최초 생성.
 *          </pre>
 */

@RestController
@RequestMapping("${" + PropertyKeys.Cloud.PREFIX + ".storage.web.endpoint:/api/mgmt/objectstorage}")
@RequiredArgsConstructor
@Slf4j
public class ObjectStorageController {
    private final ObjectStorageRegistry registry;
    private final ProviderCatalog catalog;
    private final I18n i18n;


    @GetMapping(value = "/providers")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','read')")
    public ResponseEntity<ApiResponse<List<ProviderInfoDto>>> listProviders(
            @RequestParam(defaultValue = "false") boolean health) {
        return ok(ApiResponse.ok(catalog.list(health)));
    }

    @GetMapping(value = "/providers/{providerId}/buckets")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','read')")
    public ResponseEntity<ApiResponse<List<BucketInfo>>> listBuckets(@PathVariable String providerId) {
        var storage = registry.get(providerId);
        return ok(ApiResponse.ok(storage.listBuckets()));
    }

    @GetMapping(value = "/providers/{providerId}/buckets/{bucket}/objects")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','read')")
    public ResponseEntity<ApiResponse<ObjectListResponse>> listObjects(
            @PathVariable String providerId,
            @PathVariable String bucket,
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false, defaultValue = "/") String delimiter,
            @RequestParam(required = false) String token,
            @RequestParam(required = false, defaultValue = "200") @Min(1) @Max(1000) int size) {

        var storage = registry.get(providerId);
        var page = storage.list(bucket,
                (prefix == null || prefix.isBlank()) ? null : prefix,
                (delimiter == null || delimiter.isBlank()) ? null : delimiter,
                (token == null || token.isBlank()) ? null : token,
                size);
        var items = page.getItems().stream().map(oi -> ObjectListItemDto.builder()
                .key(oi.getKey())
                .size(oi.getSize())
                .contentType(oi.getContentType())
                .eTag(oi.getETag())
                .lastModified(oi.getModifiedDate() != null ? oi.getModifiedDate() : oi.getCreatedDate())
                .folder(oi.isFolder())
                .build()).toList();
        var body = ObjectListResponse.builder()
                .bucket(bucket)
                .prefix(prefix)
                .delimiter(delimiter)
                .commonPrefixes(page.getCommonPrefixes())
                .items(items)
                .nextToken(page.getNextToken())
                .truncated(page.isTruncated())
                .build();
        return ok(ApiResponse.ok(body));
    }

    @GetMapping(value = "/providers/{providerId}/buckets/{bucket}/object")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','read')")
    public ResponseEntity<ApiResponse<ObjectInfoDto>> headObject(
            @PathVariable String providerId,
            @PathVariable String bucket,
            @RequestParam("key") String key) {
        var storage = registry.get(providerId);
        var info = storage.head(bucket, key);
        return ResponseEntity.ok(ApiResponse.ok(ObjectInfoDto.from(info)));
    }

    /**
     * (선택) GET 쿼리파라미터 버전
     * 예) GET
     * /.../presigned-get?key=path/to/a.txt&ttl=300&disposition=attachment&filename=abc.txt
     */
    @GetMapping("/providers/{providerId}/buckets/{bucket}/object:presigned-get")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','write')")
    public ResponseEntity<ApiResponse<PresignedUrlDto>> presignGet(
            @PathVariable String providerId,
            @PathVariable String bucket,
            @RequestParam("key") String key,
            @RequestParam(value = "ttl", required = false, defaultValue = "300") @Min(1) @Max(86400) long ttlSeconds,
            @RequestParam(value = "disposition", required = false) String disposition,
            @RequestParam(value = "filename", required = false) String filename,
            @RequestParam(value = "contentType", required = false) String contentType) {
        var storage = registry.get(providerId);
        var ttl = Duration.ofSeconds(ttlSeconds);
        String contentDisposition = buildContentDisposition(disposition, filename);
        var uri = storage.presignedGetUrl(
                bucket, key, ttl, contentType, contentDisposition);
        var dto = new PresignedUrlDto(uri.toString(), Instant.now().plus(ttl));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    @PostMapping("/providers/{providerId}/buckets/{bucket}/object:presigned-put")
    @PreAuthorize("@endpointAuthz.can('services:storage:cloud','write')")
    public ApiResponse<PresignedUrlDto> presignedPut(
            @PathVariable String providerId,
            @PathVariable String bucket,
            @Valid @RequestBody PresignedPutRequest req) {
        CloudObjectStorage storage = registry.get(providerId);
        Duration ttl = Duration.ofSeconds(req.getTtlSeconds() != null ? req.getTtlSeconds() : 300L);
        URL url = storage.presignedPut(bucket, req.getKey(), ttl, req.getContentType(), req.getDisposition());
        PresignedUrlDto dto = PresignedUrlDto.builder()
                .url(url.toString())
                .expiresAt(Instant.now().plus(ttl))
                .build();
        return ApiResponse.ok(dto);
    }

    private static @Nullable String buildContentDisposition(@Nullable String disposition,
            @Nullable String filename) {
        boolean hasDisp = disposition != null && !disposition.isBlank();
        boolean hasName = filename != null && !filename.isBlank();

        if (!hasDisp && !hasName)
            return null;
        String disp = hasDisp ? disposition : "attachment";
        if (!hasName)
            return disp;
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        return String.format("%s; filename*=UTF-8''%s", disp, encoded);
    }

    @Data
    public static class PresignedPutRequest {
        @NotBlank
        private String key;
        @Min(1)
        @Max(86400)
        private Long ttlSeconds; // nullable → 기본 300
        @NotBlank
        private String contentType; 
 
        String disposition;
    }
}