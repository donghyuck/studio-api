package studio.one.application.wiki.web.dto.request;

import javax.validation.constraints.Positive;

public class WikiArchiveRequest {

    @Positive
    private Long baseRevisionId;

    public WikiArchiveRequest() {
    }

    public WikiArchiveRequest(Long baseRevisionId) {
        this.baseRevisionId = baseRevisionId;
    }

    public Long getBaseRevisionId() { return baseRevisionId; }

    public Long baseRevisionId() { return baseRevisionId; }
    public void setBaseRevisionId(Long baseRevisionId) { this.baseRevisionId = baseRevisionId; }

}
