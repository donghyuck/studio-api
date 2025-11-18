package studio.one.base.user.constant;

import lombok.NoArgsConstructor;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class JpaEntityNames {

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Company {
        public static final String ENTITY = "ApplicationCompany";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class User {
        public static final String ENTITY = "ApplicationUser";

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Role {
            public static final String ENTITY = "ApplicationUserRole";
        }        
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Group {
        public static final String ENTITY = "ApplicationGroup";

        @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
        public static final class Role {
            public static final String ENTITY = "ApplicationGroupRole";
        }
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class GroupMembership {
        public static final String ENTITY = "ApplicationGroupMembership";
    }

    @NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
    public static final class Role {
        public static final String ENTITY = "ApplicationRole";
    }
}
