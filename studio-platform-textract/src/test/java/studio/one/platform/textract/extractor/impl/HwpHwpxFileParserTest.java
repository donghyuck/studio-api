package studio.one.platform.textract.extractor.impl;

import static java.nio.charset.StandardCharsets.UTF_16LE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ParseWarningSeverity;
import studio.one.platform.textract.model.ParsedFile;

class HwpHwpxFileParserTest {

    private final HwpHwpxFileParser parser = new HwpHwpxFileParser();

    @Test
    void parseHwpxExtractsTextTableAndImages() throws Exception {
        ParsedFile result = parser.parseStructured(hwpxBytes(), "application/hwpx", "sample.hwpx");

        assertEquals(DocumentFormat.HWPX, result.format());
        assertTrue(result.plainText().contains("본문 문단"));
        assertTrue(result.plainText().contains("A1"));
        assertEquals(1, result.tables().size());
        assertEquals(4, result.tables().get(0).cells().size());
        assertEquals(1, result.images().size());
        assertTrue(result.blocks().stream().anyMatch(block -> block.type() == BlockType.TABLE));
        assertEquals("section[0]/p[3]/tbl[1]", result.tables().get(0).sourceRef());
        assertEquals("hwpx", result.tables().get(0).format());
        assertEquals("A1 | B1\nA1: A2 | B1: B2", result.tables().get(0).vectorText());
        assertEquals(1, result.tables().get(0).headerRowCount());
        assertEquals("section[0]/p[3]/tbl[1]/row[0]/cell[0]", result.tables().get(0).cells().get(0).sourceRef());
        assertEquals(0, result.tables().get(0).cells().get(0).order());
        assertTrue(result.tables().get(0).cells().get(0).header());
        assertEquals("section[0]/p[5]/pic[0]", result.images().get(0).sourceRef());
        assertEquals(java.util.List.of("section[0]/p[5]/pic[0]"), result.images().get(0).sourceRefs());
        assertEquals("Contents/BinData/image1.png", result.images().get(0).binDataRef());
        assertEquals("image1", result.images().get(0).packageId());
    }

    @Test
    void parseHwpExtractsBodyTextAndBinDataImages() throws Exception {
        ParsedFile result = parser.parseStructured(hwpBytes(), "application/x-hwp", "sample.hwp");

        assertEquals(DocumentFormat.HWP, result.format());
        assertTrue(result.plainText().contains("한글 본문"));
        assertFalse(result.blocks().isEmpty());
        assertEquals(1, result.images().size());
        assertEquals("image/png", result.images().get(0).contentType());
        assertEquals("BIN0001.png", result.images().get(0).binDataRef());
    }

    @Test
    void parseStructuredEmitsCodeBasedWarningsForMissingHwpxSection() throws Exception {
        ParsedFile result = parser.parseStructured(hwpxBytesWithMissingSection(), "application/hwpx", "missing.hwpx");

        assertEquals(1, result.warnings().size());
        assertEquals("hwpx.section.missing", result.warnings().get(0).code());
        assertEquals("HWPX_SECTION_MISSING", result.warnings().get(0).canonicalCode());
        assertTrue(result.warnings().get(0).partialParse());
    }

    @Test
    void parseStructuredMarksEncryptedHwpAsErrorWarning() throws Exception {
        ParsedFile result = parser.parseStructured(hwpBytesWithFlags(0x02), "application/x-hwp", "encrypted.hwp");

        assertEquals(1, result.warnings().size());
        assertEquals("hwp.encrypted", result.warnings().get(0).code());
        assertEquals("HWP_ENCRYPTED", result.warnings().get(0).canonicalCode());
        assertEquals(ParseWarningSeverity.ERROR, result.warnings().get(0).severity());
        assertFalse(result.warnings().get(0).partialParse());
    }

