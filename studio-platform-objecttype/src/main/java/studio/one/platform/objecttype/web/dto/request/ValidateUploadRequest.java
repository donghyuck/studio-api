package studio.one.platform.objecttype.web.dto.request;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

public class ValidateUploadRequest {

    @NotBlank private String fileName;
    @NotBlank private String contentType;
    @NotNull @Min(0) private Long sizeBytes;

    public ValidateUploadRequest() { }
    public ValidateUploadRequest(String fileName, String contentType, Long sizeBytes) { this.fileName = fileName; this.contentType = contentType; this.sizeBytes = sizeBytes; }

    public String fileName() { return fileName; }
    public String contentType() { return contentType; }
    public Long sizeBytes() { return sizeBytes; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public Long getSizeBytes() { return sizeBytes; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
}
