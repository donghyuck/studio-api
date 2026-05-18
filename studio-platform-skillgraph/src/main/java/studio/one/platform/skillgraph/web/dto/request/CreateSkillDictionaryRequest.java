package studio.one.platform.skillgraph.web.dto.request;

import jakarta.validation.constraints.Size;
import studio.one.platform.skillgraph.application.command.CreateSkillDictionaryCommand;

public record CreateSkillDictionaryRequest(
        @Size(max = 200) String name,
        @Size(max = 200) String normalizedName,
        @Size(max = 100) String categoryId,
        @Size(max = 50) String status,
        @Size(max = 2000) String description) {

    public CreateSkillDictionaryCommand toCommand() {
        return new CreateSkillDictionaryCommand(name, normalizedName, categoryId, status, description);
    }
}
