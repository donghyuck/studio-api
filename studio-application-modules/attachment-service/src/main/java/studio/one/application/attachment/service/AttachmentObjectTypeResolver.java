package studio.one.application.attachment.service;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.util.StringUtils;

import studio.one.platform.objecttype.application.usecase.ObjectTypeRuntimeService;

public class AttachmentObjectTypeResolver {

    private final ObjectProvider<ObjectTypeRuntimeService> runtimeServiceProvider;

    public AttachmentObjectTypeResolver(ObjectProvider<ObjectTypeRuntimeService> runtimeServiceProvider) {
        this.runtimeServiceProvider = runtimeServiceProvider;
    }

    public int resolveRequired(String objectTypeKey) {
        if (!StringUtils.hasText(objectTypeKey)) {
            throw new IllegalArgumentException("objectTypeKey is required");
        }
        ObjectTypeRuntimeService runtimeService = runtimeServiceProvider.getIfAvailable();
        if (runtimeService == null) {
            throw new IllegalStateException("ObjectTypeRuntimeService is required to resolve attachment objectType key: "
                    + objectTypeKey);
        }
        return runtimeService.objectTypeByKey(objectTypeKey.trim());
    }
}
