package studio.one.platform.ai.autoconfigure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptPropertiesTest {

    @Test
    void includesSkillGraphPromptsByDefault() {
        PromptProperties properties = new PromptProperties();

        assertThat(properties.getPrompts())
                .containsEntry("skill-extraction", "classpath:/prompts/skill-extraction.v1.prompt")
                .containsEntry("skill-category-naming", "classpath:/prompts/skill-category-naming.v1.prompt");
    }
}
