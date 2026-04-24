package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedImage;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParseWarning;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

/**
 * HWP/HWPX parser based on rhwp's parser flow:
 * CFB/ZIP container -> section records/XML -> paragraph/control traversal.
 */
public class HwpHwpxFileParser extends AbstractFileParser implements StructuredFileParser {

    private static final int HWPTAG_BEGIN = 0x010;
    private static final int HWPTAG_BIN_DATA = HWPTAG_BEGIN + 2;
    private static final int HWPTAG_PARA_HEADER = HWPTAG_BEGIN + 50;
    private static final int HWPTAG_PARA_TEXT = HWPTAG_BEGIN + 51;
    private static final byte[] HWP_CFB_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1
    };

    @Override
    public boolean supports(String contentType, String filename) {
        String name = lower(filename);
        String type = lower(contentType);
        return name.endsWith(".hwp")
                || name.endsWith(".hwpx")
                || type.contains("hwp")
                || type.contains("hwpx");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        if (looksLikeHwpx(bytes, filename)) {
            return parseHwpx(bytes, contentType, filename);
        }
        if (looksLikeHwp(bytes, filename)) {
            return parseHwp(bytes, contentType, filename);
        }
        throw new FileParseException("Unsupported HWP/HWPX file: " + safeFilename(filename));
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private ParsedFile parseHwpx(byte[] bytes, String contentType, String filename) throws FileParseException {
        try {
            Map<String, byte[]> entries = readZipEntries(bytes);
            PackageInfo packageInfo = parsePackageInfo(entries);
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ExtractedTable> tables = new ArrayList<>();
            List<ExtractedImage> images = new ArrayList<>();
            List<ParseWarning> warnings = new ArrayList<>();
            StringBuilder plain = new StringBuilder();

            for (int i = 0; i < packageInfo.sectionFiles().size(); i++) {
                String sectionPath = resolvePackagePath(entries, packageInfo.sectionFiles().get(i));
                byte[] sectionBytes = entries.get(sectionPath);
                if (sectionBytes == null) {
                    warnings.add(ParseWarning.partial(
                            "hwpx.section.missing",
                            "Missing section file",
                            sectionPath,
                            Map.of()));
                    continue;
                }
                Document section = parseXml(sectionBytes);
                Element root = section.getDocumentElement();
                parseHwpxContainer(root, "section[" + i + "]", plain, blocks, tables, packageInfo);
            }

            for (PackageItem item : packageInfo.binDataItems()) {
                String path = resolvePackagePath(entries, item.href());
                byte[] data = entries.get(path);
                List<String> sourceRefs = packageInfo.referencedImages().getOrDefault(item.id(), List.of());
                Map<String, Object> imageMetadata = new LinkedHashMap<>();
                imageMetadata.put("bytes", data == null ? 0 : data.length);
                imageMetadata.put(ExtractedImage.KEY_PACKAGE_ID, item.id());
                imageMetadata.put(ExtractedImage.KEY_BIN_DATA_REF, path);
                if (sourceRefs.size() == 1) {
                    imageMetadata.put(ExtractedImage.KEY_SOURCE_REF, sourceRefs.get(0));
                } else if (!sourceRefs.isEmpty()) {
                    imageMetadata.put(ExtractedImage.KEY_SOURCE_REFS, List.copyOf(sourceRefs));
                }
                images.add(new ExtractedImage(
                        "bindata/" + item.id(),
                        item.mediaType(),
                        path,
                        null,
                        null,
                        imageMetadata));
                if (sourceRefs.isEmpty()) {
                    warnings.add(ParseWarning.partial(
                            "image.mapping.partial",
                            "Image binary has no paragraph/source mapping",
                            path,
                            Map.of(ExtractedImage.KEY_PACKAGE_ID, item.id())));
                }
            }

            return new ParsedFile(
                    DocumentFormat.HWPX,
                    cleanText(plain.toString()),
                    blocks,
                    metadata(contentType, filename, "hwpx"),
                    warnings,
                    List.of(),
                    tables,
                    images,
                    false);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new FileParseException("Failed to parse HWPX file: " + safeFilename(filename), e);
        }
    }

    private void parseHwpxContainer(
            Node node,
            String path,
            StringBuilder plain,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            PackageInfo packageInfo) {
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            return;
        }
        String name = localName(node);
        if ("p".equals(name)) {
            parseHwpxParagraph((Element) node, path, plain, blocks, tables, packageInfo);
            return;
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            parseHwpxContainer(children.item(i), path + "/" + localName(children.item(i)) + "[" + i + "]",
                    plain, blocks, tables, packageInfo);
        }
    }

    private void parseHwpxParagraph(
            Element paragraph,
            String path,
            StringBuilder plain,
            List<ParsedBlock> blocks,
            List<ExtractedTable> tables,
            PackageInfo packageInfo) {
        StringBuilder paragraphText = new StringBuilder();
        NodeList children = paragraph.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = localName(child);
            if ("run".equals(name)) {
                collectHwpxRunText(child, paragraphText);
            } else if ("tbl".equals(name)) {
                ExtractedTable table = parseHwpxTable((Element) child, path + "/tbl[" + i + "]");
                tables.add(table);
                blocks.add(new ParsedBlock(table.path(), BlockType.TABLE, table.path(), table.markdown(),
                        null, List.of(), ParsedBlock.metadata(table.path(), blocks.size(), null, null, table.metadata())));
                if (!table.markdown().isBlank()) {
                    paragraphText.append('\n').append(table.markdown()).append('\n');
                }
            } else if ("pic".equals(name)) {
                Optional<String> imageRef = findHwpxImageRef((Element) child);
                imageRef.ifPresent(ref -> paragraphText.append("[IMAGE:").append(ref).append("]"));
                if (imageRef.isPresent()) {
                    packageInfo.referencedImages()
                            .computeIfAbsent(imageRef.get(), ignored -> new ArrayList<>())
                            .add(path + "/pic[" + i + "]");
                }
            }
        }
        String text = cleanText(paragraphText.toString());
        if (!text.isBlank()) {
            if (plain.length() > 0) {
                plain.append('\n');
            }
            plain.append(text);
            blocks.add(ParsedBlock.text(path, BlockType.PARAGRAPH, text, null, blocks.size(), Map.of()));
        }
    }

    private void collectHwpxRunText(Node run, StringBuilder text) {
        NodeList children = run.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                text.append(child.getTextContent());
                continue;
            }
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = localName(child);
            if ("t".equals(name)) {
                text.append(child.getTextContent());
            } else if ("lineBreak".equals(name) || "br".equals(name)) {
                text.append('\n');
            } else if ("tab".equals(name)) {
                text.append('\t');
            } else {
                collectHwpxRunText(child, text);
            }
        }
    }

    private ExtractedTable parseHwpxTable(Element table, String path) {
        List<ExtractedTableCell> cells = new ArrayList<>();
        List<String> markdownRows = new ArrayList<>();
        List<Element> rows = directChildren(table, "tr");
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<Element> tableCells = directChildren(rows.get(rowIndex), "tc");
            List<String> markdownCells = new ArrayList<>();
            for (int colIndex = 0; colIndex < tableCells.size(); colIndex++) {
                Element cell = tableCells.get(colIndex);
                int rowSpan = attrInt(firstDescendant(cell, "cellSpan").orElse(null), "rowSpan", 1);
                int colSpan = attrInt(firstDescendant(cell, "cellSpan").orElse(null), "colSpan", 1);
                String text = collectDescendantText(cell);
                cells.add(new ExtractedTableCell(rowIndex, colIndex, rowSpan, colSpan, text, Map.of()));
                markdownCells.add(text.replace('\n', ' '));
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
        }
        return new ExtractedTable(
                path,
                String.join("\n", markdownRows),
                cells,
                Map.of(
                        ExtractedTable.KEY_FORMAT, "hwpx",
                        ExtractedTable.KEY_SOURCE_REF, path));
    }

    private String collectDescendantText(Node node) {
        StringBuilder text = new StringBuilder();
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.TEXT_NODE) {
                text.append(child.getTextContent());
            } else if (child.getNodeType() == Node.ELEMENT_NODE) {
                String name = localName(child);
                if ("t".equals(name)) {
                    text.append(child.getTextContent());
                } else if ("lineBreak".equals(name) || "br".equals(name)) {
                    text.append('\n');
                } else {
                    String nested = collectDescendantText(child);
                    if (!nested.isBlank()) {
                        if (text.length() > 0 && text.charAt(text.length() - 1) != '\n') {
                            text.append(' ');
                        }
                        text.append(nested);
                    }
                }
            }
        }
        return cleanText(text.toString());
    }

    private Optional<String> findHwpxImageRef(Element picture) {
        Optional<Element> image = firstDescendant(picture, "img");
        if (image.isEmpty()) {
            image = firstDescendant(picture, "image");
        }
        return image.map(e -> attr(e, "binaryItemIDRef")).filter(s -> !s.isBlank());
    }

    private ParsedFile parseHwp(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (POIFSFileSystem fs = new POIFSFileSystem(new ByteArrayInputStream(bytes))) {
            DirectoryEntry root = fs.getRoot();
            byte[] header = readDocument(root, "FileHeader");
            HwpFlags flags = parseHwpFlags(header);
            List<ParseWarning> warnings = new ArrayList<>();
            if (flags.encrypted()) {
                warnings.add(ParseWarning.error(
                        "hwp.encrypted",
                        "Encrypted HWP is not supported",
                        "FileHeader",
                        Map.of()));
            }
            if (flags.distribution()) {
                warnings.add(ParseWarning.partial(
                        "hwp.distribution",
                        "Distribution HWP ViewText decryption is not supported",
                        "FileHeader",
                        Map.of()));
            }

            List<BinDataRef> binDataRefs = readHwpBinDataRefs(root, flags.compressed(), warnings);
            List<ParsedBlock> blocks = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            for (int sectionIndex = 0; ; sectionIndex++) {
                Optional<byte[]> section = readHwpSection(root, sectionIndex, flags.compressed(), flags.distribution());
                if (section.isEmpty()) {
                    break;
                }
                parseHwpSection(section.get(), sectionIndex, plain, blocks, warnings);
            }

            List<ExtractedImage> images = readHwpImages(root, binDataRefs);
            return new ParsedFile(
                    DocumentFormat.HWP,
                    cleanText(plain.toString()),
                    blocks,
                    metadata(contentType, filename, "hwp"),
                    warnings,
                    List.of(),
                    List.of(),
                    images,
                    false);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse HWP file: " + safeFilename(filename), e);
        }
    }

    private void parseHwpSection(
            byte[] section,
            int sectionIndex,
            StringBuilder plain,
            List<ParsedBlock> blocks,
            List<ParseWarning> warnings) {
        List<HwpRecord> records = readHwpRecords(section, warnings);
        int paragraphOrder = 0;
        for (int i = 0; i < records.size(); i++) {
            HwpRecord record = records.get(i);
            if (record.tagId() != HWPTAG_PARA_HEADER) {
                continue;
            }
            int baseLevel = record.level();
            String text = "";
            int j = i + 1;
            while (j < records.size() && records.get(j).level() > baseLevel) {
                if (records.get(j).level() == baseLevel + 1 && records.get(j).tagId() == HWPTAG_PARA_TEXT) {
                    text = parseHwpParaText(records.get(j).data());
                    break;
                }
                j++;
            }
            text = cleanText(text);
            if (!text.isBlank()) {
                String path = "section[" + sectionIndex + "]/paragraph[" + paragraphOrder + "]";
                if (plain.length() > 0) {
                    plain.append('\n');
                }
                plain.append(text);
                blocks.add(ParsedBlock.text(path, BlockType.PARAGRAPH, text, null, blocks.size(), Map.of()));
                paragraphOrder++;
            }
        }
    }

    private String parseHwpParaText(byte[] data) {
        StringBuilder text = new StringBuilder();
        int pos = 0;
        while (pos + 1 < data.length) {
            int ch = u16(data, pos);
            if (ch == 0x0000) {
                pos += 2;
            } else if (ch == 0x0009) {
                text.append('\t');
                pos += 16;
            } else if (ch == 0x000A) {
                text.append('\n');
                pos += 2;
            } else if (ch == 0x000D) {
                break;
            } else if (isHwpExtendedControl(ch)) {
                pos += 16;
            } else if (ch < 0x0020) {
                if (ch == 0x0018) {
                    text.append('\u00A0');
                } else if (ch == 0x0019) {
                    text.append(' ');
                } else if (ch == 0x001E) {
                    text.append('-');
                } else if (ch == 0x001F) {
                    text.append('\u2007');
                }
                pos += 2;
            } else if (Character.isHighSurrogate((char) ch) && pos + 3 < data.length) {
                int low = u16(data, pos + 2);
                if (Character.isLowSurrogate((char) low)) {
                    text.append(Character.toChars(Character.toCodePoint((char) ch, (char) low)));
                    pos += 4;
                } else {
                    text.append((char) ch);
                    pos += 2;
                }
            } else {
                text.append((char) ch);
                pos += 2;
            }
        }
        return text.toString();
    }

    private boolean isHwpExtendedControl(int ch) {
        return (ch >= 1 && ch <= 8) || (ch >= 11 && ch <= 12) || (ch >= 14 && ch <= 23);
    }

    private List<BinDataRef> readHwpBinDataRefs(DirectoryEntry root, boolean compressed, List<ParseWarning> warnings) {
        if (!hasEntry(root, "DocInfo")) {
            return List.of();
        }
        try {
            byte[] docInfo = readDocument(root, "DocInfo");
            if (compressed) {
                docInfo = inflate(docInfo);
            }
            List<HwpRecord> records = readHwpRecords(docInfo, warnings);
            List<BinDataRef> refs = new ArrayList<>();
            for (HwpRecord record : records) {
                if (record.tagId() == HWPTAG_BIN_DATA) {
                    parseBinDataRecord(record.data()).ifPresent(refs::add);
                }
            }
            return refs;
        } catch (RuntimeException | IOException e) {
            warnings.add(ParseWarning.partial(
                    "hwp.docinfo.parse",
                    "Failed to parse DocInfo BinData",
                    "DocInfo",
                    Map.of("error", e.getMessage())));
            return List.of();
        }
    }

    private Optional<BinDataRef> parseBinDataRecord(byte[] data) {
        if (data.length < 4) {
            return Optional.empty();
        }
        int attr = u16(data, 0);
        int dataType = attr & 0x000F;
        if (dataType != 1 && dataType != 2) {
            return Optional.empty();
        }
        int storageId = u16(data, 2);
        String extension = readHwpString(data, 4).orElse("dat");
        return Optional.of(new BinDataRef(storageId, extension));
    }

    private Optional<String> readHwpString(byte[] data, int offset) {
        if (offset + 2 > data.length) {
            return Optional.empty();
        }
        int length = u16(data, offset);
        int byteLength = length * 2;
        int start = offset + 2;
        if (length <= 0 || start + byteLength > data.length) {
            return Optional.empty();
        }
        return Optional.of(new String(data, start, byteLength, StandardCharsets.UTF_16LE));
    }

    private List<ExtractedImage> readHwpImages(DirectoryEntry root, List<BinDataRef> binDataRefs) throws IOException {
        if (!hasEntry(root, "BinData") || !(root.getEntry("BinData") instanceof DirectoryEntry binDataDir)) {
            return List.of();
        }
        Map<Integer, BinDataRef> refsByStorageId = new HashMap<>();
        for (BinDataRef ref : binDataRefs) {
            refsByStorageId.put(ref.storageId(), ref);
        }
        List<ExtractedImage> images = new ArrayList<>();
        for (Entry entry : binDataDir) {
            if (entry.isDirectoryEntry()) {
                continue;
            }
            byte[] data = readDocument(binDataDir, entry.getName());
            String ext = extensionOf(entry.getName());
            int id = storageIdFromBinName(entry.getName()).orElse(images.size() + 1);
            BinDataRef ref = refsByStorageId.get(id);
            images.add(new ExtractedImage(
                    "bindata/" + entry.getName(),
                    contentTypeForExtension(ref == null ? ext : ref.extension()),
                    entry.getName(),
                    null,
                    null,
                    Map.of(
                            "bytes", data.length,
                            "storageId", id,
                            ExtractedImage.KEY_BIN_DATA_REF, entry.getName(),
                            ExtractedImage.KEY_SOURCE_REF, "bindata/" + entry.getName())));
        }
        return images;
    }

    private Optional<byte[]> readHwpSection(DirectoryEntry root, int sectionIndex, boolean compressed, boolean distribution)
            throws IOException {
        String[] candidates = distribution
                ? new String[] { "ViewText/Section" + sectionIndex, "BodyText/Section" + sectionIndex, "Section" + sectionIndex }
                : new String[] { "BodyText/Section" + sectionIndex, "Section" + sectionIndex };
        for (String candidate : candidates) {
            Optional<byte[]> raw = readDocumentPath(root, candidate);
            if (raw.isPresent()) {
                return Optional.of(compressed && !candidate.startsWith("ViewText/")
                        ? inflate(raw.get())
                        : raw.get());
            }
        }
        return Optional.empty();
    }

    private HwpFlags parseHwpFlags(byte[] header) {
        if (header.length < 40) {
            return new HwpFlags(false, false, false);
        }
        int flags = i32(header, 36);
        return new HwpFlags((flags & 0x01) != 0, (flags & 0x02) != 0, (flags & 0x04) != 0);
    }

    private List<HwpRecord> readHwpRecords(byte[] data, List<ParseWarning> warnings) {
        List<HwpRecord> records = new ArrayList<>();
        int pos = 0;
        while (pos + 4 <= data.length) {
            int header = i32(data, pos);
            pos += 4;
            int tagId = header & 0x3FF;
            int level = (header >>> 10) & 0x3FF;
            int size = (header >>> 20) & 0xFFF;
            if (size == 0xFFF) {
                if (pos + 4 > data.length) {
                    warnings.add(ParseWarning.partial(
                            "hwp.record.eof",
                            "Record extended size is truncated",
                            "",
                            Map.of()));
                    break;
                }
                size = i32(data, pos);
                pos += 4;
            }
            if (size < 0 || pos + size > data.length) {
                warnings.add(ParseWarning.partial(
                        "hwp.record.eof",
                        "Record data is truncated",
                        "",
                        Map.of("tagId", tagId, "size", size)));
                break;
            }
            byte[] recordData = new byte[size];
            System.arraycopy(data, pos, recordData, 0, size);
            pos += size;
            records.add(new HwpRecord(tagId, level, recordData));
        }
        return records;
    }

    private Map<String, byte[]> readZipEntries(byte[] bytes) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    entries.put(entry.getName(), zip.readAllBytes());
                }
            }
        }
        return entries;
    }

    private PackageInfo parsePackageInfo(Map<String, byte[]> entries)
            throws ParserConfigurationException, IOException, SAXException {
        byte[] content = entries.getOrDefault("Contents/content.hpf", entries.get("content.hpf"));
        if (content == null) {
            List<String> fallbackSections = entries.keySet().stream()
                    .filter(name -> name.toLowerCase(Locale.ROOT).contains("section"))
                    .sorted()
                    .toList();
            return new PackageInfo(fallbackSections, List.of(), new HashMap<>());
        }
        Document doc = parseXml(content);
        List<PackageItem> allItems = new ArrayList<>();
        List<String> spine = new ArrayList<>();
        NodeList nodes = doc.getDocumentElement().getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element e = (Element) nodes.item(i);
            String name = localName(e);
            if ("item".equals(name)) {
                String id = attr(e, "id");
                String href = attr(e, "href");
                if (!href.isBlank()) {
                    allItems.add(new PackageItem(id, href, attr(e, "media-type")));
                }
            } else if ("itemref".equals(name)) {
                String idref = attr(e, "idref");
                if (!idref.isBlank()) {
                    spine.add(idref);
                }
            }
        }
        List<String> sectionFiles = new ArrayList<>();
        for (String idref : spine) {
            allItems.stream()
                    .filter(item -> Objects.equals(item.id(), idref))
                    .filter(item -> item.href().toLowerCase(Locale.ROOT).contains("section"))
                    .findFirst()
                    .map(PackageItem::href)
                    .ifPresent(sectionFiles::add);
        }
        if (sectionFiles.isEmpty()) {
            allItems.stream()
                    .filter(item -> item.href().toLowerCase(Locale.ROOT).contains("section"))
                    .map(PackageItem::href)
                    .sorted()
                    .forEach(sectionFiles::add);
        }
        List<PackageItem> binData = allItems.stream()
                .filter(item -> item.href().contains("BinData/") || item.href().startsWith("BinData/"))
                .toList();
        return new PackageInfo(sectionFiles, binData, new HashMap<>());
    }

    private Document parseXml(byte[] bytes) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    }

    private String resolvePackagePath(Map<String, byte[]> entries, String href) {
        if (entries.containsKey(href)) {
            return href;
        }
        String contentsPath = "Contents/" + href;
        if (entries.containsKey(contentsPath)) {
            return contentsPath;
        }
        return href;
    }

    private List<Element> directChildren(Element parent, String localName) {
        List<Element> children = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && localName.equals(localName(node))) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private Optional<Element> firstDescendant(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            Element node = (Element) nodes.item(i);
            if (localName.equals(localName(node))) {
                return Optional.of(node);
            }
        }
        return Optional.empty();
    }

    private String localName(Node node) {
        if (node == null) {
            return "";
        }
        String local = node.getLocalName();
        String name = local == null ? node.getNodeName() : local;
        int colon = name.indexOf(':');
        return colon >= 0 ? name.substring(colon + 1) : name;
    }

    private String attr(Element element, String name) {
        return element == null || !element.hasAttribute(name) ? "" : element.getAttribute(name);
    }

    private int attrInt(Element element, String name, int defaultValue) {
        try {
            String value = attr(element, name);
            return value.isBlank() ? defaultValue : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private Map<String, Object> metadata(String contentType, String filename, String sourceFormat) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        if (filename != null && !filename.isBlank()) {
            metadata.put("filename", filename);
        }
        if (contentType != null && !contentType.isBlank()) {
            metadata.put("contentType", contentType);
        }
        metadata.put("sourceFormat", sourceFormat);
        return metadata;
    }

    private boolean looksLikeHwpx(byte[] bytes, String filename) {
        return lower(filename).endsWith(".hwpx")
                || bytes.length >= 4 && bytes[0] == 0x50 && bytes[1] == 0x4B;
    }

    private boolean looksLikeHwp(byte[] bytes, String filename) {
        if (lower(filename).endsWith(".hwp")) {
            return true;
        }
        if (bytes.length < HWP_CFB_SIGNATURE.length) {
            return false;
        }
        for (int i = 0; i < HWP_CFB_SIGNATURE.length; i++) {
            if (bytes[i] != HWP_CFB_SIGNATURE[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean hasEntry(DirectoryEntry dir, String name) {
        try {
            return dir.hasEntry(name);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Optional<byte[]> readDocumentPath(DirectoryEntry root, String path) throws IOException {
        String[] parts = path.split("/");
        DirectoryEntry dir = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!hasEntry(dir, parts[i]) || !(dir.getEntry(parts[i]) instanceof DirectoryEntry next)) {
                return Optional.empty();
            }
            dir = next;
        }
        if (!hasEntry(dir, parts[parts.length - 1])) {
            return Optional.empty();
        }
        return Optional.of(readDocument(dir, parts[parts.length - 1]));
    }

    private byte[] readDocument(DirectoryEntry dir, String name) throws IOException {
        try (DocumentInputStream in = new DocumentInputStream((DocumentEntry) dir.getEntry(name))) {
            return in.readAllBytes();
        }
    }

    private byte[] inflate(byte[] raw) throws IOException {
        try {
            return inflate(raw, true);
        } catch (IOException e) {
            return inflate(raw, false);
        }
    }

    private byte[] inflate(byte[] raw, boolean nowrap) throws IOException {
        try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(raw), new Inflater(nowrap));
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toByteArray();
        }
    }

    private String contentTypeForExtension(String extension) {
        return switch (extension.toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "tif", "tiff" -> "image/tiff";
            case "wmf" -> "image/wmf";
            case "emf" -> "image/emf";
            default -> "application/octet-stream";
        };
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "dat" : filename.substring(dot + 1);
    }

    private Optional<Integer> storageIdFromBinName(String filename) {
        String upper = filename.toUpperCase(Locale.ROOT);
        if (!upper.startsWith("BIN") || upper.length() < 7) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(upper.substring(3, 7), 16));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private int u16(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
    }

    private int i32(byte[] data, int offset) {
        return ByteBuffer.wrap(data, offset, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }

    private record PackageItem(String id, String href, String mediaType) {
    }

    private record PackageInfo(List<String> sectionFiles, List<PackageItem> binDataItems,
            Map<String, List<String>> referencedImages) {
    }

    private record HwpFlags(boolean compressed, boolean encrypted, boolean distribution) {
    }

    private record HwpRecord(int tagId, int level, byte[] data) {
    }

    private record BinDataRef(int storageId, String extension) {
    }
}
