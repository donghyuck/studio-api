package studio.one.platform.chunking.core;

import java.util.List;

/**
 * Tokenizer contract used when chunk boundaries must follow token boundaries.
 */
public interface TokenizerPort extends TokenCounter {

    List<Integer> encode(String text);

    String decode(List<Integer> tokens);
}
