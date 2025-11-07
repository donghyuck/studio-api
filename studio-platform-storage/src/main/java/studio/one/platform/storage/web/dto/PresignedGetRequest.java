package studio.one.platform.storage.web.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignedGetRequest {
    @NotBlank
    private String key;

    /** 초단위 TTL (기본 300, 최대 86400 = 24h) */
    @Min(1)
    @Max(86400)
    private Long ttlSeconds;

    /** Content-Disposition: inline|attachment */
    private String disposition;

    /** 다운로드 파일명 (선택) */
    private String filename;

    /** 응답 Content-Type 힌트 (선택) */
    private String contentType;
}