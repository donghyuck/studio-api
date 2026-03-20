package studio.one.aplication.security.auth.password;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.transaction.Transactional;

import org.springframework.security.crypto.password.PasswordEncoder;

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

    private static final Pattern TOKEN_PATTERN = Pattern.compile("^(\\d+)\\.([A-Za-z0-9_-]+)$");

    private final ApplicationUserService<User, Role> userService;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final Duration tokenTtl = Duration.ofMinutes(30);

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":security:password-reset-service";

    @Transactional
    public void requestReset(String email) {
        userService.findByEmail(email).ifPresent(user -> {
            createAndSendToken(user);
        });
    }

    private void createAndSendToken(User user) {
        // 토큰 생성
        String token = generateToken(user.getUserId());
        PasswordResetToken entity = new PasswordResetToken();
        entity.setUserId(user.getUserId());
        entity.setToken(passwordEncoder.encode(token));
        entity.setExpiresAt(Instant.now().plus(tokenTtl));
        entity.setUsed(false);
        tokenRepository.save(entity);
        // 메일 발송
        mailService.sendPasswordResetMail(user.getEmail(), token);
    }

    private String generateToken(Long userId) {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return userId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        Long userId = extractUserId(token);
        PasswordResetToken tokenEntity = tokenRepository.findActiveByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));
        if (!passwordEncoder.matches(token, tokenEntity.getToken())) {
            throw new IllegalArgumentException("Invalid token");
        }
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
        try {
            return tokenRepository.findActiveByUserId(extractUserId(token))
                    .filter(t -> passwordEncoder.matches(token, t.getToken()))
                    .filter(t -> !t.isUsed())
                    .filter(t -> t.getExpiresAt().isAfter(Instant.now()))
                    .isPresent();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private Long extractUserId(String token) {
        Matcher matcher = TOKEN_PATTERN.matcher(token == null ? "" : token);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid token");
        }
        return Long.valueOf(matcher.group(1));
    }
}
