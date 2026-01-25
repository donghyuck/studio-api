package studio.one.platform.objecttype.service;

import studio.one.platform.objecttype.web.dto.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.ValidateUploadResponse;
import studio.one.platform.constant.ServiceNames;
public interface ObjectTypeRuntimeService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:runtimeservice";

    ObjectTypeDefinitionDto definition(int objectType);

    ValidateUploadResponse validateUpload(int objectType, ValidateUploadRequest request);
}
