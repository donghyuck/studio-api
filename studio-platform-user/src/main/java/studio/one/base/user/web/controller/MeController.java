package studio.one.base.user.web.controller;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.base.user.web.dto.MeProfileDto;
import studio.one.base.user.web.mapper.ApplicationUserMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Features.User.Web.Self.PATH + ":/api/self}")
@RequiredArgsConstructor
@Slf4j
public class MeController {
    private final ApplicationUserService<User, Role> userService;
    private final ApplicationUserMapper mapper;

    @GetMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<MeProfileDto>> me(@AuthenticationPrincipal UserDetails principal) {
        String username = requirePrincipal(principal);
        User user = userService.findByUsername(username)
                .orElseThrow(
                        () -> new org.springframework.security.core.userdetails.UsernameNotFoundException(username));
        Set<Role> roles = userService.findEffectiveRoles(user.getUserId());
        return ResponseEntity.ok(ApiResponse.ok(toDto(user, roles)));
    }

    private String requirePrincipal(UserDetails principal) {
        if (principal == null || principal.getUsername() == null)
            throw new org.springframework.security.authentication.AuthenticationCredentialsNotFoundException(
                    "Unauthenticated");
        return principal.getUsername();
    }

    private MeProfileDto toDto(User u, Set<Role> roles) {
        return MeProfileDto.builder()
                .userId(u.getUserId())
                .username(u.getUsername())
                .name(u.getName())
                .email(u.getEmail())
                .enabled(u.isEnabled())
                .roles(roles == null ? List.of()
                        : roles.stream()
                                .filter(Objects::nonNull)
                                .map(Role::getName)
                                .filter(Objects::nonNull)
                                .sorted()
                                .collect(Collectors.toList()))
                .createdAt(toOffset(u.getCreationDate()))
                .updatedAt(toOffset(u.getModifiedDate()))
                .build();
    }

    private OffsetDateTime toOffset(Instant d) {
        return d == null ? null : d.atOffset(ZoneOffset.UTC);
    }
}