    private byte[] hwpxBytes() throws Exception {
        Map<String, byte[]> entries = Map.of(
                "Contents/content.hpf", """
                        <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
                          <opf:manifest>
                            <opf:item id="section0" href="section0.xml" media-type="application/xml"/>
                            <opf:item id="image1" href="BinData/image1.png" media-type="image/png"/>
                          </opf:manifest>
                          <opf:spine><opf:itemref idref="section0"/></opf:spine>
                        </opf:package>
                        """.getBytes(UTF_8),
                "Contents/section0.xml", """
                        <hs:sec xmlns:hs="http://www.hancom.co.kr/hwpml/2011/section"
                                xmlns:hp="http://www.hancom.co.kr/hwpml/2011/paragraph">
                          <hp:p><hp:run><hp:t>본문 문단</hp:t></hp:run></hp:p>
                          <hp:p>
                            <hp:tbl>
                              <hp:tr>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>A1</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>B1</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                              </hp:tr>
                              <hp:tr>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>A2</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                                <hp:tc><hp:subList><hp:p><hp:run><hp:t>B2</hp:t></hp:run></hp:p></hp:subList></hp:tc>
                              </hp:tr>
                            </hp:tbl>
                          </hp:p>
                          <hp:p><hp:pic><hp:img binaryItemIDRef="image1"/></hp:pic></hp:p>
                        </hs:sec>
                        """.getBytes(UTF_8),
                "Contents/BinData/image1.png", new byte[] { (byte) 0x89, 'P', 'N', 'G' });
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private byte[] hwpxBytesWithMissingSection() throws Exception {
        Map<String, byte[]> entries = Map.of(
                "Contents/content.hpf", """
                        <opf:package xmlns:opf="http://www.idpf.org/2007/opf">
                          <opf:manifest>
                            <opf:item id="section0" href="section0.xml" media-type="application/xml"/>
                          </opf:manifest>
                          <opf:spine><opf:itemref idref="section0"/></opf:spine>
                        </opf:package>
                        """.getBytes(UTF_8));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return out.toByteArray();
    }

    private byte[] hwpBytes() throws Exception {
        return hwpBytesWithFlags(0);
    }

    private byte[] hwpBytesWithFlags(int flags) throws Exception {
        try (POIFSFileSystem fs = new POIFSFileSystem();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            fs.getRoot().createDocument("FileHeader", new ByteArrayInputStream(fileHeader(flags)));
            DirectoryEntry bodyText = fs.getRoot().createDirectory("BodyText");
            bodyText.createDocument("Section0", new ByteArrayInputStream(section("한글 본문")));
            DirectoryEntry binData = fs.getRoot().createDirectory("BinData");
            binData.createDocument("BIN0001.png", new ByteArrayInputStream(new byte[] { (byte) 0x89, 'P', 'N', 'G' }));
            fs.writeFilesystem(out);
            return out.toByteArray();
        }
    }

    private byte[] fileHeader(int flags) {
        byte[] header = new byte[256];
        byte[] signature = "HWP Document File".getBytes(UTF_8);
        System.arraycopy(signature, 0, header, 0, signature.length);
        header[35] = 5;
        header[36] = (byte) flags;
        header[37] = (byte) (flags >>> 8);
        header[38] = (byte) (flags >>> 16);
        header[39] = (byte) (flags >>> 24);
        return header;
    }

    private byte[] section(String text) throws Exception {
        byte[] paraHeaderData = new byte[12];
        byte[] paraTextData = paraText(text);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(record(66, 0, paraHeaderData));
        out.write(record(67, 1, paraTextData));
        return out.toByteArray();
    }

    private byte[] paraText(String text) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(text.getBytes(UTF_16LE));
        out.write(new byte[] { 0x0D, 0x00 });
        return out.toByteArray();
    }

    private byte[] record(int tagId, int level, byte[] data) throws Exception {
        int header = tagId | (level << 10) | (data.length << 20);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {
                (byte) header,
                (byte) (header >>> 8),
                (byte) (header >>> 16),
                (byte) (header >>> 24)
        });
        out.write(data);
        return out.toByteArray();
    }
}
