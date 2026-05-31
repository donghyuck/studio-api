package studio.one.platform.skillgraph.application.usecase;

import studio.one.platform.skillgraph.domain.model.SkillGraphBatchJob;

public interface SkillGraphBatchJobNotifier {

    SkillGraphBatchJobNotifier NOOP = job -> {
    };

    void notifyJob(SkillGraphBatchJob job);
}
