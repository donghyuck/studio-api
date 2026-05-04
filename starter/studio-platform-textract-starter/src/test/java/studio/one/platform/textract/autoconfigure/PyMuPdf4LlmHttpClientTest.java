package studio.one.platform.textract.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import studio.one.platform.textract.extractor.pdf.PdfExtractionOptions;
import studio.one.platform.textract.extractor.pdf.PdfExtractionRequest;

class PyMuPdf4LlmHttpClientTest {

    @Test
    void sanitizesMultipartFilenameAndContentTypeHeaderValues() throws Exception {
        AtomicReference<String> bodyRef = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/extract/pdf", exchange -> {
            bodyRef.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
            byte[] response = """
                    {
                      "filename": "sample.pdf",
                      "contentType": "application/pdf",
                      "markdown": "ok",
                      "pages": [],
                      "blocks": [],
                      "tables": [],
                      "images": [],
                      "metadata": {},
                      "warnings": [],
                      "elapsedMs": 1,
                      "ocrApplied": false
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            PyMuPdf4LlmHttpClient client = new PyMuPdf4LlmHttpClient(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/extract/pdf",
                    Duration.ofSeconds(5),
                    1024,
                    new ObjectMapper());
            client.extract(new PdfExtractionRequest(
                    "%PDF".getBytes(StandardCharsets.UTF_8),
                    "application/pdf\r\nX-Injected: yes",
                    "sample.pdf\"\r\nX-Injected: yes",
                    PdfExtractionOptions.defaults()));
        } finally {
            server.stop(0);
        }

        assertThat(bodyRef.get())
                .contains("Content-Type: application/pdf")
                .contains("filename=\"sample.pdf\"")
                .doesNotContain("X-Injected");
    }
}
