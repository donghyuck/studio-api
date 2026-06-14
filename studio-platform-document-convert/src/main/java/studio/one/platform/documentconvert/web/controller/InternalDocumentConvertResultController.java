package studio.one.platform.documentconvert.web.controller;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.documentconvert.web.controller.InternalDocumentConvertCallbackController.DocumentConvertCallbackToken;

@RestController
@RequiredArgsConstructor
@RequestMapping("${studio.document-convert.internal-base-path:/api/internal/document-conversions}")
public class InternalDocumentConvertResultController {
    private final DocumentConvertService service;
    private final DocumentConvertCallbackToken callbackToken;

    @PutMapping("/{jobId}/result")
    public ResponseEntity<Void> upload(
            @PathVariable String jobId,
            @RequestHeader("X-Upload-Token") String token,
            HttpServletRequest request) throws IOException {
        verifyToken(token);
        String resultFileId = service.storeResult(jobId, request.getInputStream());
        return ResponseEntity.noContent()
                .header("X-Result-File-Id", resultFileId)
                .build();
    }

    private void verifyToken(String token) {
        if (!MessageDigest.isEqual(callbackToken.value().getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }
}
