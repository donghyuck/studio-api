package studio.one.platform.objecttype.application.usecase;

import studio.one.platform.constant.ServiceNames;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;

public interface ObjectTypeRuntimeService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:runtimeservice";

    ObjectTypeDefinition definition(int objectType);

    ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request);
}
