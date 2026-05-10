package studio.one.platform.textract.application.usecase;

import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.ParsedFile;

/**
 * Parser contract for RAG-oriented structured file extraction.
 */
public interface StructuredFileParser extends FileParser {

    /**
     * 파일 내용을 구조화된 파싱 결과로 변환한다.
     */
    ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException;

    @Override
    default String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }
}
