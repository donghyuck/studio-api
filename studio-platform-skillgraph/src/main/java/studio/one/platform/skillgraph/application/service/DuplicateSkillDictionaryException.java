package studio.one.platform.skillgraph.application.service;

public class DuplicateSkillDictionaryException extends RuntimeException {

    public DuplicateSkillDictionaryException(String normalizedName) {
        super("Skill dictionary already exists: " + normalizedName);
    }
}
