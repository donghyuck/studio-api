package studio.one.platform.markdown.autoconfigure;

import java.util.ArrayList;
import java.util.List;

import studio.one.platform.chunking.core.NormalizedBlock;
import studio.one.platform.chunking.core.NormalizedBlockType;
import studio.one.platform.chunking.core.NormalizedDocument;

public class NormalizedMarkdownRenderer {
    private final RenderedMarkdownPostProcessor postProcessor = new RenderedMarkdownPostProcessor();

    public String render(NormalizedDocument document, String fallbackMarkdown) {
        if (document == null || document.blocks().isEmpty()) {
            return postProcessor.postProcess(fallback(fallbackMarkdown, document));
        }
        List<String> parts = new ArrayList<>();
        for (NormalizedBlock block : document.blocks()) {
            String rendered = render(block);
            if (!rendered.isBlank()) {
                parts.add(rendered);
            }
        }
        String markdown = String.join("\n\n", parts).trim();
        return postProcessor.postProcess(markdown);
    }

    private String render(NormalizedBlock block) {
        if (excludedFromMarkdown(block)) {
            return "";
        }
        String text = clean(block);
        if (text.isBlank()) {
            return "";
        }
        return switch (block.type()) {
            case TITLE -> "# " + stripHeading(text);
            case HEADING -> "#".repeat(headingLevel(block)) + " " + stripHeading(text);
            case LIST_ITEM -> "- " + stripListMarker(text);
            case TABLE -> table(block, text);
            case IMAGE -> image(block, text);
            case IMAGE_CAPTION -> "_%s_".formatted(text);
            case PAGE -> page(block, text);
            default -> paragraph(text);
        };
    }

    private boolean excludedFromMarkdown(NormalizedBlock block) {
        return block != null && (Boolean.TRUE.equals(block.metadata().get("searchContextOnly"))
                || Boolean.TRUE.equals(block.metadata().get("discardedOcrNoise")));
    }

    private String table(NormalizedBlock block, String fallback) {
        Object markdown = block.metadata().get("markdown");
        if (markdown instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return fallback;
    }

    private String paragraph(String text) {
        if (problemHeading(text)) {
            return "### " + text.replaceFirst("\\s*[:.)-]$", "").trim();
        }
        if (choiceItem(text)) {
            return "- " + text;
        }
        return text;
    }

    private boolean problemHeading(String text) {
        return text != null && text.trim().matches("^(?:문제|예제|유형)\\s*\\d{1,4}(?:\\s*[:.)-])?$");
    }

    private boolean choiceItem(String text) {
        return text != null && text.trim().matches("^\\([1-9][0-9]?\\)\\s+[가-힣A-Za-z0-9].*");
    }

    private String image(NormalizedBlock block, String text) {
        String alt = string(block.metadata().get("altText"), text);
        String src = string(block.metadata().get("src"), block.sourceRef());
        if (src.isBlank()) {
            return "[image: " + alt + "]";
        }
        return "![" + escapeAlt(alt) + "](" + src + ")";
    }

    private String page(NormalizedBlock block, String text) {
        if (block.page() == null) {
            return text;
        }
        return "<!-- page: " + block.page() + " -->\n\n" + text;
    }

    private int headingLevel(NormalizedBlock block) {
        Object level = block.metadata().get("level");
        if (level instanceof Number number) {
            return Math.max(1, Math.min(6, number.intValue()));
        }
        return 2;
    }

    private String fallback(String fallbackMarkdown, NormalizedDocument document) {
        if (fallbackMarkdown != null && !fallbackMarkdown.isBlank()) {
            return fallbackMarkdown.trim();
        }
        return document == null ? "" : document.chunkableText();
    }

    private String stripHeading(String text) {
        return text.replaceFirst("^#{1,6}\\s+", "").trim();
    }

    private String stripListMarker(String text) {
        return text.replaceFirst("^[-*+]\\s+", "").trim();
    }

    private String clean(NormalizedBlock block) {
        String text = block == null ? "" : block.text();
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = ocrOrigin(block) ? OcrTextNormalizer.normalize(text) : normalizeMathArtifacts(text);
        return normalized
                .replaceAll("(?m)^\\s*(@\\$)", "\\$")
                .replaceAll("(?m)(\"\\$|\\$\")", "\\$")
                .trim();
    }

    private boolean ocrOrigin(NormalizedBlock block) {
        if (block == null) {
            return false;
        }
        Object originalType = block.metadata().get("originalType");
        return block.type() == NormalizedBlockType.OCR_TEXT
                || "OCR_TEXT".equals(originalType)
                || Boolean.TRUE.equals(block.metadata().get("ocrApplied"))
                || block.metadata().containsKey("reclassifiedFrom");
    }

    private String normalizeMathArtifacts(String text) {
        return text
                .replace('—', '-')
                .replace('–', '-')
                .replace('−', '-')
                .replaceAll("(?<=[0-9A-Za-z)\\]}])ㅡ(?=[0-9A-Za-z({\\[])", "-")
                .replaceAll("(?<=\\d)\\s*ㅡ\\s*(?=\\d|[A-Za-z])", "-")
                .replaceAll("(?<=[A-Za-z])\\s*ㅡ\\s*(?=\\d)", "-")
                .replaceAll("^\\s*ㅡ\\s*(?=\\d|[A-Za-z])", "-")
                .replaceAll("(?<=[0-9A-Za-z])\\s+ㅡ\\s*(?=[0-9A-Za-z])", " - ")
                .replaceAll("(?<=[=+\\-*/^({\\[])\\s+ㅡ\\s*(?=[0-9A-Za-z])", " -")
                .replaceAll("(?<=[=+\\-*/^({\\[])ㅡ(?=[0-9A-Za-z])", "-")
                .trim();
    }

    private String string(Object value, String fallback) {
        if (value == null) {
            return fallback == null ? "" : fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isBlank() ? fallback == null ? "" : fallback : text;
    }

    private String escapeAlt(String alt) {
        return alt == null ? "" : alt.replace("[", "").replace("]", "");
    }
}
