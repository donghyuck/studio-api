package studio.one.platform.objecttype.application.usecase;

import studio.one.platform.objecttype.application.command.ValidateUploadCommand;
import studio.one.platform.objecttype.application.result.ObjectTypeDefinition;
import studio.one.platform.objecttype.application.result.ValidateUploadResult;

public interface ObjectTypeKeyRuntimeService extends ObjectTypeRuntimeService {

    ObjectTypeDefinition definitionByKey(String key);

    int objectTypeByKey(String key);

    ValidateUploadResult validateUploadByKey(String key, ValidateUploadCommand request);
}
