package studio.one.platform.markdown.application;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public enum MarkdownDocumentProfile {
    AUTO("Auto", "Low-cost baseline; runtime analyzers may adapt the extraction route.", "LOW",
            "structure-based", 1200, 150, "CHARACTER", false, null, "AUTO", false,
            List.of("pdf", "docx", "html", "pptx", "epub", "txt")),
    GENERAL_DOCUMENT("General document", "General text document with structure-aware chunking.", "LOW",
            "structure-based", 1200, 150, "CHARACTER", false, null, "AUTO", false,
            List.of("pdf", "docx", "html", "pptx", "epub", "txt")),
    PROFESSIONAL_BOOK("Professional book", "Long-form chapters, tables, footnotes, and wider context.", "MEDIUM",
            "structure-based", 1600, 200, "CHARACTER", false, null, "AUTO", false,
            List.of("pdf", "docx", "html", "epub")),
    TEXTBOOK("Textbook", "Units, concepts, examples, questions, choices, and page provenance.", "MEDIUM",
            "structure-based", 1200, 150, "CHARACTER", false, "kor+eng", "AUTO", false,
            List.of("pdf", "docx", "pptx")),
    MATH_TEXTBOOK("Math textbook", "Korean OCR, math OCR, formula correction, and strict provenance.", "HIGH",
            "structure-based", 1200, 150, "CHARACTER", true, "kor+eng", "FORCE", true,
            List.of("pdf")),
    SCANNED_DOCUMENT("Scanned document", "Forced OCR with page and bounding-box provenance.", "HIGH",
            "structure-based", 1000, 120, "CHARACTER", true, "kor+eng", "FORCE", false,
            List.of("pdf", "png", "jpg", "jpeg", "tiff")),
    PRESENTATION("Presentation", "Slide and shape structure with compact slide-oriented chunks.", "LOW",
            "structure-based", 900, 80, "CHARACTER", false, null, "AUTO", false,
            List.of("pptx")),
    TECHNICAL_MANUAL("Technical manual", "Procedures, code, tables, warnings, and section context.", "MEDIUM",
            "structure-based", 1200, 150, "CHARACTER", false, null, "AUTO", false,
            List.of("pdf", "docx", "html", "epub"));

    public static final String VERSION = "v1";

    private final String displayName;
    private final String description;
    private final String costTier;
    private final String chunkingStrategy;
    private final int chunkMaxSize;
    private final int chunkOverlap;
    private final String chunkUnit;
    private final boolean ocrRequired;
    private final String ocrLanguage;
    private final String ocrMode;
    private final boolean mathVisionCorrection;
    private final List<String> supportedFormats;

    MarkdownDocumentProfile(String displayName, String description, String costTier,
            String chunkingStrategy, int chunkMaxSize, int chunkOverlap, String chunkUnit,
            boolean ocrRequired, String ocrLanguage, String ocrMode, boolean mathVisionCorrection,
            List<String> supportedFormats) {
        this.displayName = displayName;
        this.description = description;
        this.costTier = costTier;
        this.chunkingStrategy = chunkingStrategy;
        this.chunkMaxSize = chunkMaxSize;
        this.chunkOverlap = chunkOverlap;
        this.chunkUnit = chunkUnit;
        this.ocrRequired = ocrRequired;
        this.ocrLanguage = ocrLanguage;
        this.ocrMode = ocrMode;
        this.mathVisionCorrection = mathVisionCorrection;
        this.supportedFormats = List.copyOf(supportedFormats);
    }

    public static MarkdownDocumentProfile parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(profile -> profile.name().equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported documentProfile: " + value));
    }

    public MarkdownDocumentProfile resolved() {
        return this == AUTO ? GENERAL_DOCUMENT : this;
    }

    public MarkdownDocumentProfileDescriptor descriptor() {
        return new MarkdownDocumentProfileDescriptor(name(), displayName, description, VERSION, costTier,
                supportedFormats, chunkingStrategy, chunkMaxSize, chunkOverlap, chunkUnit,
                ocrRequired, ocrLanguage, ocrMode, mathVisionCorrection);
    }

    String chunkingStrategy() { return chunkingStrategy; }
    int chunkMaxSize() { return chunkMaxSize; }
    int chunkOverlap() { return chunkOverlap; }
    String chunkUnit() { return chunkUnit; }
    boolean ocrRequired() { return ocrRequired; }
    String ocrLanguage() { return ocrLanguage; }
    String ocrMode() { return ocrMode; }
    boolean mathVisionCorrection() { return mathVisionCorrection; }
    public String costTier() { return costTier; }
}
