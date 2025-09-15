package studio.echo.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.entity.ApplicationRole;
import studio.echo.base.user.service.ApplicationRoleService;
import studio.echo.base.user.web.dto.ApplicationRoleDto;
import studio.echo.base.user.web.mapper.ApplicationRoleMapper;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/roles")
@RequiredArgsConstructor
@Slf4j
public class RoleController {

    private final ApplicationRoleService roleService;
    private final ApplicationRoleMapper mapper;

    @GetMapping
    @PreAuthorize("@endpointAuthz.can('role','read')")
    public ResponseEntity<ApiResponse<Page<ApplicationRoleDto>>> list(
            @PageableDefault(size = 15, sort = "roleId", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<ApplicationRole> page = roleService.findAll(pageable);
        Page<ApplicationRoleDto> dtoPage = page.map(mapper::toDto);
        return ok(ApiResponse.ok(dtoPage));
    }
}
