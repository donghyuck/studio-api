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

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.storage.service.BucketInfo;
import studio.one.platform.storage.service.CloudObjectStorage;
import studio.one.platform.storage.service.ObjectStorageRegistry;
import studio.one.platform.storage.service.ProviderCatalog;
import studio.one.platform.storage.web.dto.ProviderInfo;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Object Storage Controller 
 * 
 * @author  donghyuck, son
 * @since 2025-11-04
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-04  donghyuck, son: 최초 생성.
 * </pre>
 */

@RestController
@RequestMapping("${" + PropertyKeys.Cloud.PREFIX + ".storage.web.endpoint:/api/mgmt/objectstorage}")
@RequiredArgsConstructor
@Slf4j
public class ObjectStorageController {
    private final ObjectStorageRegistry objectStorageRegistry;
    private final ProviderCatalog catalog;
    private final I18n i18n;


    @GetMapping( value = "/providers" )
    public ResponseEntity<ApiResponse<List<ProviderInfo>>> list(@RequestParam(defaultValue="false") boolean health) {
        return ok(ApiResponse.ok(catalog.list(health)));
    }

    @GetMapping( value = "/providers/{name}/buckets" )
    public ResponseEntity<ApiResponse<List<BucketInfo>>> listBuckets(@PathVariable String name){
        CloudObjectStorage cos = objectStorageRegistry.get(name); 
        return ok(ApiResponse.ok(cos.listBuckets()));
    }

   // @GetMapping( value = "/providers/{name}/buckets/{bucketName}/objects" )
    
}
