/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file AiInfoController.java
 *      @date 2025
 *
 */

package studio.one.platform.ai.web.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.ai.autoconfigure.config.AiAdapterProperties;
import studio.one.platform.ai.core.registry.AiProviderRegistry;
import studio.one.platform.ai.core.vector.VectorStorePort;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.web.dto.ApiResponse;

/**
 * Provides information about enabled AI providers, models, and vector store
 * status.
 */
@RestController
@RequestMapping("${" + PropertyKeys.AI.Endpoints.BASE_PATH + ":/api/ai}/info")
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(prefix = PropertyKeys.AI.Endpoints.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = false)
public class AiInfoController {

        private final AiAdapterProperties properties;
        private final AiProviderRegistry registry;

        @Nullable
        private final VectorStorePort vectorStorePort;

        @GetMapping("/providers")
        public ResponseEntity<ApiResponse<AiInfoResponse>> providers() {
                List<ProviderInfo> providers = new ArrayList<>();
                providers.add(mapOpenAi());
                providers.add(mapOllama());
                providers.add(mapGoogleAi());
                VectorInfo vectorInfo = new VectorInfo(
                                vectorStorePort != null,
                                vectorStorePort == null ? null : vectorStorePort.getClass().getSimpleName());

                AiInfoResponse response = new AiInfoResponse(providers, properties.getDefaultProvider(), vectorInfo);
                return ResponseEntity.ok(ApiResponse.ok(response));
        }

        private ProviderInfo mapOpenAi() {
                AiAdapterProperties.OpenAiProperties openai = properties.getOpenai();
                ProviderChannel chat = new ProviderChannel(openai.isEnabled() && openai.getChat().isEnabled(),
                                openai.getChat().getOptions().getModel());
                ProviderChannel embedding = new ProviderChannel(openai.isEnabled() && openai.getEmbedding().isEnabled(),
                                openai.getEmbedding().getOptions().getModel());
                return new ProviderInfo("openai", chat, embedding, openai.getBaseUrl());
        }

        private ProviderInfo mapOllama() {
                AiAdapterProperties.OllamaProperties ollama = properties.getOllama();
                ProviderChannel chat = new ProviderChannel(false, null);
                ProviderChannel embedding = new ProviderChannel(ollama.isEnabled() && ollama.getEmbedding().isEnabled(),
                                ollama.getEmbedding().getOptions().getModel());
                return new ProviderInfo("ollama", chat, embedding, ollama.getBaseUrl());
        }

        private ProviderInfo mapGoogleAi() {
                AiAdapterProperties.GoogleAiGeminiProperties google = properties.getGoogleAiGemini();
                ProviderChannel chat = new ProviderChannel(google.isEnabled() && google.getChat().isEnabled(),
                                google.getChat().getOptions().getModel());
                ProviderChannel embedding = new ProviderChannel(google.isEnabled() && google.getEmbedding().isEnabled(),
                                google.getEmbedding().getOptions().getModel());
                return new ProviderInfo("google-ai-gemini", chat, embedding, google.getBaseUrl());
        }

        public record AiInfoResponse(List<ProviderInfo> providers, String defaultProvider, VectorInfo vector) {

        }

        public record ProviderInfo(String name, ProviderChannel chat, ProviderChannel embedding, String baseUrl) {

        }

        public record ProviderChannel(boolean enabled, String model) {

        }

        public record VectorInfo(boolean available, String implementation) {

        }
}
