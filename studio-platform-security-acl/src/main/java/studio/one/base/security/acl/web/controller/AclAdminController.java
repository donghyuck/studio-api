package studio.one.base.security.acl.web.controller;

import java.util.List;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.security.acl.web.base-path:/api/mgmt/acl}/admin")
public class AclAdminController {

    private final AclAdministrationService administrationService;

    @GetMapping("/classes")
    public ResponseEntity<ApiResponse<List<AclClassDto>>> classes() {
        var list = administrationService.listClasses();
        return ResponseEntity.ok(ApiResponse.ok(list)); 
    }

    @PostMapping("/classes")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclClassDto>>  createClass(@RequestBody @Valid AclClassRequest request) {
        var created =  administrationService.createClass(request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @GetMapping("/sids")
    public ResponseEntity<ApiResponse<List<AclSidDto>>> sids() {
        var list = administrationService.listSids();
        return ResponseEntity.ok(ApiResponse.ok(list)); 
    }

    @PostMapping("/sids")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclSidDto>> createSid(@RequestBody @Valid AclSidRequest request) {
        var created = administrationService.createSid(request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @GetMapping("/object-identities")
    public ResponseEntity<ApiResponse<List<AclObjectIdentityDto>>> objectIdentities() {
        var list = administrationService.listObjectIdentities();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/object-identities")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclObjectIdentityDto>>  createObjectIdentity(@RequestBody @Valid AclObjectIdentityRequest request) {
        var created = administrationService.createObjectIdentity(request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }

    @GetMapping("/entries")
    public ResponseEntity<ApiResponse<List<AclEntryDto>>> entries() {
        List<AclEntryDto> list = administrationService.listEntries();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<ApiResponse<AclEntryDto>> createEntry(@RequestBody @Valid AclEntryRequest request) {
        AclEntryDto created = administrationService.createEntry(request);
        return ResponseEntity.ok(ApiResponse.ok(created));
    }
}
