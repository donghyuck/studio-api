package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentKind;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

class GeminiMathVisionCorrectionClientTest {

    @Test
    void parsesStringArrayFormulaResponses() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = """
                    {
                      "candidates": [
                        {
                          "content": {
                            "parts": [
                              {
                                "text": "{\\"formulas\\":[\\"x^2+1\\",\\"a^2-b^2\\"]}"
                              }
                            ]
                          }
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            GeminiMathVisionCorrectionClient client = new GeminiMathVisionCorrectionClient(
                    baseUrl, "test-key", "gemini-test", Duration.ofSeconds(5), 1024, new ObjectMapper());
            PdfDocumentAnalysis analysis = new PdfDocumentAnalysis(
                    2, 2, 0.0d, 1.0d, 0.0d, true, 0.0d, 1.0d, 1.0d, PdfDocumentKind.MATH_LIKE);
            PdfExtractionRequest request = new PdfExtractionRequest(
                    "pdf".getBytes(StandardCharsets.UTF_8),
                    "application/pdf",
                    "math.pdf",
                    PdfExtractionOptions.defaults().withMathVisionCorrection(true));

            ParsedFile result = client.correct(request, analysis, List.of(3, 5));

            assertThat(result.blocks()).hasSize(2);
            assertThat(result.blocks()).extracting(block -> block.text())
                    .containsExactly("$x^2+1$", "$a^2-b^2$");
            assertThat(result.blocks()).extracting(block -> block.page())
                    .containsExactly(3, 5);
            assertThat(result.metadata())
                    .containsEntry("mathVisionCorrectionApplied", true)
                    .containsEntry("mathVisionFormulaCount", 2);
        } finally {
            server.stop(0);
        }
    }
}
