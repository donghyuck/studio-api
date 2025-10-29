package studio.echo.base.user.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.model.Role;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.base.user.service.BatchResult;
import studio.echo.base.user.web.dto.CreateUserRequest;
import studio.echo.base.user.web.dto.DisableUserRequest;
import studio.echo.base.user.web.dto.RoleDto;
import studio.echo.base.user.web.dto.UpdateUserRequest;
import studio.echo.base.user.web.dto.UserDto;
import studio.echo.base.user.web.mapper.ApplicationRoleMapper;
import studio.echo.base.user.web.mapper.ApplicationUserMapper;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

        private final ApplicationUserService<User, Role> userService;
        private final ApplicationUserMapper userMapper;
        private final ApplicationRoleMapper roleMapper;

        @PostMapping
        @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<ApiResponse<Long>> register(@Valid @RequestBody CreateUserRequest request,
                        HttpServletRequest http) {

                User created = userService.create(userMapper.toEntity(request));
                URI location = ServletUriComponentsBuilder.fromRequestUri(http).path("/{id}")
                                .buildAndExpand(created.getUserId()).toUri();
                return ResponseEntity.created(location).body(ApiResponse.ok(created.getUserId()));
        }

        @GetMapping
        @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<Page<UserDto>>> list(
                        @PageableDefault(size = 15, sort = "userId", direction = Sort.Direction.DESC) Pageable pageable) {
                Page<User> page = userService.findAll(pageable);
                Page<UserDto> dtoPage = page.map(userMapper::toDto);
                return ok(ApiResponse.ok(dtoPage));
        }

        @GetMapping("/find")
        @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<Page<UserDto>>> find(
                @RequestParam(value = "q", required = false) Optional<String> q,
                @RequestParam(value = "requireQuery" , required = false, defaultValue = "true" ) boolean requireQuery,
                @PageableDefault(size = 15, sort = "userId", direction = Sort.Direction.DESC) Pageable pageable) {

                if( q.isEmpty() && requireQuery )
                      return ok(ApiResponse.ok(Page.empty(pageable)));          
                Page<User> page;
                if (q.isEmpty()) {
                        page = userService.findAll(pageable);
                } else {
                        page = userService.findByNameOrUsernameOrEmail(q.get(), pageable);
                }
                Page<UserDto> dtoPage = page.map(userMapper::toDto);

                return ok(ApiResponse.ok(dtoPage));
        }

        @GetMapping("/{id}")
        @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<UserDto>> get(@PathVariable Long id) {
                var user = userService.get(id);
                return ResponseEntity.ok(ApiResponse.ok(userMapper.toDto(user)));
        }

        @PutMapping("/{id}")
        @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<ApiResponse<UserDto>> update(@PathVariable Long id, @RequestBody UpdateUserRequest req) {
                User updated = userService.update(id, u -> userMapper.updateEntityFromDto(req, u));
                return ResponseEntity.ok(ApiResponse.ok(userMapper.toDto(updated)));
        }

        @PostMapping("/{id}/enable")
        @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<Void> enable(@PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
                userService.enable(id, requireActor(actor));
                return ResponseEntity.noContent().build();
        }

        @PostMapping("/{id}/disable")
        @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<Void> disable(@PathVariable Long id,
                        @RequestBody(required = false) DisableUserRequest req,
                        @AuthenticationPrincipal UserDetails actor) {

                String reason = (req != null) ? req.getReason() : null;
                OffsetDateTime until = (req != null) ? req.getUntil() : null;
                boolean revoke = (req == null) || req.isRevokeTokens();
                boolean invalidate = (req == null) || req.isInvalidateSessions();
                boolean notify = (req != null) && req.isNotifyUser();

                userService.disable(id, requireActor(actor), reason, until, revoke, invalidate, notify);
                return ResponseEntity.noContent().build();
        }

        private String requireActor(UserDetails actor) {
                if (actor == null) {
                        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
                }
                return actor.getUsername();
        }

        // Roles --------------------------------------
        enum RoleScope {
                USER, GROUP, EFFECTIVE;

                public static RoleScope from(@Nullable String v) {
                        if (v == null || v.isEmpty())
                                return EFFECTIVE;
                        String s = v.trim().toLowerCase(Locale.ROOT);
                        if ("user".equals(s))
                                return USER;
                        if ("group".equals(s))
                                return GROUP;
                        if ("effective".equals(s) || "all".equals(s))
                                return EFFECTIVE; // all 하위호환
                        throw new IllegalArgumentException("Invalid scope: " + v);
                }
        }

        @GetMapping("/{id}/roles")
        @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<List<RoleDto>>> roles(
                        @PathVariable Long id,
                        @RequestParam(value = "by", required = false, defaultValue = "all") String by) {
                // by : user, group, all
                RoleScope scope = RoleScope.from(by);
                List<Role> roles;
                switch (scope) {
                        case USER:
                                roles = userService.getUserRoles(id);
                                break;
                        case GROUP:
                                roles = userService.getUserGroupsRoles(id);
                                break;
                        case EFFECTIVE:
                        default:
                                roles = userService.findEffectiveRoles(id).stream().collect(Collectors.toList());
                                break;
                }
                List<RoleDto> list = roleMapper.toDtos(roles);
                return ok(ApiResponse.ok(list));
        }

        @PostMapping("/{id}/roles")
        @PreAuthorize("@endpointAuthz.can('group','write')")
        public ResponseEntity<ApiResponse<Void>> updateUserRoles(@PathVariable Long id,
                        @RequestBody List<RoleDto> roles,
                        @AuthenticationPrincipal UserDetails actor) {
                if (actor == null) {
                        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
                }
                List<Long> desired = Optional.ofNullable(roles).orElseGet(Collections::emptyList)
                                .stream()
                                .map(RoleDto::getRoleId)
                                .filter(Objects::nonNull)
                                .distinct()
                                .collect(Collectors.toList());

                BatchResult result = userService.updateUserRolesBulk(id, desired, actor.getUsername());
                log.debug("batch : {}", result);
                return ok(ApiResponse.ok());
        }

}
