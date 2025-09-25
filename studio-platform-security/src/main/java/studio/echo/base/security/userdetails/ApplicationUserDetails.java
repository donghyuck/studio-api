package studio.echo.base.security.userdetails;

import java.time.Instant;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import lombok.Getter;
import studio.echo.base.user.domain.model.User;

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
