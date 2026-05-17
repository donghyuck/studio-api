package studio.one.platform.skillgraph.application.usecase;

import studio.one.platform.skillgraph.application.command.SkillExtractionCommand;
import studio.one.platform.skillgraph.application.result.SkillExtractionResult;

public interface SkillExtractionService {

    String SERVICE_NAME = "skillExtractionService";

    SkillExtractionResult extract(SkillExtractionCommand command);

    default SkillExtractionResult dryRun(SkillExtractionCommand command) {
        throw new UnsupportedOperationException("Skill extraction dry-run is not supported");
    }
}
