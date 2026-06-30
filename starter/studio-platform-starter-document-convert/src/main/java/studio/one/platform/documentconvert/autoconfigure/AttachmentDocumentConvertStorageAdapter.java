package studio.one.platform.documentconvert.autoconfigure;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.result.AttachmentDownloadUrlEndpointKind;
import studio.one.application.attachment.application.result.AttachmentDownloadUrlIssueActor;
import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlService;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.application.attachment.domain.model.Attachment;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertDirectResultStore;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertStoragePort;
import studio.one.platform.documentconvert.domain.model.DocumentConvertJob;

public class AttachmentDocumentConvertStorageAdapter implements DocumentConvertStoragePort {
    private final AttachmentService attachmentService;
    private final AttachmentDownloadUrlService downloadUrlService;
    private final URI callbackBaseUrl;
    private final String uploadToken;
    private final Duration ttl;
    private final ObjectProvider<DocumentConvertDirectResultStore> directResultStores;
    private final Map<String, Long> resultAttachmentIds = new ConcurrentHashMap<>();

    public AttachmentDocumentConvertStorageAdapter(AttachmentService attachmentService,
            AttachmentDownloadUrlService downloadUrlService, URI callbackBaseUrl, String uploadToken, Duration ttl) {
        this(attachmentService, downloadUrlService, callbackBaseUrl, uploadToken, ttl, null);
    }

    public AttachmentDocumentConvertStorageAdapter(AttachmentService attachmentService,
            AttachmentDownloadUrlService downloadUrlService, URI callbackBaseUrl, String uploadToken, Duration ttl,
            ObjectProvider<DocumentConvertDirectResultStore> directResultStores) {
        this.attachmentService = attachmentService;
        this.downloadUrlService = downloadUrlService;
        this.callbackBaseUrl = callbackBaseUrl;
        this.uploadToken = uploadToken;
        this.ttl = ttl;
        this.directResultStores = directResultStores;
    }

    @Override
    public TransferUrls prepareTransfer(DocumentConvertJob job) {
        Attachment source = sourceAttachment(job);
        var sourceUrl = downloadUrlService.issueDownloadUrl(
                source,
                ttl.toSeconds(),
                AttachmentDownloadUrlEndpointKind.SERVICE,
                new AttachmentDownloadUrlIssueActor(source.getCreatedBy(), "document-convert"),
                null,
                "studio-document-convert");
        URI uploadUrl = callbackBaseUrl.resolve(
                "/api/internal/document-conversions/" + job.jobId() + "/result");
        return new TransferUrls(workerAccessibleUrl(URI.create(sourceUrl.url())), uploadUrl, uploadToken, job.jobId());
    }

    @Override
    public String storeResult(DocumentConvertJob job, InputStream input) {
        DocumentConvertDirectResultStore directStore = directStore(job);
        if (directStore != null) {
            return directStore.storeResult(job, input);
        }
        Attachment source = sourceAttachment(job);
        Attachment result = attachmentService.createAttachment(
                source.getObjectType(),
                source.getObjectId(),
                resultName(source, job),
                contentType(job),
                input);
        resultAttachmentIds.put(job.jobId(), result.getAttachmentId());
        return Long.toString(result.getAttachmentId());
    }

    @Override
    public String resultFileId(DocumentConvertJob job) {
        if (job.resultFileId() != null && !job.resultFileId().isBlank()) {
            return job.resultFileId();
        }
        Long attachmentId = resultAttachmentIds.get(job.jobId());
        return attachmentId == null ? job.jobId() : Long.toString(attachmentId);
    }

    @Override
    public URI resultDownloadUrl(DocumentConvertJob job) {
        long attachmentId = Long.parseLong(resultFileId(job));
        Attachment result = attachmentService.getAttachmentById(attachmentId);
        var url = downloadUrlService.issueDownloadUrl(
                result,
                ttl.toSeconds(),
                AttachmentDownloadUrlEndpointKind.SERVICE,
                new AttachmentDownloadUrlIssueActor(result.getCreatedBy(), "document-convert"),
                null,
                "studio-document-convert");
        return URI.create(url.url());
    }

    private Attachment sourceAttachment(DocumentConvertJob job) {
        return attachmentService.getAttachmentById(parseAttachmentId(job.sourceFileId()));
    }

    private DocumentConvertDirectResultStore directStore(DocumentConvertJob job) {
        if (directResultStores == null) {
            return null;
        }
        return directResultStores.orderedStream()
                .filter(store -> store.supports(job))
                .findFirst()
                .orElse(null);
    }

    private URI workerAccessibleUrl(URI publicUrl) {
        String pathAndQuery = publicUrl.getRawPath();
        if (publicUrl.getRawQuery() != null) {
            pathAndQuery += "?" + publicUrl.getRawQuery();
        }
        return callbackBaseUrl.resolve(pathAndQuery);
    }

    private long parseAttachmentId(String value) {
        String normalized = value != null && value.startsWith("att-") ? value.substring(4) : value;
        try {
            return Long.parseLong(normalized);
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("sourceFileId must identify an attachment", ex);
        }
    }

    private String resultName(Attachment source, DocumentConvertJob job) {
        String name = source.getName();
        int dot = name == null ? -1 : name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        if (base == null || base.isBlank()) {
            base = "converted";
        }
        return base + "." + job.targetFormat().extension();
    }

    private String contentType(DocumentConvertJob job) {
        return switch (job.targetFormat()) {
            case PDF -> "application/pdf";
            case DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case HTML -> "text/html";
            case MARKDOWN -> "text/markdown";
            case TEXT -> "text/plain";
        };
    }
}
