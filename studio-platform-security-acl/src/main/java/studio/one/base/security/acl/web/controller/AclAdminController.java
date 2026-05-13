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
import studio.one.base.security.acl.application.command.AclClassCommand;
import studio.one.base.security.acl.application.command.AclEntryCommand;
import studio.one.base.security.acl.application.command.AclObjectIdentityCommand;
import studio.one.base.security.acl.application.command.AclSidCommand;
import studio.one.base.security.acl.application.result.AclClassResult;
import studio.one.base.security.acl.application.result.AclEntryResult;
import studio.one.base.security.acl.application.result.AclObjectIdentityResult;
import studio.one.base.security.acl.application.result.AclSidResult;
import studio.one.base.security.acl.application.usecase.AclAdministrationService;
import studio.one.base.security.acl.web.dto.response.AclClassDto;
import studio.one.base.security.acl.web.dto.request.AclClassRequest;
import studio.one.base.security.acl.web.dto.response.AclEntryDto;
import studio.one.base.security.acl.web.dto.request.AclEntryRequest;
import studio.one.base.security.acl.web.dto.response.AclObjectIdentityDto;
import studio.one.base.security.acl.web.dto.request.AclObjectIdentityRequest;
import studio.one.base.security.acl.web.dto.response.AclSidDto;
import studio.one.base.security.acl.web.dto.request.AclSidRequest;
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

    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<AclClassDto>>> classes() {
        var list = administrationService.listClasses().stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','create')")
    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclClassDto>> createClass(@RequestBody @Valid AclClassRequest request) {
        var created = administrationService.createClass(toCommand(request));
        return ok(ApiResponse.ok(toDto(created)));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','delete')")
    @DeleteMapping("/classes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteClass(@PathVariable("id") Long id) {
        administrationService.deleteClass(id);
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','delete')")
    @DeleteMapping("/classes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteClasses(@RequestBody List<Long> ids) {
        if (ids != null && !ids.isEmpty()) {
            ids.forEach(administrationService::deleteClass);
        }
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/sids")
    public ResponseEntity<ApiResponse<List<AclSidDto>>> sids() {
        var list = administrationService.listSids().stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @PostMapping("/sids")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclSidDto>> createSid(@RequestBody @Valid AclSidRequest request) {
        var created = administrationService.createSid(toCommand(request));
        return ok(ApiResponse.ok(toDto(created)));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','admin')")
    @DeleteMapping("/sids/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteSid(@PathVariable("id") Long id) {
        administrationService.deleteSid(id);
        return ok(ApiResponse.ok());
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/objects")
    public ResponseEntity<ApiResponse<List<AclObjectIdentityDto>>> objectIdentities() {
        var list = administrationService.listObjectIdentities().stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','create')")
    @PostMapping("/objects")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclObjectIdentityDto>> createObjectIdentity(@RequestBody @Valid AclObjectIdentityRequest request) {
        var created = administrationService.createObjectIdentity(toCommand(request));
        return ok(ApiResponse.ok(toDto(created)));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','delete')")
    @DeleteMapping("/objects/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteObjectIdentity(@PathVariable("id") Long id) {
        administrationService.deleteObjectIdentity(id);
        return ok(ApiResponse.ok());
    }


    @PreAuthorize("@endpointAuthz.can('security:acl','read')")
    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<List<AclEntryDto>>>  entries() {
        var list = administrationService.listEntries().stream().map(this::toDto).collect(java.util.stream.Collectors.toList());
        return ok(ApiResponse.ok(list));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','create')")
    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclEntryDto>>   createEntry(@RequestBody @Valid AclEntryRequest request) {
        var created = administrationService.createEntry(toCommand(request));
        return ok(ApiResponse.ok(toDto(created)));
    }

    @PreAuthorize("@endpointAuthz.can('security:acl','delete')")
    @DeleteMapping("/entries/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteEntry(@PathVariable("id") Long id) {
        administrationService.deleteEntry(id);
        return ok(ApiResponse.ok());
    }

    private AclClassCommand toCommand(AclClassRequest request) {
        return AclClassCommand.builder().className(request.getClassName()).build();
    }

    private AclSidCommand toCommand(AclSidRequest request) {
        return AclSidCommand.builder().principal(request.isPrincipal()).sid(request.getSid()).build();
    }

    private AclObjectIdentityCommand toCommand(AclObjectIdentityRequest request) {
        return AclObjectIdentityCommand.builder()
                .classId(request.getClassId())
                .objectIdentity(request.getObjectIdentity())
                .parentId(request.getParentId())
                .ownerSidId(request.getOwnerSidId())
                .entriesInheriting(request.isEntriesInheriting())
                .build();
    }

    private AclEntryCommand toCommand(AclEntryRequest request) {
        return AclEntryCommand.builder()
                .objectIdentityId(request.getObjectIdentityId())
                .sidId(request.getSidId())
                .mask(request.getMask())
                .aceOrder(request.getAceOrder())
                .granting(request.isGranting())
                .auditSuccess(request.isAuditSuccess())
                .auditFailure(request.isAuditFailure())
                .build();
    }

    private AclClassDto toDto(AclClassResult result) {
        return new AclClassDto(result.getId(), result.getClassName());
    }

    private AclSidDto toDto(AclSidResult result) {
        return new AclSidDto(result.getId(), result.isPrincipal(), result.getSid());
    }

    private AclObjectIdentityDto toDto(AclObjectIdentityResult result) {
        return new AclObjectIdentityDto(
                result.getId(),
                result.getClassId(),
                result.getClassName(),
                result.getObjectIdentity(),
                result.getParentId(),
                result.getOwnerSidId(),
                result.isEntriesInheriting());
    }

    private AclEntryDto toDto(AclEntryResult result) {
        return new AclEntryDto(
                result.getId(),
                result.getObjectIdentityId(),
                result.getObjectIdentity(),
                result.getSidId(),
                result.getSid(),
                result.getAceOrder(),
                result.getMask(),
                result.isGranting(),
                result.isAuditSuccess(),
                result.isAuditFailure());
    }
}
