package studio.one.platform.ai.autoconfigure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.AI.PREFIX + ".vector")
public class VectorStoreProperties {

    private final PostgresProperties postgres = new PostgresProperties();

    public PostgresProperties getPostgres() {
        return postgres;
    }

    public static class PostgresProperties {
        private String textSearchConfig = "simple";

        public String getTextSearchConfig() {
            return textSearchConfig;
        }

        public void setTextSearchConfig(String textSearchConfig) {
            this.textSearchConfig = textSearchConfig;
        }
    }
}
