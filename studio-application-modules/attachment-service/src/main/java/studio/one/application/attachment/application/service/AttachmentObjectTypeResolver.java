package studio.one.application.attachment.application.service;

import studio.one.application.attachment.application.command.*;
import studio.one.application.attachment.application.result.*;
import studio.one.application.attachment.application.usecase.*;

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
