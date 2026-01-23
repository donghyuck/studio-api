package studio.one.platform.objecttype.service;

import java.util.List;

import studio.one.platform.objecttype.web.dto.ObjectTypeDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePatchRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyDto;
import studio.one.platform.objecttype.web.dto.ObjectTypePolicyUpsertRequest;
import studio.one.platform.objecttype.web.dto.ObjectTypeUpsertRequest;

public interface ObjectTypeAdminService {

    List<ObjectTypeDto> search(String domain, String status, String q);

    ObjectTypeDto get(int objectType);

    ObjectTypeDto upsert(ObjectTypeUpsertRequest request);

    ObjectTypeDto patch(int objectType, ObjectTypePatchRequest request);

    ObjectTypePolicyDto getPolicy(int objectType);

    ObjectTypePolicyDto upsertPolicy(int objectType, ObjectTypePolicyUpsertRequest request);
}
