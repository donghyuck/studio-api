package studio.one.application.webknowledge.infrastructure.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayOutputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import studio.one.application.webknowledge.application.WebPageFetchPolicy;
import studio.one.application.webknowledge.application.WebPageFetchPort;

@SuppressWarnings({ "rawtypes", "unchecked" })
class SafeHttpWebPageFetcherTest {

    private static final URI PUBLIC_URI = URI.create("https://93.184.216.34/article");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-29T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void returnsBoundedHtmlWithResponseMetadata() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        byte[] body = "<html><body>reference</body></html>".getBytes(StandardCharsets.UTF_8);
        ClassicHttpResponse response = response(200, "text/html; charset=utf-8", body);
        response.setHeader("ETag", "\"v1\"");
        response.setHeader("Last-Modified", "Wed, 29 Jul 2026 00:00:00 GMT");
        respond(client, response);
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(1024, 2), CLOCK, client);

        WebPageFetchPort.FetchResult result = fetcher.fetch(
                PUBLIC_URI,
                WebPageFetchPort.ConditionalRequest.none());

        assertEquals(200, result.statusCode());
        assertEquals("text/html", result.contentType());
        assertArrayEquals(body, result.body());
        assertEquals("\"v1\"", result.etag());
        assertEquals(CLOCK.instant(), result.retrievedAt());
    }

    @Test
    void followsOnlyManuallyValidatedRedirects() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        ClassicHttpResponse redirect = response(302, null, new byte[0]);
        redirect.setHeader("Location", "/final");
        ClassicHttpResponse success = response(
                200,
                "text/html",
                "<html>final</html>".getBytes(StandardCharsets.UTF_8));
        respond(client, redirect, success);
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(1024, 2), CLOCK, client);

        WebPageFetchPort.FetchResult result = fetcher.fetch(
                PUBLIC_URI,
                new WebPageFetchPort.ConditionalRequest("\"private-origin-etag\"", null));

        assertEquals("https://93.184.216.34/final", result.finalUri().toString());
        ArgumentCaptor<ClassicHttpRequest> requests = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        verify(client, times(2)).execute(
                requests.capture(),
                any(HttpClientResponseHandler.class));
        assertEquals("\"private-origin-etag\"",
                requests.getAllValues().get(0).getFirstHeader("If-None-Match").getValue());
        assertEquals("\"private-origin-etag\"",
                requests.getAllValues().get(1).getFirstHeader("If-None-Match").getValue());
    }

    @Test
    void rejectsCrossOriginRedirects() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        ClassicHttpResponse redirect = response(302, null, new byte[0]);
        redirect.setHeader("Location", "https://93.184.216.35/final");
        respond(client, redirect);
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(1024, 2), CLOCK, client);

        WebPageFetchException error = assertThrows(
                WebPageFetchException.class,
                () -> fetcher.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));

        assertEquals("CROSS_ORIGIN_REDIRECT_NOT_ALLOWED", error.errorCode());
        verify(client, times(1)).execute(
                any(ClassicHttpRequest.class),
                any(HttpClientResponseHandler.class));
    }

    @Test
    void rejectsOversizedAndNonHtmlResponses() throws Exception {
        CloseableHttpClient oversizedClient = mock(CloseableHttpClient.class);
        respond(oversizedClient, response(200, "text/html", new byte[9]));
        SafeHttpWebPageFetcher oversized = new SafeHttpWebPageFetcher(policy(8, 0), CLOCK, oversizedClient);

        WebPageFetchException sizeError = assertThrows(
                WebPageFetchException.class,
                () -> oversized.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));
        assertEquals("RESPONSE_TOO_LARGE", sizeError.errorCode());

        CloseableHttpClient jsonClient = mock(CloseableHttpClient.class);
        respond(jsonClient, response(200, "application/json", "{}".getBytes(StandardCharsets.UTF_8)));
        SafeHttpWebPageFetcher json = new SafeHttpWebPageFetcher(policy(1024, 0), CLOCK, jsonClient);

        WebPageFetchException typeError = assertThrows(
                WebPageFetchException.class,
                () -> json.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));
        assertEquals("CONTENT_TYPE_NOT_ALLOWED", typeError.errorCode());
    }

    @Test
    void rejectsPageLevelCollectionProhibition() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        ClassicHttpResponse response = response(
                200,
                "text/html",
                "<html>blocked</html>".getBytes(StandardCharsets.UTF_8));
        response.setHeader("X-Robots-Tag", "noindex");
        respond(client, response);
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(1024, 0), CLOCK, client);

        WebPageFetchException error = assertThrows(
                WebPageFetchException.class,
                () -> fetcher.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));

        assertEquals("PAGE_COLLECTION_DISALLOWED", error.errorCode());
    }

    @Test
    void distinguishesRemovedPageFromOtherHttpFailures() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        respond(client, response(404, null, new byte[0]));
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(1024, 0), CLOCK, client);

        WebPageFetchException error = assertThrows(
                WebPageFetchException.class,
                () -> fetcher.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));

        assertEquals("PAGE_REMOVED", error.errorCode());
    }

    @Test
    void rejectsGzipExpansionBeyondDecompressedLimit() throws Exception {
        CloseableHttpClient client = mock(CloseableHttpClient.class);
        ClassicHttpResponse response = response(
                200,
                "text/html",
                gzip("<html><body>" + "x".repeat(200) + "</body></html>"));
        response.setHeader("Content-Encoding", "gzip");
        respond(client, response);
        SafeHttpWebPageFetcher fetcher = new SafeHttpWebPageFetcher(policy(128, 0), CLOCK, client);

        WebPageFetchException error = assertThrows(
                WebPageFetchException.class,
                () -> fetcher.fetch(PUBLIC_URI, WebPageFetchPort.ConditionalRequest.none()));

        assertEquals("RESPONSE_TOO_LARGE", error.errorCode());
    }

    private static WebPageFetchPolicy policy(int maxBytes, int maxRedirects) {
        return new WebPageFetchPolicy(
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                maxBytes,
                10_000,
                maxRedirects,
                false,
                "test-agent");
    }

    private static ClassicHttpResponse response(int status, String contentType, byte[] body) {
        BasicClassicHttpResponse response = new BasicClassicHttpResponse(status);
        if (contentType != null) {
            response.setHeader("Content-Type", contentType);
        }
        response.setEntity(new ByteArrayEntity(body, ContentType.APPLICATION_OCTET_STREAM));
        return response;
    }

    private static void respond(
            CloseableHttpClient client,
            ClassicHttpResponse... responses) throws IOException {
        AtomicInteger index = new AtomicInteger();
        when(client.execute(
                any(ClassicHttpRequest.class),
                any(HttpClientResponseHandler.class)))
                .thenAnswer(invocation -> {
                    HttpClientResponseHandler handler = invocation.getArgument(1);
                    return handler.handleResponse(responses[index.getAndIncrement()]);
                });
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
}
