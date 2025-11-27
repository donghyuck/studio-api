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
 *      @file FileParser.java
 *      @date 2025
 *
 */

package studio.one.platform.text.extractor;

import java.io.File;
import java.io.InputStream;

/**
 * 문서에서 테스트를 추출하는 파서 인터페이스
 * 
 * @author donghyuck, son
 * @since 2025-11-27
 * @version 1.0
 *
 *          <pre>
 *  
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-27  donghyuck, son: 최초 생성.
 *          </pre>
 */

public interface FileParser {

    /**
     * 이 파서가 해당 파일을 지원하는지 여부.
     */
    boolean supports(String contentType, String filename);

    /**
     * 파일 내용을 텍스트로 변환.
     *
     * @param bytes       파일 전체 바이트
     * @param contentType HTTP Content-Type (nullable)
     * @param filename    원본 파일명 (nullable 아님이 좋음)
     */
    String parse(byte[] bytes, String contentType, String filename) throws FileParseException;
}