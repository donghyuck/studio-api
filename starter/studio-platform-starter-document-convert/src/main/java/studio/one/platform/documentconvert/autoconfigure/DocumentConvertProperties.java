package studio.one.platform.documentconvert.autoconfigure;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("studio.document-convert")
public class DocumentConvertProperties {
    private boolean enabled;
    private URI callbackBaseUrl = URI.create("http://localhost:8080");
    private String callbackToken;
    private final Worker worker = new Worker();
    private final Storage storage = new Storage();
    private final Job job = new Job();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public URI getCallbackBaseUrl() { return callbackBaseUrl; }
    public void setCallbackBaseUrl(URI callbackBaseUrl) { this.callbackBaseUrl = callbackBaseUrl; }
    public String getCallbackToken() { return callbackToken; }
    public void setCallbackToken(String callbackToken) { this.callbackToken = callbackToken; }
    public Worker getWorker() { return worker; }
    public Storage getStorage() { return storage; }
    public Job getJob() { return job; }

    public static class Worker {
        private URI baseUrl = URI.create("http://localhost:8090");
        private String internalToken;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofSeconds(30);
        public URI getBaseUrl() { return baseUrl; }
        public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
        public String getInternalToken() { return internalToken; }
        public void setInternalToken(String internalToken) { this.internalToken = internalToken; }
        public Duration getConnectTimeout() { return connectTimeout; }
        public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
        public Duration getRequestTimeout() { return requestTimeout; }
        public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    }

    public static class Storage {
        private Duration signedUrlTtl = Duration.ofMinutes(10);
        public Duration getSignedUrlTtl() { return signedUrlTtl; }
        public void setSignedUrlTtl(Duration signedUrlTtl) { this.signedUrlTtl = signedUrlTtl; }
    }

    public static class Job {
        private int maxRetryCount = 2;
        public int getMaxRetryCount() { return maxRetryCount; }
        public void setMaxRetryCount(int maxRetryCount) { this.maxRetryCount = maxRetryCount; }
    }
}
