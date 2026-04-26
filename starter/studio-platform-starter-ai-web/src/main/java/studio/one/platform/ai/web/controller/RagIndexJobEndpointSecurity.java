package studio.one.platform.ai.web.controller;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import studio.one.platform.ai.service.pipeline.RagIndexJobService;
import studio.one.platform.ai.web.dto.RagIndexJobCreateRequestDto;

@Component("ragIndexJobEndpointSecurity")
@RequiredArgsConstructor
public class RagIndexJobEndpointSecurity {

    private static final String ATTACHMENT = "attachment";

    private final RagIndexJobService jobService;

    public boolean isAttachmentSource(RagIndexJobCreateRequestDto request) {
        return request != null && ATTACHMENT.equalsIgnoreCase(request.sourceType());
    }

    public boolean isAttachmentObject(String objectType) {
        return ATTACHMENT.equalsIgnoreCase(objectType);
    }

    public boolean isAttachmentJob(String jobId) {
        return jobService.getJob(jobId)
                .map(job -> ATTACHMENT.equalsIgnoreCase(job.sourceType()))
                .orElse(false);
    }
}
