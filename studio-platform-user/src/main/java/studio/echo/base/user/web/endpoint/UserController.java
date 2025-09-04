package studio.echo.base.user.web.endpoint;

import static org.springframework.http.ResponseEntity.ok;

import java.net.URI;
import java.time.OffsetDateTime;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.user.domain.entity.ApplicationUser;
import studio.echo.base.user.domain.model.User;
import studio.echo.base.user.service.ApplicationUserService;
import studio.echo.base.user.web.dto.ApplicationUserDto;
import studio.echo.base.user.web.dto.CreateUserRequest;
import studio.echo.base.user.web.dto.DisableUserRequest;
import studio.echo.base.user.web.dto.UpdateUserRequest;
import studio.echo.base.user.web.mapper.ApplicationUserMapper;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.BASE_PATH + ":/api/mgmt}/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

        private final ApplicationUserService<User> userService; 
        private final ApplicationUserMapper mapper;

        @PostMapping
        @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<ApiResponse<Long>> register(@Valid @RequestBody CreateUserRequest request,
                        HttpServletRequest http) {
 
                User created = userService.create(mapper.toEntity(request));
                URI location = ServletUriComponentsBuilder.fromRequestUri(http).path("/{id}")
                                .buildAndExpand(created.getUserId()).toUri();
                return ResponseEntity.created(location).body(ApiResponse.ok(created.getUserId()));
        }

        @GetMapping
         @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<Page<ApplicationUserDto>>> list(
                        @PageableDefault(size = 15, sort = "userId", direction = Sort.Direction.DESC) Pageable pageable) {
                Page<ApplicationUser> page = userService.findAll(pageable);
                Page<ApplicationUserDto> dtoPage = page.map(mapper::toDto);
                return ok(ApiResponse.ok(dtoPage));
        }

        @GetMapping("/{id}")
         @PreAuthorize("@endpointAuthz.can('user','read')")
        public ResponseEntity<ApiResponse<ApplicationUserDto>> get(@PathVariable Long id) {
                var user = userService.get(id);
                return ResponseEntity.ok(ApiResponse.ok(mapper.toDto(user)));
        }

        @PutMapping("/{id}")
         @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<ApiResponse<ApplicationUserDto>> update(@PathVariable Long id,
                        @RequestBody UpdateUserRequest req) { 
                ApplicationUser updated = userService.update(id, u -> mapper.updateEntityFromDto(req, u));
                return ResponseEntity.ok(ApiResponse.ok(mapper.toDto(updated)));
        }

        @PostMapping("/{id}/enable")
         @PreAuthorize("@endpointAuthz.can('user','write')")
        public ResponseEntity<Void> enable(@PathVariable Long id, @AuthenticationPrincipal UserDetails actor) {
                userService.enable(id, requireActor(actor) );  
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

                userService.disable(id, requireActor(actor) , reason, until, revoke, invalidate, notify);
                return ResponseEntity.noContent().build();
        }

        private String requireActor(UserDetails actor) {
                if (actor == null) { 
                        throw new AuthenticationCredentialsNotFoundException("No authenticated user");
                }
                return actor.getUsername();
        }
}

