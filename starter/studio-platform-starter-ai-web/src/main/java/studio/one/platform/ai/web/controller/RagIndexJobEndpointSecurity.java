package studio.one.platform.ai.web.controller;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.core.rag.RagIndexJob;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;

@Component("ragIndexJobEndpointSecurity")
@RequiredArgsConstructor
public class RagIndexJobEndpointSecurity {

    private static final String ATTACHMENT = "attachment";

    private final RagIndexJobService jobService;

    public boolean isAttachmentSource(RagIndexJobCreateRequestDto request) {
        return request != null
                && (isAttachment(request.sourceType()) || isAttachment(request.objectType()));
    }

    public boolean isAttachmentObject(String objectType) {
        return isAttachment(objectType);
    }

    public boolean isAttachmentJob(String jobId) {
        return jobService.getJob(jobId)
                .map(job -> isAttachment(job.sourceType()) || isAttachment(job.objectType()))
                .orElse(false);
    }

    public boolean canWriteJob(String jobId, RagObjectAuthorizationRouter authorizationRouter) {
        if (authorizationRouter == null) {
            return false;
        }
        return jobService.getJob(jobId)
                .filter(job -> job.objectType() != null && job.objectId() != null)
                .map(job -> authorizationRouter.canWrite(job.objectType(), job.objectId()))
                .orElse(false);
    }

    public boolean canReadJobObject(String jobId, RagObjectAuthorizationRouter authorizationRouter) {
        if (authorizationRouter == null) {
            return false;
        }
        return jobService.getJob(jobId)
                .filter(job -> normalize(job.objectType()) != null && normalize(job.objectId()) != null)
                .map(job -> authorizationRouter.canRead(job.objectType(), job.objectId()))
                .orElse(false);
    }

    public String jobObjectId(String jobId) {
        return jobService.getJob(jobId)
                .map(RagIndexJobEndpointSecurity::normalizeObjectId)
                .orElse(null);
    }

    public String jobObjectType(String jobId) {
        return jobService.getJob(jobId)
                .map(RagIndexJobEndpointSecurity::normalizeObjectType)
                .orElse(null);
    }

    private static String normalizeObjectType(RagIndexJob job) {
        return normalize(job.objectType());
    }

    private static String normalizeObjectId(RagIndexJob job) {
        return normalize(job.objectId());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean isAttachment(String value) {
        return value != null && ATTACHMENT.equalsIgnoreCase(value.trim());
    }
}
