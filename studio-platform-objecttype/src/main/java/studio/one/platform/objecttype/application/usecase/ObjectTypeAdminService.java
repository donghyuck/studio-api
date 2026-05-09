package studio.one.platform.objecttype.application.usecase;

import java.util.List;

import studio.one.platform.constant.ServiceNames;
import studio.one.platform.objecttype.application.command.ObjectTypePatchCommand;
import studio.one.platform.objecttype.application.command.ObjectTypePolicyUpsertCommand;
import studio.one.platform.objecttype.application.command.ObjectTypeUpsertCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeEffectivePolicyView;
import studio.one.platform.objecttype.application.result.ObjectTypePolicyView;
import studio.one.platform.objecttype.application.result.ObjectTypeView;

public interface ObjectTypeAdminService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:adminservice";

    List<ObjectTypeView> search(String domain, String status, String q);

    ObjectTypeView get(int objectType);

    ObjectTypeView upsert(ObjectTypeUpsertCommand request);

    ObjectTypeView patch(int objectType, ObjectTypePatchCommand request);

    ObjectTypePolicyView getPolicy(int objectType);

    ObjectTypeEffectivePolicyView getEffectivePolicy(int objectType);

    ObjectTypePolicyView upsertPolicy(int objectType, ObjectTypePolicyUpsertCommand request);

    void delete(int objectType);
}
