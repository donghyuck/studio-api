package studio.one.platform.documentconvert.autoconfigure;

import java.net.http.HttpClient;
import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;

import studio.one.application.attachment.application.usecase.AttachmentDownloadUrlService;
import studio.one.application.attachment.application.usecase.AttachmentService;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertDirectResultStore;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobRepository;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertJobListener;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertStoragePort;
import studio.one.platform.documentconvert.application.port.out.DocumentConvertWorkerClient;
import studio.one.platform.documentconvert.application.service.DocumentConvertService;
import studio.one.platform.documentconvert.infrastructure.persistence.JdbcDocumentConvertJobRepository;
import studio.one.platform.documentconvert.infrastructure.worker.HttpDocumentConvertWorkerClient;
import studio.one.platform.documentconvert.web.controller.DocumentConvertController;
import studio.one.platform.documentconvert.web.controller.InternalDocumentConvertCallbackController;
import studio.one.platform.documentconvert.web.controller.InternalDocumentConvertResultController;
import studio.one.platform.documentconvert.web.controller.InternalDocumentConvertCallbackController.DocumentConvertCallbackToken;

@AutoConfiguration
@EnableConfigurationProperties(DocumentConvertProperties.class)
@ConditionalOnProperty(prefix = "studio.document-convert", name = "enabled", havingValue = "true")
@Import({DocumentConvertController.class, InternalDocumentConvertCallbackController.class,
        InternalDocumentConvertResultController.class})
public class DocumentConvertAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(NamedParameterJdbcTemplate.class)
    @ConditionalOnBean(NamedParameterJdbcTemplate.class)
    DocumentConvertJobRepository documentConvertJobRepository(NamedParameterJdbcTemplate jdbc) {
        return new JdbcDocumentConvertJobRepository(jdbc);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({AttachmentService.class, AttachmentDownloadUrlService.class})
    DocumentConvertStoragePort documentConvertStoragePort(AttachmentService attachmentService,
            AttachmentDownloadUrlService downloadUrlService, DocumentConvertProperties properties,
            ObjectProvider<DocumentConvertDirectResultStore> directResultStores) {
        var storage = properties.getStorage();
        return new AttachmentDocumentConvertStorageAdapter(attachmentService, downloadUrlService,
                properties.getCallbackBaseUrl(),
                requireSecret(properties.getCallbackToken(), "studio.document-convert.callback-token"),
                storage.getSignedUrlTtl(),
                directResultStores);
    }

    @Bean
    @ConditionalOnMissingBean
    DocumentConvertWorkerClient documentConvertWorkerClient(ObjectMapper objectMapper,
            DocumentConvertProperties properties) {
        var worker = properties.getWorker();
        HttpClient client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(worker.getConnectTimeout())
                .build();
        return new HttpDocumentConvertWorkerClient(client, objectMapper, worker.getBaseUrl(),
                requireSecret(worker.getInternalToken(), "studio.document-convert.worker.internal-token"),
                worker.getRequestTimeout());
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean({DocumentConvertJobRepository.class, DocumentConvertStoragePort.class,
            DocumentConvertWorkerClient.class})
    DocumentConvertService documentConvertService(DocumentConvertJobRepository repository,
            DocumentConvertStoragePort storage, DocumentConvertWorkerClient worker, ObjectMapper objectMapper,
            DocumentConvertProperties properties,
            org.springframework.beans.factory.ObjectProvider<DocumentConvertJobListener> listeners) {
        return new DocumentConvertService(repository, storage, worker, objectMapper,
                properties.getCallbackBaseUrl(), properties.getJob().getMaxRetryCount(), Clock.systemUTC(),
                () -> listeners.orderedStream().toList());
    }

    @Bean
    DocumentConvertCallbackToken documentConvertCallbackToken(DocumentConvertProperties properties) {
        return new DocumentConvertCallbackToken(requireSecret(properties.getCallbackToken(),
                "studio.document-convert.callback-token"));
    }

    private static String requireSecret(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required when document conversion is enabled");
        }
        return value;
    }
}
