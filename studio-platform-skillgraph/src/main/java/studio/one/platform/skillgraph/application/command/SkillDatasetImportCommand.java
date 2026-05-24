package studio.one.platform.skillgraph.application.command;

public record SkillDatasetImportCommand(

        String provider,

        String datasetId,

        String datasetName,

        String version,

        String language,

        String sourceLocation

) {

}