package studio.one.platform.text.extractor.impl;

import studio.one.platform.text.extractor.FileParseException;

final class LegacyParserSupport {

    private LegacyParserSupport() {
    }

    static <T> T translate(ParserCall<T> call) throws FileParseException {
        try {
            return call.run();
        } catch (studio.one.platform.textract.extractor.FileParseException e) {
            throw FileParseException.from(e);
        }
    }

    @FunctionalInterface
    interface ParserCall<T> {
        T run() throws studio.one.platform.textract.extractor.FileParseException;
    }
}
