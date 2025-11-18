package studio.one.base.user.constant;

import lombok.NoArgsConstructor;

public class CacheNames {

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class User {
        public static final String BY_USER_ID = "users.byUserId";
        public static final String BY_USERNAME = "users.byUsername";
    }
}
