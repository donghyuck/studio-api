package studio.one.base.user.domain.event;

public interface UserCacheEvictableEvent {
    Long getUserId();

    String getUsername();
}
