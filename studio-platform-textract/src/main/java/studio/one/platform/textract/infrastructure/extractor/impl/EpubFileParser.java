package studio.one.platform.textract.infrastructure.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import studio.one.platform.textract.application.usecase.StructuredFileParser;
import studio.one.platform.textract.domain.error.FileParseException;
import studio.one.platform.textract.domain.model.BlockType;
import studio.one.platform.textract.domain.model.DocumentFormat;
import studio.one.platform.textract.domain.model.ParsedBlock;
import studio.one.platform.textract.domain.model.ParsedFile;

public class EpubFileParser extends AbstractFileParser implements StructuredFileParser {

    private static final String CONTAINER_PATH = "META-INF/container.xml";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_ENTRIES = 4096;
    private static final long MAX_ENTRY_BYTES = 16L * 1024L * 1024L;
    private static final long MAX_EXTRACTED_BYTES = 50L * 1024L * 1024L;

    @Override
    public boolean supports(String contentType, String filename) {
        return isContentType(contentType, "application/epub+zip") || hasExtension(filename, ".epub");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        try {
            Map<String, byte[]> entries = readEntries(bytes, filename);
            String opfPath = packagePath(entries);
            PackageDocument packageDocument = readPackage(entries, opfPath);
            List<String> contentPaths = contentPaths(packageDocument);
            List<ParsedBlock> blocks = new ArrayList<>();
            StringBuilder plainText = new StringBuilder();
            int order = 0;

            for (String contentPath : contentPaths) {
                byte[] content = entries.get(contentPath);
                if (content == null) {
                    continue;
                }
                org.jsoup.nodes.Document document =
                        Jsoup.parse(new String(content, StandardCharsets.UTF_8));
                document.select("script, style, noscript, template, nav, aside, footer, form").remove();
                Element root = semanticRoot(document);
                int elementIndex = 0;
                for (Element element : root.select("h1, h2, h3, h4, h5, h6, p, li")) {
                    String text = cleanText(element.text());
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    String sourceRef = "epub:" + contentPath + "#element[" + elementIndex++ + "]";
                    Map<String, Object> metadata = new LinkedHashMap<>(blockMetadata(sourceRef, order));
                    BlockType blockType = blockType(element);
                    if (blockType == BlockType.HEADING || blockType == BlockType.TITLE) {
                        metadata.put("headingLevel", headingLevel(element));
                    }
                    blocks.add(ParsedBlock.text(sourceRef, blockType, text, null, order, metadata));
                    if (plainText.length() > 0) {
                        plainText.append('\n');
                    }
                    plainText.append(text);
                    order++;
                }
            }

            Map<String, Object> metadata = new LinkedHashMap<>(fileMetadata(contentType, filename));
            metadata.put("packagePath", opfPath);
            metadata.put("contentDocumentCount", contentPaths.size());
            return new ParsedFile(
                    DocumentFormat.EPUB,
                    cleanText(plainText.toString()),
                    blocks,
                    metadata,
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    false);
        } catch (FileParseException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new FileParseException("Failed to parse EPUB: " + safeFilename(filename), ex);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private Map<String, byte[]> readEntries(byte[] bytes, String filename) {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long totalBytes = 0;
        int entryCount = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > MAX_ENTRIES) {
                    throw new FileParseException("EPUB exceeds max entry count: " + safeFilename(filename));
                }
                String path = normalizePath(entry.getName(), false);
                byte[] content = readEntry(zip, path);
                totalBytes += content.length;
                if (totalBytes > MAX_EXTRACTED_BYTES) {
                    throw new FileParseException("EPUB exceeds max extracted bytes: " + safeFilename(filename));
                }
                if (entries.putIfAbsent(path, content) != null) {
                    throw new FileParseException("EPUB contains duplicate entry: " + path);
                }
            }
            return entries;
        } catch (IOException ex) {
            throw new FileParseException("Failed to read EPUB ZIP: " + safeFilename(filename), ex);
        }
    }

    private byte[] readEntry(ZipInputStream input, String path) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new FileParseException("EPUB entry exceeds max extracted bytes: " + path);
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private String packagePath(Map<String, byte[]> entries) {
        byte[] container = entries.get(CONTAINER_PATH);
        if (container == null) {
            throw new FileParseException("EPUB is missing " + CONTAINER_PATH);
        }
        Document document = parseXml(container, CONTAINER_PATH);
        org.w3c.dom.Element rootfile = elements(document, "rootfile").stream()
                .findFirst()
                .orElseThrow(() -> new FileParseException("EPUB container has no rootfile"));
        String path = normalizePath(rootfile.getAttribute("full-path"), false);
        if (!entries.containsKey(path)) {
            throw new FileParseException("EPUB is missing package document: " + path);
        }
        return path;
    }

    private PackageDocument readPackage(Map<String, byte[]> entries, String opfPath) {
        Document document = parseXml(entries.get(opfPath), opfPath);
        Map<String, ManifestItem> manifest = new LinkedHashMap<>();
        for (org.w3c.dom.Element item : elements(document, "item")) {
            String id = item.getAttribute("id");
            String href = item.getAttribute("href");
            if (id.isBlank() || href.isBlank()) {
                continue;
            }
            manifest.put(id, new ManifestItem(
                    resolveRelativePath(opfPath, href),
                    item.getAttribute("media-type").toLowerCase(Locale.ROOT)));
        }
        List<String> spine = elements(document, "itemref").stream()
                .map(item -> item.getAttribute("idref"))
                .filter(id -> !id.isBlank())
                .toList();
        return new PackageDocument(entries, manifest, spine);
    }

    private List<String> contentPaths(PackageDocument epub) {
        Set<String> paths = new LinkedHashSet<>();
        for (String id : epub.spine()) {
            ManifestItem item = epub.manifest().get(id);
            if (item != null && isContentDocument(item) && epub.entries().containsKey(item.path())) {
                paths.add(item.path());
            }
        }
        if (paths.isEmpty()) {
            epub.manifest().values().stream()
                    .filter(this::isContentDocument)
                    .map(ManifestItem::path)
                    .filter(epub.entries()::containsKey)
                    .forEach(paths::add);
        }
        return List.copyOf(paths);
    }

    private boolean isContentDocument(ManifestItem item) {
        return item.mediaType().equals("application/xhtml+xml")
                || item.mediaType().equals("text/html")
                || item.path().toLowerCase(Locale.ROOT).endsWith(".xhtml")
                || item.path().toLowerCase(Locale.ROOT).endsWith(".html")
                || item.path().toLowerCase(Locale.ROOT).endsWith(".htm");
    }

    private Element semanticRoot(org.jsoup.nodes.Document document) {
        var roots = document.select("main, article, body");
        return roots.isEmpty() ? document : roots.first();
    }

    private BlockType blockType(Element element) {
        if ("h1".equals(element.tagName())) {
            return BlockType.TITLE;
        }
        if (element.tagName().matches("h[2-6]")) {
            return BlockType.HEADING;
        }
        if ("li".equals(element.tagName())) {
            return BlockType.LIST_ITEM;
        }
        return BlockType.PARAGRAPH;
    }

    private int headingLevel(Element element) {
        try {
            return Integer.parseInt(element.tagName().substring(1));
        } catch (RuntimeException ex) {
            return 2;
        }
    }

    private Document parseXml(byte[] bytes, String description) {
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
            throw new FileParseException("Failed to parse EPUB XML: " + description, ex);
        }
    }

    private List<org.w3c.dom.Element> elements(Document document, String localName) {
        NodeList nodes = document.getElementsByTagNameNS("*", localName);
        List<org.w3c.dom.Element> result = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof org.w3c.dom.Element element) {
                result.add(element);
            }
        }
        return result;
    }

    private String resolveRelativePath(String opfPath, String href) {
        try {
            URI uri = new URI(href);
            if (uri.isAbsolute() || uri.getAuthority() != null || uri.getQuery() != null
                    || uri.getPath() == null || uri.getPath().isBlank()) {
                throw new FileParseException("External EPUB manifest href is not allowed");
            }
            int slash = opfPath.lastIndexOf('/');
            String parent = slash < 0 ? "" : opfPath.substring(0, slash + 1);
            return normalizePath(parent + uri.getPath(), true);
        } catch (URISyntaxException ex) {
            throw new FileParseException("Invalid EPUB manifest href", ex);
        }
    }

    private String normalizePath(String value, boolean allowParentSegments) {
        if (value == null || value.isBlank() || value.startsWith("/") || value.startsWith("\\")
                || value.indexOf('\\') >= 0 || value.indexOf('\0') >= 0) {
            throw new FileParseException("Invalid EPUB package path");
        }
        List<String> normalized = new ArrayList<>();
        for (String part : value.split("/")) {
            if (part.isBlank() || ".".equals(part)) {
                continue;
            }
            if ("..".equals(part)) {
                if (!allowParentSegments || normalized.isEmpty()) {
                    throw new FileParseException("EPUB path escapes package root");
                }
                normalized.remove(normalized.size() - 1);
                continue;
            }
            if (part.indexOf(':') >= 0) {
                throw new FileParseException("External EPUB package path is not allowed");
            }
            normalized.add(part);
        }
        if (normalized.isEmpty()) {
            throw new FileParseException("Invalid EPUB package path");
        }
        return String.join("/", normalized);
    }

    private record ManifestItem(String path, String mediaType) {
    }

    private record PackageDocument(
            Map<String, byte[]> entries,
            Map<String, ManifestItem> manifest,
            List<String> spine) {
    }
}
