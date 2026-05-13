package studio.one.platform.ai.web.dto;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import javax.validation.constraints.NotBlank;

/**
 * 요청된 질문을 검색에 적합한 형태로 리라이트하기 위한 DTO.
 */
public class QueryRewriteRequestDto {

    @NotBlank(message = "query must not be blank")
    private final String query;

    @JsonCreator
    public QueryRewriteRequestDto(@JsonProperty("query") String query) {
        this.query = query;
    }

    public String query() {
        return query;
    }

    public String getQuery() {
        return query;
    }

}