package studio.one.application.wiki.web.dto.request;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

public class WikiPageWriteRequest {

    @NotBlank
    @Size(max = 255)
    private String title;

    @NotNull
    private String markdown;

    @Positive
    private Long baseRevisionId;

    public WikiPageWriteRequest() {
    }

    public WikiPageWriteRequest(String title, String markdown, Long baseRevisionId) {
        this.title = title;
        this.markdown = markdown;
        this.baseRevisionId = baseRevisionId;
    }

    public String getTitle() { return title; }

    public String title() { return title; }

    public String getMarkdown() { return markdown; }

    public String markdown() { return markdown; }

    public Long getBaseRevisionId() { return baseRevisionId; }

    public Long baseRevisionId() { return baseRevisionId; }
    public void setTitle(String title) { this.title = title; }

    public void setMarkdown(String markdown) { this.markdown = markdown; }

    public void setBaseRevisionId(Long baseRevisionId) { this.baseRevisionId = baseRevisionId; }

}
