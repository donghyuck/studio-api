package studio.one.base.security.audit.service;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LoginFailQuery {
    private OffsetDateTime from; // 기간 필터(>=)
    private OffsetDateTime to; // 기간 필터(<)
    private String usernameLike; // 부분검색
    private String ipEquals; // 정확히 일치
    private String failureType; // 유형 필터
}