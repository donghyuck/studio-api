package studio.one.platform.skillgraph.domain.model;

import java.util.Locale;

public enum SkillType {
    CONCEPT_SKILL,
    TECH_SKILL,
    TOOL_SKILL,
    TASK_SKILL,
    DOMAIN_SKILL,
    SOFT_SKILL,
    UNKNOWN;

    public static SkillType from(String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
        try {
            return SkillType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    public static String normalizeName(String value) {
        return from(value).name();
    }
}
