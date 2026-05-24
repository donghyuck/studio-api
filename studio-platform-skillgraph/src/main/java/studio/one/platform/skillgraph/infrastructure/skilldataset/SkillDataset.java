package studio.one.platform.skillgraph.infrastructure.skilldataset;

import java.time.Instant;

public record SkillDataset(

        String datasetId,

        String provider,

        String datasetName,

        String version,

        String language,

        String sourceLocation,

        Instant importedAt

) {

}
