package studio.one.platform.ai.web.controller;

import java.util.Locale;
import java.util.regex.Pattern;

public enum RagEvidenceCoverageRequirement {
    ANY_RELEVANT_SOURCE,
    DOCUMENT_AND_EXTERNAL;

    private static final Pattern COMPARISON = Pattern.compile(
            "(문서|첨부|내부\\s*자료).{0,30}(외부|웹|공식\\s*자료).{0,30}(비교|대조|검토|차이|기준)"
                    + "|(외부|웹|공식\\s*자료).{0,30}(문서|첨부|내부\\s*자료).{0,30}(비교|대조|검토|차이)"
                    + "|외부\\s*(자료|기준|법규).{0,30}(비교|대조|검토|확인)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public static RagEvidenceCoverageRequirement classify(String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return COMPARISON.matcher(normalized).find()
                ? DOCUMENT_AND_EXTERNAL
                : ANY_RELEVANT_SOURCE;
    }
}
