package studio.one.platform.ai.web.controller;

import java.io.IOException;

import org.springframework.boot.jackson.JsonComponent;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import dev.langchain4j.model.output.TokenUsage;

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
