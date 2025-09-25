package studio.echo.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;

import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.service.ApplicationRoleService;
import studio.echo.base.user.web.dto.RoleDto;
import studio.echo.base.user.web.mapper.ApplicationRoleMapper;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.annotation.Message;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final ApplicationRoleService<Role> roleService;
    private final ApplicationRoleMapper mapper;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<Page<RoleDto>>> list(
            @PageableDefault(size = 15, sort = "roleId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<Role> page = roleService.findAll(pageable);
        Page<RoleDto> dtoPage = page.map(mapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }

    @PostMapping
    @PreAuthorize("@endpointAuthz.can('role','write')")
    @Message( value="success.role.created.named" , args = {"#req.name"})
    public ResponseEntity<ApiResponse<RoleDto>> create(@Valid @RequestBody RoleDto req)  {
        Role role = mapper.toEntity(req); 
        Role saved = roleService.create(role); 
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(String.format("/roles/%s", saved.getRoleId())));
        return new ResponseEntity<>(ApiResponse.ok(mapper.toDto(saved)), headers, HttpStatus.CREATED);
    }  
    
    @GetMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<RoleDto>> get(@PathVariable Long id) {
        Role role = roleService.get(id);
        return ok(ApiResponse.ok(mapper.toDto(role)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('role','write')")
    @Message(value = "success.role.updated.named", args = {"#dto.name"})
    public ResponseEntity<ApiResponse<RoleDto>> update(@PathVariable Long id, @Valid @RequestBody RoleDto dto)  {
        Role updated = roleService.update(id, r-> mapper.updateEntityFromDto(dto, (ApplicationRole)r)  );
        return ok(ApiResponse.ok(mapper.toDto(updated)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@endpointAuthz.can('group','write')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        roleService.delete(id);
        return ok(ApiResponse.ok());
    }

}
