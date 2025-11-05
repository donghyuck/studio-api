/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file ClientRequestDetails.java
 *      @date 2025
 *
 */


package studio.one.base.security.audit;

import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import lombok.Value;

/**
 * 클라이언트 요청의 주요 세부 정보를 캡처하는 불변 객체입니다.
 * @author  donghyuck, son
 * @since 2025-09-24
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-09-24  donghyuck, son: 최초 생성.
 * </pre>
 */


@Value
public class ClientRequestDetails {
  String remoteIp;     // 최종 판단한 클라이언트 IP
  String forwardedFor; // 원본 X-Forwarded-For 헤더
  String userAgent;    // User-Agent
  String sessionId;    // 있을 수도, 없을 수도

  public static ClientRequestDetails from(HttpServletRequest req) {
    String xff = Optional.ofNullable(req.getHeader("X-Forwarded-For")).orElse(null);
    String ip = extractIp(req, xff);
    String ua = Optional.ofNullable(req.getHeader("User-Agent")).orElse("");
    return new ClientRequestDetails(ip, xff, ua, req.getRequestedSessionId());
  }

  private static String extractIp(HttpServletRequest req, String xff) {
    String cand = (xff != null && !xff.isBlank())
        ? xff.split(",")[0].trim()
        : req.getRemoteAddr();
    // "127.0.0.1:54321" 같은 형태 방지
    int colon = cand.indexOf(':');
    if (colon > 0 && cand.contains(".")) cand = cand.substring(0, colon);
    return cand;
  }
}
