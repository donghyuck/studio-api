package studio.one.platform.ai.web.controller;

import java.io.IOException;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.model.output.TokenUsage;

/**
 * LangChain4j TokenUsage를 Jackson 직렬화할 때 입력/출력/총 토큰 수를 명시적 필드로 출력하는 JsonComponent입니다. 컨트롤러 응답 내 메타데이터에 토큰 사용량을 포함할 때 활용LangChain4j TokenUsage를 Jackson 직렬화할 때 입력/출력/총 토큰 수를 명시적 필드로 출력하는 JsonComponent입니다. 컨트롤러 응답 내 메타데이터에 토큰 사용량을 포함할 때 활용
 * 
 * @author  donghyuck, son
 * @since 2025-11-28
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-11-28  donghyuck, son: 최초 생성.
 * </pre>
 */

@JsonComponent
public class TokenUsageJsonComponent {

    public static class TokenUsageSerializer extends StdSerializer<TokenUsage> {
        public TokenUsageSerializer() { super(TokenUsage.class); }

        @Override
        public void serialize(TokenUsage tu, JsonGenerator gen, SerializerProvider serializers)
                throws IOException {
            gen.writeStartObject();
            write(gen, "inputTokens",  tu.inputTokenCount());
            write(gen, "outputTokens", tu.outputTokenCount());
            write(gen, "totalTokens",  tu.totalTokenCount());
            gen.writeEndObject();
        }
        private void write(JsonGenerator gen, String name, Number n) throws IOException {
            if (n == null) gen.writeNullField(name); else gen.writeNumberField(name, n.longValue());
        }
    }
}
