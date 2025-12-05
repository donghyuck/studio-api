package studio.one.aplication.security.web.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import studio.one.aplication.security.auth.password.PasswordResetService;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequestMapping("${" + PropertyKeys.Security.Auth.PASSWORD_RESET + ".web.base-path:/api/auth/password-reset}")
@RequiredArgsConstructor
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/request")
    public ResponseEntity<ApiResponse<Void>> request(@RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.email());
        // 이메일 존재 여부는 노출하지 않고 항상 success
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("비밀번호 재설정 메일이 발송되었습니다(존재하는 이메일인 경우).")
                .build());
    }

    @GetMapping("/validate")
    public ResponseEntity<ApiResponse<Boolean>> validate(@RequestParam("token") String token) {
        boolean valid = passwordResetService.validateToken(token);
        return ResponseEntity.ok(ApiResponse.ok(
                valid ? "유효한 토큰입니다." : "만료되었거나 유효하지 않은 토큰입니다.",
                valid));
    }

    @PostMapping("/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(@RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.resetPassword(request.token(), request.password());
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .message("비밀번호가 성공적으로 변경되었습니다.")
                .build());
    }

    public record PasswordResetRequest(String email) {
    }

    public record PasswordResetConfirmRequest(String token, String password) {
    }

}
