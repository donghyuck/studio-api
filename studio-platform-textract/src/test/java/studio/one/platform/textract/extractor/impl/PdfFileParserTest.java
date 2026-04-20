package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PdfFileParserTest {

    private final PdfFileParser parser = new PdfFileParser();

    @Test
    void cleanPdfTextCompactsFragmentedShortLines() {
        String raw = """
                증
                주
                업
                을
                왼쪽
                유
                문
                로맨스로
                고는
                오른쪽과
                그
                에 있는 캐릭터는 삶을 살면서 서로의 감정을 나누는
                """;

        String result = parser.cleanPdfText(raw);

        assertEquals("증주업을왼쪽유문로맨스로고는오른쪽과그에 있는 캐릭터는 삶을 살면서 서로의 감정을 나누는", result);
    }

    @Test
    void cleanPdfTextKeepsNormalLineBreaks() {
        String raw = """
                첫 번째 문단입니다.
                다음 줄도 문단 구조입니다.

                두 번째 문단입니다.
                정상적인 줄바꿈은 유지합니다.
                """;

        String result = parser.cleanPdfText(raw);

        assertEquals("""
                첫 번째 문단입니다.
                다음 줄도 문단 구조입니다.

                두 번째 문단입니다.
                정상적인 줄바꿈은 유지합니다.""", result);
    }
}
