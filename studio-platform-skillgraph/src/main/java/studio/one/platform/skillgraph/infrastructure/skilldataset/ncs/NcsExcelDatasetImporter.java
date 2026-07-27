package studio.one.platform.skillgraph.infrastructure.skilldataset.ncs;

import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ooxml.util.SAXHelper;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.ss.util.CellReference;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillConcept;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDataset;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImportProgressListener;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetImporter;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillDatasetStore;
import studio.one.platform.skillgraph.infrastructure.skilldataset.SkillRelation;

@RequiredArgsConstructor
public class NcsExcelDatasetImporter implements SkillDatasetImporter {

    private static final int BATCH_SIZE = 3_000;
    private static final int DEFAULT_MAX_BYTE_ARRAY_SIZE = 256_000_000;

    private final SkillDatasetStore store;
    private final ObjectMapper objectMapper;
    private final int maxByteArraySize;

    public NcsExcelDatasetImporter(SkillDatasetStore store, ObjectMapper objectMapper) {
        this(store, objectMapper, DEFAULT_MAX_BYTE_ARRAY_SIZE);
    }

    @Override
    public String provider() {
        return NcsTypes.PROVIDER;
    }

    @Override
    public void importDataset(ImportCommand command, SkillDatasetImportProgressListener listener) {
        SkillDatasetImportProgressListener progressListener =
                listener == null ? SkillDatasetImportProgressListener.NOOP : listener;

        store.saveDataset(new SkillDataset(
                command.datasetId(),
                NcsTypes.PROVIDER,
                isBlank(command.datasetName()) ? command.datasetId() : command.datasetName(),
                command.version(),
                command.language(),
                command.sourceLocation(),
                Instant.now()
        ));

        ImportContext context = new ImportContext(progressListener);

        Path source = Path.of(command.sourceLocation());
        IOUtils.setByteArrayMaxOverride(Math.max(DEFAULT_MAX_BYTE_ARRAY_SIZE, maxByteArraySize));

        try (OPCPackage pkg = OPCPackage.open(source.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable sharedStrings = new ReadOnlySharedStringsTable(pkg);
            DataFormatter formatter = new DataFormatter();

            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    importSheet(command, sheet, styles, sharedStrings, formatter, context);
                }
            }
            context.flush();

        } catch (Exception e) {
            throw new IllegalStateException("Failed to import NCS Excel dataset: " + command.sourceLocation(), e);
        }
    }

    private void importSheet(
            ImportCommand command,
            InputStream sheet,
            StylesTable styles,
            ReadOnlySharedStringsTable sharedStrings,
            DataFormatter formatter,
            ImportContext context
    ) throws Exception {
        XMLReader parser = SAXHelper.newXMLReader();
        parser.setContentHandler(new XSSFSheetXMLHandler(
                styles,
                null,
                sharedStrings,
                new NcsSheetHandler(command, context),
                formatter,
                false));
        parser.parse(new InputSource(sheet));
    }

    private void importRow(ImportCommand command, NcsRow row, ImportContext context) throws Exception {
        String majorId = id("ncs_major", row.majorCode());
        String middleId = id("ncs_middle", row.majorCode(), row.middleCode());
        String minorId = id("ncs_minor", row.majorCode(), row.middleCode(), row.minorCode());
        String detailId = id("ncs_detail", row.majorCode(), row.middleCode(), row.minorCode(), row.detailCode());
        String unitId = id("ncs_unit", row.unitCode());
        String elementId = id("ncs_element", row.elementNo());
        String criteriaId = id("ncs_criteria", row.elementNo(), row.criteriaNo(), row.criteriaText());
        String ksaId = id("ncs_ksa", row.unitCode(), row.ksaTypeName(), row.ksaNo(), row.ksaText());

        String categoryPath = joinPath(row.majorName(), row.middleName(), row.minorName(), row.detailName());

        bufferConcept(command, context, majorId, NcsTypes.MAJOR_CATEGORY, row.majorCode(), null, row.majorName(), null, null, row.majorName(), row);
        bufferConcept(command, context, middleId, NcsTypes.MIDDLE_CATEGORY, row.middleCode(), row.majorCode(), row.middleName(), null, null, joinPath(row.majorName(), row.middleName()), row);
        bufferConcept(command, context, minorId, NcsTypes.MINOR_CATEGORY, row.minorCode(), row.middleCode(), row.minorName(), null, null, joinPath(row.majorName(), row.middleName(), row.minorName()), row);
        bufferConcept(command, context, detailId, NcsTypes.DETAIL_CATEGORY, row.detailCode(), row.minorCode(), row.detailName(), null, null, categoryPath, row);

        bufferConcept(command, context, unitId, NcsTypes.COMPETENCY_UNIT, row.unitCode(), row.detailCode(), row.unitName(), null, row.unitLevel(), categoryPath, row);
        bufferConcept(command, context, elementId, NcsTypes.COMPETENCY_ELEMENT, row.elementNo(), row.unitCode(), row.elementName(), null, row.elementLevel(), categoryPath, row);
        bufferConcept(command, context, criteriaId, NcsTypes.PERFORMANCE_CRITERIA, row.criteriaNo(), row.elementNo(), row.criteriaText(), null, null, categoryPath, row);

        String ksaType = ksaConceptType(row.ksaTypeName());
        String relationType = ksaRelationType(row.ksaTypeName());

        bufferConcept(command, context, ksaId, ksaType, row.ksaNo(), row.unitCode(), row.ksaText(), row.ksaTypeName(), null, categoryPath, row);

        bufferRelation(command, context, majorId, middleId, NcsTypes.HAS_MIDDLE_CATEGORY, row);
        bufferRelation(command, context, middleId, minorId, NcsTypes.HAS_MINOR_CATEGORY, row);
        bufferRelation(command, context, minorId, detailId, NcsTypes.HAS_DETAIL_CATEGORY, row);
        bufferRelation(command, context, detailId, unitId, NcsTypes.HAS_COMPETENCY_UNIT, row);
        bufferRelation(command, context, unitId, elementId, NcsTypes.HAS_COMPETENCY_ELEMENT, row);
        bufferRelation(command, context, elementId, criteriaId, NcsTypes.HAS_PERFORMANCE_CRITERIA, row);
        bufferRelation(command, context, unitId, ksaId, relationType, row);
    }

    private void bufferConcept(
            ImportCommand command,
            ImportContext context,
            String conceptId,
            String conceptType,
            String externalCode,
            String parentCode,
            String label,
            String description,
            String levelValue,
            String categoryPath,
            NcsRow row
    ) throws Exception {
        if (isBlank(conceptId) || isBlank(label)) {
            return;
        }

        SkillConcept concept = new SkillConcept(
                conceptId,
                command.datasetId(),
                NcsTypes.PROVIDER,
                conceptType,
                externalCode,
                parentCode,
                label,
                description,
                levelValue,
                categoryPath,
                normalize(label),
                objectMapper.writeValueAsString(row)
        );

        context.conceptBuffer.put(concept.conceptId(), concept);
    }

    private void bufferRelation(
            ImportCommand command,
            ImportContext context,
            String sourceId,
            String targetId,
            String relationType,
            NcsRow row
    ) throws Exception {
        if (isBlank(sourceId) || isBlank(targetId)) {
            return;
        }

        SkillRelation relation = new SkillRelation(
                id("ncs_rel", sourceId, relationType, targetId),
                command.datasetId(),
                NcsTypes.PROVIDER,
                sourceId,
                targetId,
                relationType,
                1.0,
                objectMapper.writeValueAsString(row)
        );

        context.relationBuffer.put(relation.relationId(), relation);
    }

    private final class ImportContext {

        private final SkillDatasetImportProgressListener listener;

        private final Map<String, SkillConcept> conceptBuffer = new LinkedHashMap<>();
        private final Map<String, SkillRelation> relationBuffer = new LinkedHashMap<>();

        private long totalRows;
        private long processedRows;
        private long createdConcepts;
        private long createdRelations;
        private long failedRows;

        private ImportContext(SkillDatasetImportProgressListener listener) {
            this.listener = listener;
        }

        private void flush() {
            if (!conceptBuffer.isEmpty()) {
                int count = conceptBuffer.size();
                store.upsertConcepts(List.copyOf(conceptBuffer.values()));
                createdConcepts += count;
                conceptBuffer.clear();
            }

            if (!relationBuffer.isEmpty()) {
                int count = relationBuffer.size();
                store.upsertRelations(List.copyOf(relationBuffer.values()));
                createdRelations += count;
                relationBuffer.clear();
            }

            listener.onProgress(totalRows, processedRows, createdConcepts, createdRelations, failedRows);
        }
    }

    private String ksaConceptType(String value) {
        if ("지식".equals(value)) {
            return NcsTypes.KNOWLEDGE;
        }
        if ("기술".equals(value)) {
            return NcsTypes.SKILL;
        }
        if ("태도".equals(value)) {
            return NcsTypes.ATTITUDE;
        }
        return NcsTypes.KSA;
    }

    private String ksaRelationType(String value) {
        if ("지식".equals(value)) {
            return NcsTypes.REQUIRES_KNOWLEDGE;
        }
        if ("기술".equals(value)) {
            return NcsTypes.REQUIRES_SKILL;
        }
        if ("태도".equals(value)) {
            return NcsTypes.REQUIRES_ATTITUDE;
        }
        return NcsTypes.REQUIRES_KSA;
    }

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String joinPath(String... values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!isBlank(value)) {
                if (!builder.isEmpty()) {
                    builder.append(" > ");
                }
                builder.append(value.trim());
            }
        }
        return builder.toString();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String id(String prefix, String... parts) {
        return prefix + "_" + sha256(String.join("|", parts));
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes).substring(0, 24);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private final class NcsSheetHandler implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final ImportCommand command;
        private final ImportContext context;
        private final Map<String, Integer> headers = new HashMap<>();
        private final Map<Integer, String> values = new HashMap<>();
        private boolean headerProcessed;

        private NcsSheetHandler(ImportCommand command, ImportContext context) {
            this.command = command;
            this.context = context;
        }

        @Override
        public void startRow(int rowNum) {
            values.clear();
        }

        @Override
        public void endRow(int rowNum) {
            if (!headerProcessed) {
                values.forEach((index, value) -> {
                    if (!isBlank(value)) {
                        headers.put(value.trim(), index);
                    }
                });
                headerProcessed = true;
                return;
            }

            try {
                NcsRow ncs = NcsRow.from(values, headers);
                if (!ncs.isEmpty()) {
                    importRow(command, ncs, context);
                }
            } catch (Exception ex) {
                context.failedRows++;
            } finally {
                context.processedRows++;
                context.totalRows++;
            }

            if (context.conceptBuffer.size() >= BATCH_SIZE || context.relationBuffer.size() >= BATCH_SIZE) {
                context.flush();
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, org.apache.poi.xssf.usermodel.XSSFComment comment) {
            if (cellReference == null || formattedValue == null) {
                return;
            }
            values.put((int) new CellReference(cellReference).getCol(), formattedValue.trim());
        }
    }

    record NcsRow(
            String majorCode,
            String majorName,
            String middleCode,
            String middleName,
            String minorCode,
            String minorName,
            String detailCode,
            String detailName,
            String unitCode,
            String unitName,
            String unitLevel,
            String elementNo,
            String elementName,
            String elementLevel,
            String criteriaNo,
            String criteriaText,
            String ksaTypeCode,
            String ksaTypeName,
            String ksaNo,
            String ksaText
    ) {
        static NcsRow from(Map<Integer, String> row, Map<String, Integer> h) {
            return new NcsRow(
                    get(row, h, "대분류코드"),
                    get(row, h, "대분류코드명"),
                    get(row, h, "중분류코드"),
                    get(row, h, "중분류코드명"),
                    get(row, h, "소분류코드"),
                    get(row, h, "소분류코드명"),
                    get(row, h, "세분류코드"),
                    get(row, h, "세분류코드명"),
                    get(row, h, "능력단위분류번호"),
                    get(row, h, "능력단위명칭"),
                    get(row, h, "수준"),
                    get(row, h, "능력단위요소번호"),
                    get(row, h, "능력단위요소명"),
                    get(row, h, "능력단위요소수준"),
                    get(row, h, "수행준거번호"),
                    get(row, h, "수행준거"),
                    get(row, h, "지식기술태도코드"),
                    get(row, h, "지식기술태도코드명"),
                    get(row, h, "지식기술태도번호"),
                    get(row, h, "지식기술태도의의")
            );
        }

        boolean isEmpty() {
            return isBlank(unitCode) && isBlank(elementNo) && isBlank(criteriaText) && isBlank(ksaText);
        }

        private static String get(Map<Integer, String> row, Map<String, Integer> h, String name) {
            Integer index = h.get(name);
            if (index == null) {
                return null;
            }
            String value = row.get(index);
            return value == null || value.isBlank() ? null : value.trim();
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
