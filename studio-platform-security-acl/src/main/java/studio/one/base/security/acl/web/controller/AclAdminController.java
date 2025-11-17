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
 *      @file AclAdminController.java
 *      @date 2025
 *
 */

package studio.one.base.security.acl.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.base.security.acl.service.AclAdministrationService;
import studio.one.base.security.acl.web.dto.AclClassDto;
import studio.one.base.security.acl.web.dto.AclClassRequest;
import studio.one.base.security.acl.web.dto.AclEntryDto;
import studio.one.base.security.acl.web.dto.AclEntryRequest;
import studio.one.base.security.acl.web.dto.AclObjectIdentityDto;
import studio.one.base.security.acl.web.dto.AclObjectIdentityRequest;
import studio.one.base.security.acl.web.dto.AclSidDto;
import studio.one.base.security.acl.web.dto.AclSidRequest;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Controller that exposes CRUD endpoints for ACL metadata. Enable it via
 * {@code studio.security.acl.admin.enabled=true}.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.security.acl.web.base-path:/api/mgmt/acl}/admin")
public class AclAdminController {

    private final AclAdministrationService administrationService;

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<AclClassDto>>> classes() {
        var list = administrationService.listClasses();
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclClassDto>> createClass(@RequestBody @Valid AclClassRequest request) {
        var created = administrationService.createClass(request);
        return ok(ApiResponse.ok(created));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @DeleteMapping("/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteClass(@PathVariable("id") Long id) {
        administrationService.deleteClass(id);
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @GetMapping("/sids")
    public ResponseEntity<ApiResponse<List<AclSidDto>>> sids() {
        var list = administrationService.listSids();
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/sids")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclSidDto>> createSid(@RequestBody @Valid AclSidRequest request) {
        var created = administrationService.createSid(request);
        return ok(ApiResponse.ok(created));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @DeleteMapping("/sids/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteSid(@PathVariable("id") Long id) {
        administrationService.deleteSid(id);
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @GetMapping("/objects")
    public ResponseEntity<ApiResponse<List<AclObjectIdentityDto>>> objectIdentities() {
        var list = administrationService.listObjectIdentities();
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/objects")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclObjectIdentityDto>> createObjectIdentity(@RequestBody @Valid AclObjectIdentityRequest request) {
        var created = administrationService.createObjectIdentity(request);
        return ok(ApiResponse.ok(created));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @DeleteMapping("/objects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteObjectIdentity(@PathVariable("id") Long id) {
        administrationService.deleteObjectIdentity(id);
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<List<AclEntryDto>>>  entries() {
        var list = administrationService.listEntries();
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclEntryDto>>   createEntry(@RequestBody @Valid AclEntryRequest request) {
        var created = administrationService.createEntry(request);
        return ok(ApiResponse.ok(created));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteEntry(@PathVariable("id") Long id) {
        administrationService.deleteEntry(id);
        return ok(ApiResponse.ok());
    }
}
