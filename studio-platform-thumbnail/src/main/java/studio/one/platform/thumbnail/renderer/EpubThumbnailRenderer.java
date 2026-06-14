package studio.one.platform.thumbnail.renderer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import studio.one.platform.thumbnail.ThumbnailFormats;
import studio.one.platform.thumbnail.ThumbnailGenerationException;
import studio.one.platform.thumbnail.ThumbnailImages;
import studio.one.platform.thumbnail.ThumbnailOptions;
import studio.one.platform.thumbnail.ThumbnailRenderLimits;
import studio.one.platform.thumbnail.ThumbnailRenderer;
import studio.one.platform.thumbnail.ThumbnailResult;
import studio.one.platform.thumbnail.ThumbnailSource;

public class EpubThumbnailRenderer implements ThumbnailRenderer {

    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ENTRIES = 2048;
    private static final long MAX_ENTRY_BYTES = 16L * 1024L * 1024L;
    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final Set<String> RASTER_MEDIA_TYPES = Set.of(
            "image/png", "image/jpeg", "image/jpg", "image/gif", "image/bmp", "image/x-ms-bmp");

    private final int fallbackMinWidth;
    private final int fallbackMinHeight;
    private final EpubSvgRasterizer svgRasterizer;

    public EpubThumbnailRenderer(int fallbackMinWidth, int fallbackMinHeight, EpubSvgRasterizer svgRasterizer) {
        this.fallbackMinWidth = Math.max(1, fallbackMinWidth);
        this.fallbackMinHeight = Math.max(1, fallbackMinHeight);
        this.svgRasterizer = svgRasterizer;
    }

    @Override
    public boolean supports(ThumbnailSource source) {
        return "application/epub+zip".equalsIgnoreCase(source.contentType())
                || source.filename().toLowerCase(Locale.ROOT).endsWith(".epub");
    }

    @Override
    public ThumbnailResult render(ThumbnailSource source, ThumbnailOptions options) {
        EpubPackage epub = readPackage(source.bytes(), options.maxSourceBytes(), source.filename());
        BufferedImage cover = findCover(epub, options).orElseGet(() -> defaultIcon(options.size()));
        BufferedImage scaled = ThumbnailImages.scale(cover, options.size());
        byte[] bytes = ThumbnailImages.write(scaled, options.format());
        return new ThumbnailResult(bytes, ThumbnailFormats.contentType(options.format()), options.format());
    }

    private EpubPackage readPackage(byte[] source, long maxSourceBytes, String filename) {
        Map<String, byte[]> entries = readEntries(source, maxSourceBytes, filename);
        byte[] containerBytes = entries.get(CONTAINER_PATH);
        if (containerBytes == null) {
            throw new ThumbnailGenerationException("EPUB package is missing " + CONTAINER_PATH);
        }
        Document container = parseXml(containerBytes, CONTAINER_PATH);
        Element rootfile = firstElement(container, "rootfile")
                .orElseThrow(() -> new ThumbnailGenerationException("EPUB container has no rootfile"));
        String opfPath = normalizeArchivePath(rootfile.getAttribute("full-path"), "EPUB OPF path", false);
        byte[] opfBytes = entries.get(opfPath);
        if (opfBytes == null) {
            throw new ThumbnailGenerationException("EPUB package is missing OPF entry: " + opfPath);
        }

        Document opf = parseXml(opfBytes, opfPath);
        List<ManifestItem> items = manifestItems(opf, opfPath);
        String epub2CoverId = elements(opf, "meta").stream()
                .filter(element -> "cover".equalsIgnoreCase(element.getAttribute("name")))
                .map(element -> element.getAttribute("content"))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
        return new EpubPackage(entries, items, epub2CoverId);
    }

