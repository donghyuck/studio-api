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
 *      @file OverlapTextChunker.java
 *      @date 2025
 *
 */

package studio.one.platform.ai.service.chunk;

import studio.one.platform.ai.core.chunk.TextChunk;
import studio.one.platform.ai.core.chunk.TextChunker;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/** 
 * <p>입력 텍스트를 정규화(CR/LF 통합, 과도한 빈줄 제거)한 뒤 문단 단위로 이어붙여
 * 지정된 길이(chunkSize)에 도달하면 청크를 생성하고, overlap 길이만큼 꼬리를 다음 청크에
 * 이어붙여 연속성을 유지한다. chunk_id는 문서 ID에 인덱스와 랜덤 UUID를 덧붙여 생성한다.
 * 
 * @author  donghyuck, son
 * @since 2025-12-02
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-12-02  donghyuck, son: 최초 생성.
 * </pre>
 */

public class OverlapTextChunker implements TextChunker {

    private final int chunkSize;
    private final int overlap;
    private static final Pattern MULTI_BLANK_LINES = Pattern.compile("\\n{3,}");

    public OverlapTextChunker(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap cannot be negative");
        }
        this.chunkSize = chunkSize;
        this.overlap = Math.min(overlap, chunkSize - 1);
    }

    @Override
    public List<TextChunk> chunk(String documentId, String text) {
        List<TextChunk> chunks = new ArrayList<>();
        String normalized = normalize(text);
        if (normalized == null || normalized.isBlank()) {
            return chunks;
        }
        String[] paragraphs = normalized.split("\\n\\s*\\n");
        StringBuilder current = new StringBuilder();
        int index = 0;

        for (String para : paragraphs) {
            if (current.length() + para.length() > chunkSize && current.length() > 0) {
                chunks.add(new TextChunk(buildChunkId(documentId, index++), current.toString().trim()));
                current = new StringBuilder(tailForOverlap(current));
            }
            current.append(para).append("\n\n");
        }

        if (current.length() > 0) {
            chunks.add(new TextChunk(buildChunkId(documentId, index), current.toString().trim()));
        }
        return chunks;
    }

    private String tailForOverlap(CharSequence chunk) {
        if (overlap <= 0) {
            return "";
        }
        int len = chunk.length();
        int start = Math.max(0, len - overlap);
        return chunk.subSequence(start, len).toString();
    }

    private String buildChunkId(String documentId, int index) {
        return documentId + "-" + index + "-" + UUID.randomUUID();
    }

    private String normalize(String text) {
        if (text == null) {
            return null;
        }
        String normalized = text.replace("\r\n", "\n").replace("\r", "\n");
        normalized = MULTI_BLANK_LINES.matcher(normalized).replaceAll("\n\n");
        return normalized.trim();
    }
}
