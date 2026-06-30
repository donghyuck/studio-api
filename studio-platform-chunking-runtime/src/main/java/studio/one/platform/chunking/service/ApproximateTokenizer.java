package studio.one.platform.chunking.service;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.chunking.core.TokenizerPort;

public class ApproximateTokenizer implements TokenizerPort {

    private static final int AVERAGE_LATIN_CHARS_PER_TOKEN = 4;

    @Override
    public String provider() {
        return "approximate";
    }

    @Override
    public String encodingName() {
        return "approximate";
    }

    @Override
    public int countTokens(String text) {
        return encode(text).size();
    }

    @Override
    public List<Integer> encode(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<Integer> tokens = new ArrayList<>();
        int latinRunLength = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isWhitespace(ch)) {
                latinRunLength = flushLatin(tokens, latinRunLength);
                continue;
            }
            if (isConservativeSingleToken(ch)) {
                latinRunLength = flushLatin(tokens, latinRunLength);
                tokens.add((int) ch);
                continue;
            }
            latinRunLength++;
            if (latinRunLength >= AVERAGE_LATIN_CHARS_PER_TOKEN) {
                tokens.add((int) ch);
                latinRunLength = 0;
            }
        }
        flushLatin(tokens, latinRunLength);
        return tokens;
    }

    @Override
    public String decode(List<Integer> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(tokens.size());
        for (Integer token : tokens) {
            if (token != null && token > 0) {
                builder.appendCodePoint(token);
            }
        }
        return builder.toString();
    }

    private int flushLatin(List<Integer> tokens, int latinRunLength) {
        if (latinRunLength > 0) {
            tokens.add(1);
        }
        return 0;
    }

    private boolean isConservativeSingleToken(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.HANGUL_SYLLABLES
                || block == Character.UnicodeBlock.HANGUL_JAMO
                || block == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || Character.getType(ch) == Character.OTHER_PUNCTUATION
                || Character.getType(ch) == Character.MATH_SYMBOL
                || Character.getType(ch) == Character.CURRENCY_SYMBOL;
    }
}
