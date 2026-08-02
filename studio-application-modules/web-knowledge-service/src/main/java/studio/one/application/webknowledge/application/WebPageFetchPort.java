package studio.one.application.webknowledge.application;

import java.net.URI;
import java.time.Instant;

public interface WebPageFetchPort {

    FetchResult fetch(URI uri, ConditionalRequest conditionalRequest);

    default FetchResult fetch(
            URI uri,
            ConditionalRequest conditionalRequest,
            ResourceKind resourceKind) {
        if (resourceKind == null || resourceKind == ResourceKind.PAGE) {
            return fetch(uri, conditionalRequest);
        }
        throw new UnsupportedOperationException("Resource fetch kind is not supported");
    }

    enum ResourceKind {
        PAGE,
        ROBOTS,
        SITEMAP
    }

    record ConditionalRequest(String etag, String lastModified) {
        public static ConditionalRequest none() {
            return new ConditionalRequest(null, null);
        }
    }

    record FetchResult(
            URI requestedUri,
            URI finalUri,
            int statusCode,
            String contentType,
            byte[] body,
            String etag,
            String lastModified,
            Instant retrievedAt,
            boolean notModified,
            long compressedBytes,
            long decompressedBytes) {

        public FetchResult {
            body = body == null ? new byte[0] : body.clone();
        }

        public FetchResult(
                URI requestedUri,
                URI finalUri,
                int statusCode,
                String contentType,
                byte[] body,
                String etag,
                String lastModified,
                Instant retrievedAt,
                boolean notModified) {
            this(
                    requestedUri,
                    finalUri,
                    statusCode,
                    contentType,
                    body,
                    etag,
                    lastModified,
                    retrievedAt,
                    notModified,
                    body == null ? 0L : body.length,
                    body == null ? 0L : body.length);
        }
    }
}
