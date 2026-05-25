package studio.one.platform.skillgraph.application.service;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;

final class SkillReferenceEmbeddingTextBuilder {

    String build(SkillConcept concept, String strategy) {
        String normalized = strategy == null || strategy.isBlank()
                ? "LABEL_DESCRIPTION_CATEGORY_RAW_KEYWORDS"
                : strategy.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "LABEL_ONLY" -> joinLines(line("능력명", concept.preferredLabel()));
            case "LABEL_DESCRIPTION" -> joinLines(
                    line("능력명", concept.preferredLabel()),
                    line("정규화명", concept.normalizedLabel()),
                    line("설명", concept.description()));
            case "LABEL_DESCRIPTION_CATEGORY" -> joinLines(
                    line("능력명", concept.preferredLabel()),
                    line("정규화명", concept.normalizedLabel()),
                    line("설명", concept.description()),
                    line("분류", concept.categoryPath()),
                    line("수준", concept.levelValue()));
            case "LABEL_DESCRIPTION_CATEGORY_RAW_KEYWORDS" -> joinLines(
                    line("능력명", concept.preferredLabel()),
                    line("정규화명", concept.normalizedLabel()),
                    line("설명", concept.description()),
                    line("분류", concept.categoryPath()),
                    line("수준", concept.levelValue()),
                    line("외부코드", concept.externalCode()),
                    line("원본", compactRawJson(concept.rawJson())));
            default -> throw new IllegalArgumentException("Unsupported textBuildStrategy: " + strategy);
        };
    }

    private String line(String label, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return label + ": " + value.trim();
    }

    private String joinLines(String... lines) {
        List<String> values = new ArrayList<>();
        for (String line : lines) {
            if (line != null && !line.isBlank()) {
                values.add(line);
            }
        }
        return String.join("\n", values);
    }

    private String compactRawJson(String rawJson) {
        if (rawJson == null || rawJson.isBlank() || "{}".equals(rawJson.trim())) {
            return null;
        }
        String compact = rawJson.replaceAll("\\s+", " ").trim();
        return compact.length() <= 1000 ? compact : compact.substring(0, 1000);
    }
}
