package studio.one.platform.skillgraph.application.usecase;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import studio.one.platform.skillgraph.application.command.GenerateSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.ReconcileSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveAndAssignSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.command.SaveSkillCategoryDraftCommand;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftAssignmentResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryDraftResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryReconcileResult;
import studio.one.platform.skillgraph.application.result.SkillCategoryView;
import studio.one.platform.skillgraph.application.result.SkillClusterRepresentativeView;

public interface SkillCategoryDraftService {

    String SERVICE_NAME = "skillCategoryDraftService";

    SkillCategoryDraftResult generateDrafts(String projectionId, int representativeLimit);

    SkillCategoryDraftResult generateDrafts(GenerateSkillCategoryDraftCommand command);

    SkillCategoryReconcileResult reconcileDrafts(ReconcileSkillCategoryDraftCommand command);

    Page<SkillClusterRepresentativeView> findRepresentatives(
            String projectionId,
            String clusterId,
            boolean includeNoise,
            Pageable pageable);

    List<SkillCategoryView> saveDrafts(SaveSkillCategoryDraftCommand command);

    SkillCategoryDraftAssignmentResult saveAndAssignDrafts(SaveAndAssignSkillCategoryDraftCommand command);
}
