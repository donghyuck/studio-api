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
 *      @file ApplicationUserDetails.java
 *      @date 2025
 *
 */

package studio.echo.base.security.userdetails;

import java.time.Instant;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;
import studio.echo.base.user.domain.model.User;

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

@Getter 
public class ApplicationUserDetails<T extends User> implements UserDetails {

    private  final transient T domainUser;

    private final Collection<? extends GrantedAuthority> authorities;

    ApplicationUserDetails(T domainUser, Collection<? extends GrantedAuthority> authorities) {
        this.domainUser = domainUser ;
        this.authorities = authorities;
    }
    
    public
    Collection<? extends GrantedAuthority> getAuthorities(){
        return authorities;
    }

    public T getDomainUser() { return domainUser; }

    @Override
    public String getPassword() {
        return domainUser.getPassword();
    }

    @Override
    public String getUsername() {
        return domainUser.getUsername();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true; 
    }

    @Override
    public boolean isAccountNonLocked() { 
        Instant until = domainUser.getAccountLockedUntil(); 
        return (until == null) || Instant.now().isAfter(until);
    } 

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return domainUser.isEnabled();
    }

    // 추가 메서드
    public Long getUserId() {
        return domainUser.getUserId();
    }

    public String getEmail() {
        return domainUser.getEmail();
    }

    public String getName() {
        return domainUser.getName();
    }
}
