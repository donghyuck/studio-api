package studio.one.platform.objecttype.service;

import java.util.List;

import studio.one.platform.constant.ServiceNames;
public interface ObjectTypeAdminService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:adminservice";

    List<ObjectTypeView> search(String domain, String status, String q);

    ObjectTypeView get(int objectType);

    ObjectTypeView upsert(ObjectTypeUpsertCommand request);

    ObjectTypeView patch(int objectType, ObjectTypePatchCommand request);

    ObjectTypePolicyView getPolicy(int objectType);

    ObjectTypePolicyView upsertPolicy(int objectType, ObjectTypePolicyUpsertCommand request);

    void delete(int objectType);
}
