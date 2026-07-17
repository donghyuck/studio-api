package studio.one.platform.markdown.application;

public record MarkdownPagePreviewBounds(double x0, double y0, double x1, double y1) {

    public MarkdownPagePreviewBounds {
        if (!Double.isFinite(x0) || !Double.isFinite(y0) || !Double.isFinite(x1) || !Double.isFinite(y1)
                || x1 <= x0 || y1 <= y0) {
            throw new IllegalArgumentException("Preview bounds must be finite x0,y0,x1,y1 coordinates");
        }
    }
}
