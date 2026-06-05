package studio.one.platform.skillgraph.application.usecase;

import studio.one.platform.skillgraph.application.result.SkillRagExtractionJob;

public interface SkillRagExtractionJobNotifier {

    SkillRagExtractionJobNotifier NOOP = job -> {
    };

    void notifyJob(SkillRagExtractionJob job);
}
