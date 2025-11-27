package studio.one.platform.text.extractor.impl;

import java.util.Locale;

import studio.one.platform.text.extractor.FileParser;

public abstract class AbstractFileParser implements FileParser {

    protected String safeFilename(String filename) {
        return filename != null ? filename : "unknown";
    }

    protected String lower(String s) {
        return s != null ? s.toLowerCase(Locale.ROOT) : "";
    }

    protected boolean hasExtension(String filename, String... exts) {
        if (filename == null)
            return false;
        String lower = lower(filename);
        for (String ext : exts) {
            if (lower.endsWith(ext))
                return true;
        }
        return false;
    }

    protected boolean isContentType(String contentType, String... types) {
        if (contentType == null)
            return false;
        String lower = lower(contentType);
        for (String t : types) {
            if (lower.startsWith(t)) {
                return true;
            }
        }
        return false;
    }
}