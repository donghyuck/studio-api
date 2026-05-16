package studio.one.platform.skillgraph.application.command;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CourseSkillMappingCommand(
        @Size(max = 100) String mappingId,
        @NotBlank @Size(max = 100) String courseId,
        @NotBlank @Size(max = 100) String skillId,
        @DecimalMin("0.0") @DecimalMax("1.0") double weight) {
}
