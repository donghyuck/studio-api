package studio.one.platform.objecttype.application.result;

public class ObjectTypeEffectivePolicyView {

    private final int objectType;
    private final Integer maxFileMb;
    private final String allowedExt;
    private final String allowedMime;
    private final String policyJson;
    private final Source source;

    public ObjectTypeEffectivePolicyView(int objectType, Integer maxFileMb, String allowedExt, String allowedMime, String policyJson, Source source) {
        this.objectType = objectType; this.maxFileMb = maxFileMb; this.allowedExt = allowedExt; this.allowedMime = allowedMime; this.policyJson = policyJson; this.source = source;
    }

    public int objectType() { return objectType; }
    public Integer maxFileMb() { return maxFileMb; }
    public String allowedExt() { return allowedExt; }
    public String allowedMime() { return allowedMime; }
    public String policyJson() { return policyJson; }
    public Source source() { return source; }

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
