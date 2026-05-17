package studio.one.platform.autoconfigure.skillgraph;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "studio.skillgraph")
public class SkillGraphProperties {

    private Persistence persistence = Persistence.memory;
    private Extraction extraction = new Extraction();
    private Matching matching = new Matching();
    private Clustering clustering = new Clustering();

    public enum Persistence {
        memory,
        jdbc
    }

    @Getter
    @Setter
    public static class Extraction {
        private int maxTerms = 50;
        private Mode mode = Mode.regex;
        private Llm llm = new Llm();
    }

    public enum Mode {
        regex,
        llm
    }

    @Getter
    @Setter
    public static class Llm {
        private String prompt = "skill-extraction";
        private int maxInputChars = 4000;
        private Integer maxOutputTokens = 1024;
        private Double temperature = 0.0d;
    }

    @Getter
    @Setter
    public static class Matching {
        private double matchThreshold = 0.92d;
        private double aliasThreshold = 0.82d;
        private boolean remoteEmbeddingEnabled = false;
    }

    @Getter
    @Setter
    public static class Clustering {
        private double radius = 0.24d;
    }
}
