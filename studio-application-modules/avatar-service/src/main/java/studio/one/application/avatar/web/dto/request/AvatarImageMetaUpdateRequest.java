package studio.one.application.avatar.web.dto.request;

public class AvatarImageMetaUpdateRequest {

    private String fileName;
    private Boolean primaryImage;

    public AvatarImageMetaUpdateRequest() {
    }

    public AvatarImageMetaUpdateRequest(
            String fileName,
            Boolean primaryImage) {
        this.fileName = fileName;
        this.primaryImage = primaryImage;
    }

    public String getFileName() { return fileName; }

    public void setFileName(String fileName) { this.fileName = fileName; }

    public String fileName() { return fileName; }

    public Boolean getPrimaryImage() { return primaryImage; }

    public void setPrimaryImage(Boolean primaryImage) { this.primaryImage = primaryImage; }

    public Boolean primaryImage() { return primaryImage; }

}