    private Map<String, byte[]> readEntries(byte[] source, long maxSourceBytes, String filename) {
        long maxEntryBytes = Math.min(MAX_ENTRY_BYTES, maxSourceBytes);
        long totalBytes = 0;
        int entryCount = 0;
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(source))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new ThumbnailGenerationException(
                            "EPUB package exceeds max entry count " + MAX_ENTRIES + ": " + filename);
                }
                String name = normalizeArchivePath(entry.getName(), "EPUB ZIP entry", false);
                byte[] bytes = readBounded(zip, maxEntryBytes, name);
                totalBytes += bytes.length;
                if (totalBytes > maxSourceBytes) {
                    throw new ThumbnailGenerationException(
                            "EPUB package exceeds max extracted bytes " + maxSourceBytes + ": " + filename);
                }
                if (entries.putIfAbsent(name, bytes) != null) {
                    throw new ThumbnailGenerationException("EPUB package contains duplicate entry: " + name);
                }
            }
            return entries;
        } catch (IOException ex) {
            throw new ThumbnailGenerationException("Failed to read EPUB package", ex);
        }
    }

    private byte[] readBounded(ZipInputStream input, long maxBytes, String entryName) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new ThumbnailGenerationException(
                        "EPUB package entry exceeds max extracted bytes " + maxBytes + ": " + entryName);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private Document parseXml(byte[] bytes, String sourceDescription) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
        } catch (ParserConfigurationException | SAXException | IOException | RuntimeException ex) {
            throw new ThumbnailGenerationException("Failed to parse EPUB XML: " + sourceDescription, ex);
        }
    }

    private List<ManifestItem> manifestItems(Document opf, String opfPath) {
        List<ManifestItem> items = new ArrayList<>();
        for (Element element : elements(opf, "item")) {
            String id = element.getAttribute("id");
            String href = element.getAttribute("href");
            if (id.isBlank() || href.isBlank()) {
                continue;
            }
            String resolvedPath = resolveRelativePath(opfPath, href);
            items.add(new ManifestItem(
                    id,
                    resolvedPath,
                    element.getAttribute("media-type").toLowerCase(Locale.ROOT),
                    tokens(element.getAttribute("properties"))));
        }
        return items;
    }

    private Optional<BufferedImage> findCover(EpubPackage epub, ThumbnailOptions options) {
        Optional<ManifestItem> epub3 = epub.items().stream()
                .filter(item -> item.properties().contains("cover-image"))
                .findFirst();
        Optional<BufferedImage> cover = epub3.flatMap(item -> decode(epub, item, options));
        if (cover.isPresent()) {
            return cover;
        }

        if (!epub.epub2CoverId().isBlank()) {
            Optional<ManifestItem> epub2 = epub.items().stream()
                    .filter(item -> item.id().equals(epub.epub2CoverId()))
                    .findFirst();
            cover = epub2.flatMap(item -> decode(epub, item, options));
            if (cover.isPresent()) {
                return cover;
            }
        }

        return largestRasterFallback(epub, options);
    }

    private Optional<BufferedImage> largestRasterFallback(EpubPackage epub, ThumbnailOptions options) {
        ManifestItem selected = null;
        long selectedArea = -1;
        for (ManifestItem item : epub.items()) {
            if (!isRaster(item)) {
                continue;
            }
            byte[] bytes = epub.entries().get(item.path());
            ImageDimensions dimensions = bytes == null ? null : dimensions(bytes);
            if (dimensions == null
                    || dimensions.width() < fallbackMinWidth
                    || dimensions.height() < fallbackMinHeight) {
                continue;
            }
            try {
                ThumbnailRenderLimits.requirePixelsWithinLimit(
                        dimensions.width(), dimensions.height(), options.maxSourcePixels(), item.path());
            } catch (ThumbnailGenerationException ex) {
                continue;
            }
            long area = (long) dimensions.width() * dimensions.height();
            if (area > selectedArea) {
                selected = item;
                selectedArea = area;
            }
        }
        return selected == null ? Optional.empty() : decode(epub, selected, options);
    }

    private Optional<BufferedImage> decode(EpubPackage epub, ManifestItem item, ThumbnailOptions options) {
        byte[] bytes = epub.entries().get(item.path());
        if (bytes == null) {
            return Optional.empty();
        }
        try {
            if (isSvg(item)) {
                if (svgRasterizer == null) {
                    return Optional.empty();
                }
                return Optional.ofNullable(svgRasterizer.rasterize(
                        bytes, options.size(), options.maxSourcePixels(), item.path()));
            }
            if (!isRaster(item)) {
                return Optional.empty();
            }
            return Optional.ofNullable(readRaster(bytes, options.maxSourcePixels(), item.path()));
        } catch (ThumbnailGenerationException ex) {
            return Optional.empty();
        }
    }

    private BufferedImage readRaster(byte[] bytes, long maxSourcePixels, String sourceDescription) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                ThumbnailRenderLimits.requirePixelsWithinLimit(
                        width, height, maxSourcePixels, sourceDescription);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException ex) {
            return null;
        }
    }

    private ImageDimensions dimensions(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                return new ImageDimensions(reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null;
        }
    }

    private BufferedImage defaultIcon(int size) {
        int canvasSize = Math.max(64, size);
        BufferedImage image = new BufferedImage(canvasSize, canvasSize, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(55, 86, 149));
            graphics.fillRoundRect(0, 0, canvasSize, canvasSize, canvasSize / 7, canvasSize / 7);

            int margin = Math.max(8, canvasSize / 6);
            int top = margin;
            int bookWidth = canvasSize - margin * 2;
            int bookHeight = Math.max(24, canvasSize - margin * 2 - canvasSize / 5);
            graphics.setColor(Color.WHITE);
            graphics.fillRoundRect(margin, top, bookWidth, bookHeight, 8, 8);
            graphics.setColor(new Color(55, 86, 149));
            graphics.setStroke(new BasicStroke(Math.max(1f, canvasSize / 64f)));
            graphics.drawLine(canvasSize / 2, top + 4, canvasSize / 2, top + bookHeight - 4);

            String label = "EPUB";
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, canvasSize / 8)));
            FontMetrics metrics = graphics.getFontMetrics();
            graphics.setColor(Color.WHITE);
            graphics.drawString(label, (canvasSize - metrics.stringWidth(label)) / 2, canvasSize - margin / 2);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private boolean isRaster(ManifestItem item) {
        if (RASTER_MEDIA_TYPES.contains(item.mediaType())) {
            return true;
        }
        String path = item.path().toLowerCase(Locale.ROOT);
        return path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".jpeg")
                || path.endsWith(".gif")
                || path.endsWith(".bmp");
    }

    private boolean isSvg(ManifestItem item) {
        return "image/svg+xml".equals(item.mediaType())
                || item.path().toLowerCase(Locale.ROOT).endsWith(".svg");
    }

    private String resolveRelativePath(String opfPath, String href) {
        URI uri;
        try {
            uri = new URI(href);
        } catch (URISyntaxException ex) {
            throw new ThumbnailGenerationException("Invalid EPUB manifest href", ex);
        }
        if (uri.isAbsolute()
                || uri.getAuthority() != null
                || uri.getQuery() != null
                || uri.getPath() == null
                || uri.getPath().isBlank()) {
            throw new ThumbnailGenerationException("External or empty EPUB manifest href is not allowed");
        }
        String parent = "";
        int slash = opfPath.lastIndexOf('/');
        if (slash >= 0) {
            parent = opfPath.substring(0, slash + 1);
        }
        return normalizeArchivePath(parent + uri.getPath(), "EPUB manifest href", true);
    }

    private String normalizeArchivePath(String value, String description, boolean allowParentSegments) {
        if (value == null || value.isBlank()
                || value.startsWith("/")
                || value.startsWith("\\")
                || value.indexOf('\\') >= 0
                || value.indexOf('\0') >= 0) {
            throw new ThumbnailGenerationException(description + " is invalid");
        }
        String[] parts = value.split("/");
        List<String> normalized = new ArrayList<>();
        for (String part : parts) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!allowParentSegments || normalized.isEmpty()) {
                    throw new ThumbnailGenerationException(description + " escapes the EPUB package");
                }
                normalized.remove(normalized.size() - 1);
                continue;
            }
            if (part.indexOf(':') >= 0) {
                throw new ThumbnailGenerationException(description + " escapes the EPUB package");
            }
            normalized.add(part);
        }
        if (normalized.isEmpty()) {
            throw new ThumbnailGenerationException(description + " is invalid");
        }
        return String.join("/", normalized);
    }

    private Optional<Element> firstElement(Document document, String localName) {
        return elements(document, localName).stream().findFirst();
    }

    private List<Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        if (!result.isEmpty()) {
            return result;
        }
        NodeList fallback = document.getElementsByTagName(localName);
        for (int i = 0; i < fallback.getLength(); i++) {
            Node node = fallback.item(i);
            if (node instanceof Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private Set<String> tokens(String value) {
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<String> tokens = new java.util.LinkedHashSet<>();
        for (String token : value.trim().split("\\s+")) {
            tokens.add(token);
        }
        return Set.copyOf(tokens);
    }

    private record EpubPackage(Map<String, byte[]> entries, List<ManifestItem> items, String epub2CoverId) {
        private EpubPackage {
            entries = Map.copyOf(entries);
            items = List.copyOf(items);
        }
    }

    private record ManifestItem(String id, String path, String mediaType, Set<String> properties) {
    }

    private record ImageDimensions(int width, int height) {
    }
}
