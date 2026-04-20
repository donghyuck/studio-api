package studio.one.application.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

import studio.one.application.attachment.domain.model.Attachment;
import studio.one.application.attachment.service.AttachmentService;
import studio.one.platform.ai.core.embedding.EmbeddingPort;
import studio.one.platform.ai.core.embedding.EmbeddingResponse;
import studio.one.platform.ai.core.embedding.EmbeddingVector;
import studio.one.platform.ai.core.rag.RagIndexRequest;
import studio.one.platform.ai.core.rag.RagSearchResult;
import studio.one.platform.ai.service.pipeline.RagPipelineService;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.service.I18n;
import studio.one.platform.textract.service.FileContentExtractionService;

class AttachmentEmbeddingPipelineControllerTest {

    private static final String BASE_PATH = "/api/mgmt/attachments";

    private AttachmentService attachmentService;
    private FileContentExtractionService extractionService;
    private EmbeddingPort embeddingPort;
    private RagPipelineService ragPipelineService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        attachmentService = mock(AttachmentService.class);
        extractionService = mock(FileContentExtractionService.class);
        embeddingPort = mock(EmbeddingPort.class);
        ragPipelineService = mock(RagPipelineService.class);

        AttachmentEmbeddingPipelineController controller = new AttachmentEmbeddingPipelineController(
                attachmentService,
                provider(extractionService),
                provider(embeddingPort),
                provider(null),
                provider(ragPipelineService),
                provider((I18n) null));

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(new ObjectMapper()))
                .setValidator(validator)
                .addPlaceholderValue(PropertyKeys.Features.PREFIX + ".attachment.web.mgmt-base-path", BASE_PATH)
                .build();
    }

    @Test
    void embedReturnsStableApiResponseJsonShape() throws Exception {
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(embeddingPort.embed(any()))
                .thenReturn(new EmbeddingResponse(List.of(new EmbeddingVector("0", List.of(0.1d, 0.2d)))));
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(get(BASE_PATH + "/1/embedding").param("storeVector", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.vectors[0].referenceId").value("0"))
                .andExpect(jsonPath("$.data.vectors[0].values[0]").value(0.1d))
                .andExpect(jsonPath("$.data.vectors[0].values[1]").value(0.2d));
    }

    @Test
    void ragSearchBindsRequestAndReturnsStableJsonShape() throws Exception {
        when(ragPipelineService.search(any()))
                .thenReturn(List.of(new RagSearchResult("doc-1", "body", Map.of("topic", "alpha"), 0.9d)));

        mockMvc.perform(post(BASE_PATH + "/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "hello",
                                  "topK": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$.results[0].content").value("body"))
                .andExpect(jsonPath("$.results[0].metadata.topic").value("alpha"))
                .andExpect(jsonPath("$.results[0].score").value(0.9d));
    }

    @Test
    void ragSearchRejectsInvalidRequestBody() throws Exception {
        mockMvc.perform(post(BASE_PATH + "/rag/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "query": "",
                                  "topK": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ragIndexAddsAttachmentMetadataWithoutOverwritingCallerMetadata() throws Exception {
        Attachment attachment = mock(Attachment.class);
        when(attachmentService.getAttachmentById(1L)).thenReturn(attachment);
        when(attachmentService.getInputStream(attachment))
                .thenReturn(new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)));
        when(attachment.getAttachmentId()).thenReturn(1L);
        when(attachment.getContentType()).thenReturn("text/plain");
        when(attachment.getName()).thenReturn("sample.txt");
        when(attachment.getSize()).thenReturn(5L);
        when(extractionService.extractText(any(), any(), any(InputStream.class))).thenReturn("hello");

        mockMvc.perform(post(BASE_PATH + "/1/rag/index")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "metadata": {
                                    "filename": "caller.txt",
                                    "sourceType": "caller-source",
                                    "indexedAt": "caller-time"
                                  }
                                }
                                """))
                .andExpect(status().isAccepted());

        verify(ragPipelineService).index(argThat((RagIndexRequest request) -> {
            Map<String, Object> metadata = request.metadata();
            return "caller.txt".equals(metadata.get("filename"))
                    && "caller-source".equals(metadata.get("sourceType"))
                    && "caller-time".equals(metadata.get("indexedAt"))
                    && "attachment".equals(metadata.get("objectType"))
                    && "1".equals(metadata.get("objectId"))
                    && Long.valueOf(1L).equals(metadata.get("attachmentId"))
                    && "sample.txt".equals(metadata.get("name"))
                    && "text/plain".equals(metadata.get("contentType"))
                    && Long.valueOf(5L).equals(metadata.get("size"));
        }));
    }

    private static <T> ObjectProvider<T> provider(T value) {
        @SuppressWarnings("unchecked")
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
