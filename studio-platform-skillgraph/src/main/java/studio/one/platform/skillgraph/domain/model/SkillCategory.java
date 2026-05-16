package studio.one.platform.skillgraph.domain.model;

public record SkillCategory(String categoryId, String parentCategoryId, String name, int displayOrder) {

    public SkillCategory {
        categoryId = requireText(categoryId, "categoryId");
        parentCategoryId = normalize(parentCategoryId);
        name = requireText(name, "name");
        displayOrder = Math.max(0, displayOrder);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
