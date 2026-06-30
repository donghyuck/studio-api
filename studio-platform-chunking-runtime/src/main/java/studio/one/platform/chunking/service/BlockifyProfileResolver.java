package studio.one.platform.chunking.service;

public class BlockifyProfileResolver {

    public BlockifyProfile resolve(BlockifyDocumentTypeClassification classification) {
        BlockifyDocumentType type = classification == null || classification.effectiveType() == null
                ? BlockifyDocumentType.GENERAL
                : classification.effectiveType();
        return switch (type) {
            case POLICY -> new BlockifyProfile(type, "policy-v1", "blockify-policy-v1",
                    "policy-rule-condition-exception");
            case MANUAL -> new BlockifyProfile(type, "manual-v1", "blockify-manual-v1",
                    "task-procedure-input-output");
            case NARRATIVE -> new BlockifyProfile(type, "narrative-v1", "blockify-narrative-v1",
                    "character-event-cause-effect");
            case TECHNICAL -> new BlockifyProfile(type, "technical-v1", "blockify-technical-v1",
                    "component-api-error-configuration");
            case TABLE_HEAVY -> new BlockifyProfile(type, "table-heavy-v1", "blockify-table-v1",
                    "table-row-metric-condition");
            case AUTO, GENERAL -> new BlockifyProfile(BlockifyDocumentType.GENERAL, "general-v1",
                    "blockify-general-v1", "topic-claim-evidence");
        };
    }
}
