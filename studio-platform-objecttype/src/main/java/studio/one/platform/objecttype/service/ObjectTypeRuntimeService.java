package studio.one.platform.objecttype.service;

import studio.one.platform.constant.ServiceNames;
public interface ObjectTypeRuntimeService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:runtimeservice";

    ObjectTypeDefinition definition(int objectType);

    ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request);
}
