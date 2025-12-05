package studio.one.aplication.security.auth.password;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.jwt.reset.domain.PasswordResetToken;
import studio.one.base.security.jwt.reset.persistence.PasswordResetTokenRepository;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.constant.ServiceNames;

@Slf4j
@RequiredArgsConstructor
public class PasswordResetService {

    private final ApplicationUserService<User, Role> userService;
    private final PasswordResetTokenRepository tokenRepository; 
    private final MailService mailService;
    private final Duration tokenTtl = Duration.ofMinutes(30);

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":security:password-reset-service";

    public void requestReset(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            createAndSendToken(user);
        });
    }

    private void createAndSendToken(User user) {
        // 토큰 생성
        String token = generateToken();
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUserId(user.getUserId());
        entity.setToken(token);
        entity.setExpiresAt(Instant.now().plus(tokenTtl));
        entity.setUsed(false);
        tokenRepository.save(entity);
        // 메일 발송
        mailService.sendPasswordResetMail(user.getEmail(), token);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public void resetPassword(String token, String newPassword) {
        PasswordResetToken tokenEntity = tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (tokenEntity.isUsed()) {
            throw new IllegalArgumentException("Token already used");
        }
        if (tokenEntity.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalArgumentException("Token expired");
        }
        User user = userService.get(tokenEntity.getUserId());
        userService.resetPassword(user.getUserId(), newPassword, user.getUsername(), "사용자에 의한 비밀번호 재성정");
        tokenEntity.setUsed(true);
        tokenRepository.save(tokenEntity);
    }

    public boolean validateToken(String token) {
        return tokenRepository.findByToken(token)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                .isPresent();
    }
}
