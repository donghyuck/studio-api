package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentKind;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

class PaddleOcrKoreanTextClientTest {

    @Test
    void mapsKoreanOcrBlocksAndPageProvenance() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/pdf", exchange -> {
            byte[] response = """
                    {
                      "filename": "sample.pdf",
                      "contentType": "application/pdf",
                      "markdown": "다항식의 정리",
                      "pages": [],
                      "blocks": [{
                        "type": "PARAGRAPH",
                        "text": "다항식의 정리",
                        "pageNumber": 3,
                        "order": 0,
                        "sourceRef": "page[3]/block[0]",
                        "bbox": [1,2,3,4]
                      }],
                      "tables": [],
                      "images": [],
                      "metadata": {},
                      "warnings": [],
                      "elapsedMs": 1,
                      "ocrApplied": true
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            PaddleOcrKoreanTextClient client = new PaddleOcrKoreanTextClient(
                    true,
                    "paddleocr",
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/extract/pdf",
                    Duration.ofSeconds(5),
                    1024,
                    2,
                    new ObjectMapper());
            PdfExtractionOptions options = PdfExtractionOptions.defaults().forPageRange(3, 3);
            ParsedFile file = client.extract(
                    new PdfExtractionRequest("%PDF".getBytes(StandardCharsets.UTF_8),
                            "application/pdf", "sample.pdf", options),
                    new PdfDocumentAnalysis(3, 1, 0.1d, 0.9d, 0.0d, true,
                            0.0d, 0.8d, 0.0d, PdfDocumentKind.SCANNED));

            assertThat(file.metadata())
                    .containsEntry("extractionEngine", "paddleocr")
                    .containsEntry("koreanTextOcrProvider", "paddleocr")
                    .containsEntry("pageFrom", 3)
                    .containsEntry("pageTo", 3);
            assertThat(client.maxPagesPerRequest()).isEqualTo(2);
            assertThat(file.blocks()).singleElement().satisfies(block -> {
                assertThat(block.page()).isEqualTo(3);
                assertThat(block.sourceRef()).isEqualTo("page[3]/block[0]");
                assertThat(block.metadata()).containsKey("bbox");
            });
        } finally {
            server.stop(0);
        }
    }
}
