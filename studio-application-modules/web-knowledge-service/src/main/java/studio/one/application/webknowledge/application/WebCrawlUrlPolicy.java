package studio.one.application.webknowledge.application;

import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import studio.one.application.webknowledge.infrastructure.web.WebUrlPolicy;

public final class WebCrawlUrlPolicy {

    private static final int MAX_QUERY_VALUE_LENGTH = 256;

    public Optional<URI> candidate(
            URI seedUri,
            URI discoveredFrom,
            String candidateValue,
            ResolvedWebCrawlPolicy policy) {
        if (candidateValue == null || candidateValue.isBlank()) {
            return Optional.empty();
        }
        try {
            URI base = discoveredFrom == null ? seedUri : discoveredFrom;
            URI candidate = WebUrlPolicy.normalize(base.resolve(candidateValue.trim()).toString());
            candidate = normalizeQuery(candidate, policy.allowedQueryKeys());
            if (!sameOrigin(seedUri, candidate) || !withinScope(seedUri, candidate, policy)) {
                return Optional.empty();
            }
            if (!matchesGlobs(candidate.getPath(), policy.includePathGlobs(), policy.excludePathGlobs())) {
                return Optional.empty();
            }
            WebUrlPolicy.assertPublicHost(candidate);
            return Optional.of(candidate);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    public boolean withinScope(
            URI seedUri,
            URI candidate,
            ResolvedWebCrawlPolicy policy) {
        if (!sameOrigin(seedUri, candidate)) {
            return false;
        }
        if (policy.scope() == WebCrawlScope.SAME_ORIGIN) {
            return true;
        }
        String seedPath = normalizedPath(seedUri);
        String candidatePath = normalizedPath(candidate);
        String prefix = pathPrefix(seedPath);
        return candidatePath.equals(seedPath) || candidatePath.startsWith(prefix);
    }

    private static URI normalizeQuery(URI uri, List<String> allowedKeys) {
        if (uri.getRawQuery() == null || uri.getRawQuery().isBlank() || allowedKeys.isEmpty()) {
            return withoutQuery(uri);
        }
        List<String> allowed = allowedKeys.stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .toList();
        List<String> pairs = new ArrayList<>();
        for (String part : uri.getRawQuery().split("&")) {
            String[] values = part.split("=", 2);
            String key = decode(values[0]);
            if (key == null || !allowed.contains(key.toLowerCase(Locale.ROOT))) {
                continue;
            }
            String value = values.length == 1 ? "" : decode(values[1]);
            if (value == null || value.length() > MAX_QUERY_VALUE_LENGTH) {
                continue;
            }
            pairs.add(encode(key) + "=" + encode(value));
        }
        if (pairs.isEmpty()) {
            return withoutQuery(uri);
        }
        pairs.sort(String::compareTo);
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    String.join("&", pairs),
                    null);
        } catch (Exception ex) {
            return withoutQuery(uri);
        }
    }

    private static URI withoutQuery(URI uri) {
        try {
            return new URI(
                    uri.getScheme(),
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getPath(),
                    null,
                    null);
        } catch (Exception ex) {
            throw new IllegalArgumentException("WEB_CRAWL_URL_INVALID", ex);
        }
    }

    private static boolean sameOrigin(URI left, URI right) {
        return left != null
                && right != null
                && "https".equalsIgnoreCase(right.getScheme())
                && left.getHost().equalsIgnoreCase(right.getHost())
                && effectivePort(left) == effectivePort(right);
    }

    private static int effectivePort(URI uri) {
        return uri.getPort() < 0 ? 443 : uri.getPort();
    }

    private static String normalizedPath(URI uri) {
        String path = uri.getPath();
        return path == null || path.isBlank() ? "/" : path;
    }

    private static String pathPrefix(String seedPath) {
        if (seedPath.endsWith("/")) {
            return seedPath;
        }
        int separator = seedPath.lastIndexOf('/');
        String lastSegment = separator < 0 ? seedPath : seedPath.substring(separator + 1);
        if (lastSegment.contains(".")) {
            return separator < 0 ? "/" : seedPath.substring(0, separator + 1);
        }
        return seedPath + "/";
    }

    private static boolean matchesGlobs(
            String path,
            List<String> includeGlobs,
            List<String> excludeGlobs) {
        String candidate = path == null || path.isBlank() ? "/" : path;
        boolean included = includeGlobs.isEmpty()
                || includeGlobs.stream().anyMatch(glob -> globPattern(glob).matcher(candidate).matches());
        return included
                && excludeGlobs.stream().noneMatch(glob -> globPattern(glob).matcher(candidate).matches());
    }

    private static Pattern globPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        for (int index = 0; index < glob.length(); index++) {
            char current = glob.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < glob.length() && glob.charAt(index + 1) == '*';
                regex.append(doubleStar ? ".*" : "[^/]*");
                if (doubleStar) {
                    index++;
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else {
                if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
