package studio.one.platform.objecttype.application.usecase;

import studio.one.platform.constant.ServiceNames;
import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;

public interface ObjectTypeRuntimeService {

    public static final String SERVICE_NAME = ServiceNames.PREFIX + ":objecttype:runtimeservice";

    ObjectTypeDefinition definition(int objectType);

    default ObjectTypeDefinition definitionByKey(String key) {
        if (this instanceof ObjectTypeKeyRuntimeService keyRuntimeService) {
            return keyRuntimeService.definitionByKey(key);
        }
        throw new UnsupportedOperationException("ObjectType key lookup is not supported");
    }

    default int objectTypeByKey(String key) {
        if (this instanceof ObjectTypeKeyRuntimeService keyRuntimeService) {
            return keyRuntimeService.objectTypeByKey(key);
        }
        throw new UnsupportedOperationException("ObjectType key lookup is not supported");
    }

    ValidateUploadResult validateUpload(int objectType, ValidateUploadCommand request);

    default ValidateUploadResult validateUploadByKey(String key, ValidateUploadCommand request) {
        if (this instanceof ObjectTypeKeyRuntimeService keyRuntimeService) {
            return keyRuntimeService.validateUploadByKey(key, request);
        }
        throw new UnsupportedOperationException("ObjectType key lookup is not supported");
    }
}
