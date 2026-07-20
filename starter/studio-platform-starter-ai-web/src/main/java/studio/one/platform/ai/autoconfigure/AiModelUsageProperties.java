package studio.one.platform.ai.autoconfigure;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "studio.ai.usage")
public class AiModelUsageProperties {

    private boolean enabled = true;
    private String currency = "USD";
    private final Map<String, ModelPricing> pricing = new LinkedHashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public Map<String, ModelPricing> getPricing() {
        return pricing;
    }

    public static class ModelPricing {
        private BigDecimal inputPerMillionTokens;
        private BigDecimal outputPerMillionTokens;
        private Integer highContextThresholdTokens;
        private BigDecimal highContextInputPerMillionTokens;
        private BigDecimal highContextOutputPerMillionTokens;

        public BigDecimal getInputPerMillionTokens() {
            return inputPerMillionTokens;
        }

        public void setInputPerMillionTokens(BigDecimal value) {
            this.inputPerMillionTokens = nonNegative(value, "inputPerMillionTokens");
        }

        public BigDecimal getOutputPerMillionTokens() {
            return outputPerMillionTokens;
        }

        public void setOutputPerMillionTokens(BigDecimal value) {
            this.outputPerMillionTokens = nonNegative(value, "outputPerMillionTokens");
        }

        public Integer getHighContextThresholdTokens() {
            return highContextThresholdTokens;
        }

        public void setHighContextThresholdTokens(Integer value) {
            if (value != null && value <= 0) {
                throw new IllegalArgumentException("highContextThresholdTokens must be greater than 0");
            }
            this.highContextThresholdTokens = value;
        }

        public BigDecimal getHighContextInputPerMillionTokens() {
            return highContextInputPerMillionTokens;
        }

        public void setHighContextInputPerMillionTokens(BigDecimal value) {
            this.highContextInputPerMillionTokens = nonNegative(value, "highContextInputPerMillionTokens");
        }

        public BigDecimal getHighContextOutputPerMillionTokens() {
            return highContextOutputPerMillionTokens;
        }

        public void setHighContextOutputPerMillionTokens(BigDecimal value) {
            this.highContextOutputPerMillionTokens = nonNegative(value, "highContextOutputPerMillionTokens");
        }

        private static BigDecimal nonNegative(BigDecimal value, String name) {
            if (value != null && value.signum() < 0) {
                throw new IllegalArgumentException(name + " must not be negative");
            }
            return value;
        }
    }
}
