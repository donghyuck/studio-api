package studio.one.base.user.domain.event.listener;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import studio.one.base.user.constant.CacheNames;
import studio.one.base.user.domain.event.UserCacheEvictableEvent;

@RequiredArgsConstructor
public class UserCacheEvictListener {

    private final CacheManager cacheManager;

    private void evictUserCaches(Long userId, String username) {
        Cache byId = cacheManager.getCache(CacheNames.User.BY_USER_ID);
        Cache byUsername = cacheManager.getCache(CacheNames.User.BY_USERNAME);

        if (byId != null) {
            byId.evict(userId);
        }
        if (byUsername != null && username != null) {
            byUsername.evict(username);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAnyUserEvent(UserCacheEvictableEvent event) {
        evictUserCaches(event.getUserId(), event.getUsername());
    }

}
