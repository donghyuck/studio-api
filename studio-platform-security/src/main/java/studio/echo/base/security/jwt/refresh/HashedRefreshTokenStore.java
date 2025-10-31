/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file HashedRefreshTokenStore.java
 *      @date 2025
 *
 */

package studio.echo.base.security.jwt.refresh;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.echo.base.security.jwt.JwtConfig;

/**
 *
 * @author  donghyuck, son
 * @since 2025-09-30
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-30  donghyuck, son: 최초 생성.
 * </pre>
 */

@RequiredArgsConstructor
@Slf4j
public class HashedRefreshTokenStore implements RefreshTokenStore {
    
    private static final String D_STRING = ".";

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;    
    private final JwtConfig jwtConfig;
    
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();

    private static String[] split(String raw) {
        int dot = raw.indexOf(D_STRING);
        if (dot < 1)
            throw new IllegalArgumentException("invalid refresh token format");
        return new String[] { raw.substring(0, dot), raw.substring(dot + 1) };
    }

    @Override
    @Transactional
    public String mint(Long userId, Instant expiresAt) {
        String selector = UUID.randomUUID().toString();
        String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
        String hash = bcrypt.encode(verifier);

        RefreshToken e = RefreshToken.builder().userId(userId).selector(selector).verifierHash(hash).expiresAt(expiresAt).revoked(false).build();
        refreshTokenRepository.save(e);

        return selector + D_STRING + verifier;
    }

    @Override
    @Transactional
    public String mint(Long userId) {

        String selector = UUID.randomUUID().toString();
        String verifier = UUID.randomUUID().toString() + UUID.randomUUID();
        String hash = bcrypt.encode(verifier);

        Instant now = Instant.now(clock);
        Instant expiresAt = now.plus(jwtConfig.getRefreshTtl());
        RefreshToken e = RefreshToken.builder().userId(userId).selector(selector).verifierHash(hash).expiresAt(expiresAt).revoked(false).build();
        refreshTokenRepository.save(e);

        return selector + D_STRING + verifier;
    }

    @Override
    @Transactional
    public String rotate(String raw) {
        String[] parts = split(raw);
        String selector = parts[0];
        String verifier = parts[1];
        RefreshToken current = refreshTokenRepository.findBySelector(selector).orElseThrow(() -> new IllegalArgumentException("invalid refresh"));

        Instant now = Instant.now(clock);
        Instant effectiveExpiry = current.getExpiresAt().plus(jwtConfig.getRotationGrace());
         if (current.isRevoked() || effectiveExpiry.isBefore(now)) 
            throw new IllegalStateException("expired or revoked");
        if (!bcrypt.matches(verifier, current.getVerifierHash()))
            throw new IllegalArgumentException("invalid refresh");
        // 폐기하고 새 토큰 발급
        current.setRevoked(true);
        String nextRaw = mint(current.getUserId());
        // replacedBy 연결(선택)
        String[] nextParts = split(nextRaw);
        refreshTokenRepository.findBySelector(nextParts[0]).ifPresent(current::setReplacedBy);
        return nextRaw ;
    }

    @Override
    @Transactional
    public void revoke(String raw) {
        String[] parts = split(raw);
        refreshTokenRepository.findBySelector(parts[0]).ifPresent(e -> {
            e.setRevoked(true);
            refreshTokenRepository.save(e);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Long resolveUserId(String raw) {
        String[] parts = split(raw);
        String selector = parts[0];
        String verifier = parts[1];
        RefreshToken e = refreshTokenRepository.findBySelector(selector)
                .orElseThrow(() -> new IllegalArgumentException("invalid refresh"));
        if (e.isRevoked() || e.getExpiresAt().isBefore(Instant.now()))
            throw new IllegalStateException("expired or revoked");
        if (!bcrypt.matches(verifier, e.getVerifierHash()))
            throw new IllegalArgumentException("invalid refresh");
        return e.getUserId();
    }
}
