package studio.one.platform.chunking.service;

import java.util.List;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.IntArrayList;

import studio.one.platform.chunking.core.TokenizerPort;

public class TiktokenTokenizerAdapter implements TokenizerPort {

    private static final EncodingRegistry REGISTRY = Encodings.newLazyEncodingRegistry();

    private final String encodingName;
    private final Encoding encoding;

    public TiktokenTokenizerAdapter(String encodingName) {
        if (encodingName == null || encodingName.isBlank()) {
            throw new IllegalArgumentException("encodingName must not be blank");
        }
        this.encodingName = encodingName.trim();
        this.encoding = REGISTRY.getEncoding(this.encodingName)
                .orElseThrow(() -> new IllegalArgumentException("Unsupported tiktoken encoding: " + encodingName));
    }

    @Override
    public String provider() {
        return "tiktoken";
    }

    @Override
    public String encodingName() {
        return encodingName;
    }

    @Override
    public int countTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    @Override
    public List<Integer> encode(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return encoding.encode(text).boxed();
    }

    @Override
    public String decode(List<Integer> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        IntArrayList values = new IntArrayList(tokens.size());
        for (Integer token : tokens) {
            if (token != null) {
                values.add(token);
            }
        }
        return encoding.decode(values);
    }
}
