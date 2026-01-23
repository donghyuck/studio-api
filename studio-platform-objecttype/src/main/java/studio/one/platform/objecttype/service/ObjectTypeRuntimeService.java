package studio.one.platform.objecttype.service;

import studio.one.platform.objecttype.web.dto.ObjectTypeDefinitionDto;
import studio.one.platform.objecttype.web.dto.ValidateUploadRequest;
import studio.one.platform.objecttype.web.dto.ValidateUploadResponse;

public interface ObjectTypeRuntimeService {

    ObjectTypeDefinitionDto definition(int objectType);

    ValidateUploadResponse validateUpload(int objectType, ValidateUploadRequest request);
}
