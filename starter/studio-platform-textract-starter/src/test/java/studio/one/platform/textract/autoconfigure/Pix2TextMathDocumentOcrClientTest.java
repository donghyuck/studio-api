package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import studio.one.platform.textract.domain.model.ParsedFile;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentAnalysis;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfDocumentKind;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.infrastructure.extractor.pdf.PdfExtractionRequest;

class Pix2TextMathDocumentOcrClientTest {

    @Test
    void mapsPix2TextMarkdownAndBlockProvenance() throws Exception {
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/pdf", exchange -> {
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    {
                      "markdown": "# 수식\\n\\n$x^2+1$",
                      "metadata": {"pageCount": 1},
                      "blocks": [
                        {
                          "type": "PARAGRAPH",
                          "text": "$x^2+1$",
                          "sourceRef": "page[1]/block[0]",
                          "bbox": [1,2,3,4]
                        }
                      ]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Pix2TextMathDocumentOcrClient client = new Pix2TextMathDocumentOcrClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/extract/pdf",
                    Duration.ofSeconds(5),
                    1024,
                    "ko,en",
                    true,
                    4,
                    new ObjectMapper());

            ParsedFile file = client.extract(request(), analysis());

            assertThat(requestBody.get()).contains("name=\"options\"").contains("ko,en")
                    .contains("\"pageByPage\":true")
                    .contains("name=\"file\"");
            assertThat(file.markdown()).contains("$x^2+1$");
            assertThat(file.metadata())
                    .containsEntry("mathOcrProvider", "pix2text")
                    .containsEntry("mathMarkdownQuality", "VALID")
                    .containsEntry("mathDocumentEngineRequired", false);
            assertThat(file.blocks()).hasSize(1);
            assertThat(file.blocks().get(0).page()).isEqualTo(1);
            assertThat(file.blocks().get(0).sourceRef()).isEqualTo("page[1]/block[0]");
            assertThat(file.blocks().get(0).metadata()).containsKey("bbox");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void extractsLargePdfByPageBatches() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> requestBodies = new AtomicReference<>("");
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/pdf", exchange -> {
            int call = calls.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1);
            requestBodies.set(requestBodies.get() + "\n---\n" + body);
            int pageFrom = body.contains("\"pageFrom\":4") ? 4 : 1;
            int pageTo = body.contains("\"pageTo\":5") ? 5 : 3;
            byte[] response = ("""
                    {
                      "markdown": "batch-%d",
                      "metadata": {"pageCount": 5, "pageFrom": %d, "pageTo": %d},
                      "blocks": [
                        {
                          "type": "PARAGRAPH",
                          "text": "batch-%d",
                          "sourceRef": "page[%d]/block[0]"
                        }
                      ]
                    }
                    """.formatted(call, pageFrom, pageTo, call, pageFrom)).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Pix2TextMathDocumentOcrClient client = new Pix2TextMathDocumentOcrClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/extract/pdf",
                    Duration.ofSeconds(5),
                    1024,
                    "ko,en",
                    true,
                    3,
                    new ObjectMapper());

            ParsedFile file = client.extract(request(), new PdfDocumentAnalysis(
                    5, 2, 0.0d, 1.0d, 0.0d, true, 0.0d, 0.0d, 1.0d, PdfDocumentKind.MATH_LIKE));

            assertThat(calls).hasValue(2);
            assertThat(requestBodies.get())
                    .contains("\"pageFrom\":1")
                    .contains("\"pageTo\":3")
                    .contains("\"pageFrom\":4")
                    .contains("\"pageTo\":5");
            assertThat(file.markdown()).contains("batch-1").contains("batch-2");
            assertThat(file.blocks()).hasSize(2);
            assertThat(file.metadata())
                    .containsEntry("mathOcrProvider", "pix2text")
                    .containsEntry("pix2textBatched", true)
                    .containsEntry("pageBatchSize", 3)
                    .containsEntry("pageBatchCount", 2);
        } finally {
            server.stop(0);
        }
    }

    private PdfExtractionRequest request() {
        return new PdfExtractionRequest(
                "%PDF".getBytes(StandardCharsets.UTF_8),
                "application/pdf",
                "math.pdf",
                PdfExtractionOptions.defaults());
    }

    private PdfDocumentAnalysis analysis() {
        return new PdfDocumentAnalysis(
                1,
                1,
                0.1d,
                0.9d,
                0.0d,
                true,
                0.0d,
                0.1d,
                0.8d,
                PdfDocumentKind.MATH_LIKE);
    }
}
