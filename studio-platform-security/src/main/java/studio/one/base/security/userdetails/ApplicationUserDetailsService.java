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
 *      @file ApplicationUserDetailsService.java
 *      @date 2025
 *
 */

package studio.one.base.security.userdetails;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.base.security.authentication.lock.service.AccountLockService;
import studio.one.base.user.domain.model.Role;
import studio.one.base.user.domain.model.User;
import studio.one.base.user.exception.UserNotFoundException;
import studio.one.base.user.service.ApplicationUserService;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;

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

@Service(ServiceNames.USER_DETAILS_SERVICE)
@RequiredArgsConstructor
@Slf4j
public class ApplicationUserDetailsService<T extends User, R extends Role> implements UserDetailsService, UserIdToUsername {
 
    private final ApplicationUserService<T, R> userService;
    private final ObjectProvider<AccountLockService> accountLockService; 

    @Value("${"+ PropertyKeys.Security.PREFIX +".efault-roles:#{null}}")  // 프로퍼티 없으면 ROLE_USER 기본값
    private String[] defaultRoles;

    public String usernameOf(long userId){
        User exist = userService.get(userId);
        return exist.getUsername();
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        T user = userService.findByUsername(username)
            .orElseThrow(() -> UserNotFoundException.of(username)); 
        log.debug("Found user {}", user.getUserId(), user.getUsername());
        List<GrantedAuthority> authorities = getFinalUserAuthority(user);
        return new ApplicationUserDetails<>(user, authorities);
    }

    protected List<GrantedAuthority> getFinalUserAuthority(T user) {
        Set<R> effectiveRoles = Optional
            .ofNullable(userService.findEffectiveRoles(user.getUserId()))
            .orElseGet(Set::of);
       LinkedHashSet<String> names = new LinkedHashSet<>();
        for (Role r : effectiveRoles) {
            if (r != null) names.add(norm(r.getName()));
        }
        return names.stream().filter(Objects::nonNull)
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toUnmodifiableList());
    }

    private String norm(String name) {
        if (name == null || name.isBlank()) return null;
        String trimmed = name.trim();
        return trimmed.startsWith("ROLE_") ? trimmed : "ROLE_" + trimmed;
    }
}
