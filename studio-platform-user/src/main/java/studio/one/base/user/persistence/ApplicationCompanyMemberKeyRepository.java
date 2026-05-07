package studio.one.base.user.persistence;

import java.util.Optional;

import studio.one.base.user.domain.entity.ApplicationCompanyMemberKey;
import studio.one.platform.constant.ServiceNames;

public interface ApplicationCompanyMemberKeyRepository {

    String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":user:repository:company-member-key-repository";

    Optional<ApplicationCompanyMemberKey> findById(Long keyId);

    Optional<ApplicationCompanyMemberKey> findByKeyHash(String keyHash);

    default Optional<ApplicationCompanyMemberKey> findForUpdateById(Long keyId) {
        return findById(keyId);
    }

    default Optional<ApplicationCompanyMemberKey> findForUpdateByKeyHash(String keyHash) {
        return findByKeyHash(keyHash);
    }

    ApplicationCompanyMemberKey save(ApplicationCompanyMemberKey key);
}
