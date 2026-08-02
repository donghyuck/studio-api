package studio.one.application.webknowledge.web;

import org.hibernate.validator.constraints.URL;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WebKnowledgeSitePreviewRequest(
        @NotBlank @Size(max = 2048) @URL(protocol = "https") String url,
        @Valid WebCrawlPolicyRequest crawlPolicy) {
}
