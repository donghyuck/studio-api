package studio.one.application.wiki.web.dto.request;

import javax.validation.constraints.Positive;

public class WikiRevertRequest {

    @Positive
    private Long baseRevisionId;

    public WikiRevertRequest() {
    }

    public WikiRevertRequest(Long baseRevisionId) {
        this.baseRevisionId = baseRevisionId;
    }

    public Long getBaseRevisionId() { return baseRevisionId; }

    public Long baseRevisionId() { return baseRevisionId; }
    public void setBaseRevisionId(Long baseRevisionId) { this.baseRevisionId = baseRevisionId; }

}
