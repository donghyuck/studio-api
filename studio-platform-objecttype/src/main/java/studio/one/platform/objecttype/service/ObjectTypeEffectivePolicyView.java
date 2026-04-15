package studio.one.platform.objecttype.service;

public record ObjectTypeEffectivePolicyView(
        int objectType,
        Integer maxFileMb,
        String allowedExt,
        String allowedMime,
        String policyJson,
        Source source
) {

    public enum Source {
        STORED("stored"),
        DEFAULT("default");

        private final String value;

        Source(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
