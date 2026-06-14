package studio.one.platform.documentconvert.web.controller;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import studio.one.platform.documentconvert.application.result.DocumentConvertJobResult;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.documentconvert.web.dto.DocumentConvertCallbackRequest;
import studio.one.platform.web.dto.ApiResponse;

@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.document-convert.internal-base-path:/api/internal/document-conversions}")
public class InternalDocumentConvertCallbackController {
    private final DocumentConvertService service;
    private final DocumentConvertCallbackToken callbackToken;

    @PostMapping("/{jobId}/callback")
    public ResponseEntity<ApiResponse<DocumentConvertJobResult>> callback(
            @PathVariable String jobId,
            @RequestHeader("X-Internal-Token") String token,
            @Valid @RequestBody DocumentConvertCallbackRequest request) {
        if (!MessageDigest.isEqual(callbackToken.value().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return ResponseEntity.ok(ApiResponse.ok(service.callback(jobId, request.status(), request.resultFileId(),
                request.errorCode(), request.errorMessage())));
    }

    public record DocumentConvertCallbackToken(String value) {
    }
}
