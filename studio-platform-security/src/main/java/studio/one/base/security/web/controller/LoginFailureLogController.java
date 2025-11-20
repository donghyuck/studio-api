package studio.one.base.security.web.controller;

import static org.springframework.http.ResponseEntity.ok;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.format.annotation.DateTimeFormat.ISO;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.audit.service.LoginFailQuery;
import studio.one.base.security.audit.service.LoginFailureQueryService;
import studio.one.base.security.web.dto.LoginFailureLogDto;
import studio.one.base.security.web.mapper.LoginFailureLogMapper;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${" + PropertyKeys.Security.Audit.LOGIN_FAILURE + ".web.base-path:/api/mgmt/audit/login-failure-log}")
@Slf4j
public class LoginFailureLogController {

    private final LoginFailureQueryService service;
    private final LoginFailureLogMapper mapper;

    @GetMapping("")
    @PreAuthorize("@endpointAuthz.can('security:audit_login_failure','read')")
    public ResponseEntity<ApiResponse<Page<LoginFailureLogDto>>> grouped(
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(required = false) String usernameLike,
            @RequestParam(required = false) String ipEquals,
            @RequestParam(required = false) String failureType,
            Pageable pageable) {
        OffsetDateTime truncated = to;
        if (truncated != null)
            truncated.plusDays(1).truncatedTo(ChronoUnit.DAYS);
        var q = LoginFailQuery.builder()
                .from(from).to(truncated)
                .usernameLike(usernameLike)
                .ipEquals(ipEquals)
                .failureType(failureType)
                .build();
        return ok(ApiResponse.ok(service.find(q, pageable).map(mapper::toDto)));
    }

}
