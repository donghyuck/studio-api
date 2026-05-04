package studio.one.platform.textract.extractor.impl;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellAddress;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.FileParseException;
import studio.one.platform.textract.extractor.StructuredFileParser;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParseWarning;
import studio.one.platform.textract.model.ParsedBlock;
import studio.one.platform.textract.model.ParsedFile;

public class ExcelFileParser extends AbstractFileParser implements StructuredFileParser {

    public static final String KEY_SHEET_NAME = "sheetName";
    public static final String KEY_SHEET_INDEX = "sheetIndex";
    public static final String KEY_ROW_INDEX = "rowIndex";
    public static final String KEY_COLUMN_INDEX = "columnIndex";
    public static final String KEY_CELL_ADDRESS = "cellAddress";
    public static final String KEY_CELL_TYPE = "cellType";
    public static final String KEY_FORMULA = "formula";

    @Override
    public boolean supports(String contentType, String filename) {
        if (hasExtension(filename, ".csv")) {
            return false;
        }
        if (isContentType(
                contentType,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel")) {
            return true;
        }
        return hasExtension(filename, ".xlsx", ".xls");
    }

    @Override
    public ParsedFile parseStructured(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
                Workbook workbook = WorkbookFactory.create(in)) {
            DataFormatter formatter = new DataFormatter();
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<ParsedBlock> blocks = new ArrayList<>();
            List<ParsedBlock> pages = new ArrayList<>();
            List<ExtractedTable> tables = new ArrayList<>();
            List<ParseWarning> warnings = new ArrayList<>();
            StringBuilder plainText = new StringBuilder();
            int order = 0;

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                if (workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex)) {
                    continue;
                }
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                SheetExtraction sheetExtraction = extractSheet(sheet, sheetIndex, formatter, evaluator, warnings);
                if (sheetExtraction.isEmpty()) {
                    continue;
                }

                String sheetPath = sheetPath(sheetIndex);
                Map<String, Object> metadata = sheetMetadata(sheetPath, sheet.getSheetName(), sheetIndex);
                pages.add(ParsedBlock.text(
                        sheetPath,
                        BlockType.PAGE,
                        sheetExtraction.plainText(),
                        null,
                        pages.size(),
                        metadata));
                blocks.add(ParsedBlock.text(
                        sheetPath,
                        BlockType.TABLE,
                        sheetExtraction.markdown(),
                        null,
                        order,
                        new LinkedHashMap<>(metadata)));
                tables.add(new ExtractedTable(
                        sheetPath,
                        sheetExtraction.markdown(),
                        sheetExtraction.cells(),
                        excelTableMetadata(
                                sheetPath,
                                sheet.getSheetName(),
                                sheetIndex,
                                sheetExtraction.cells(),
                                sheetExtraction.headerRowCount())));
                plainText.append(sheet.getSheetName()).append("\n")
                        .append(sheetExtraction.plainText())
                        .append("\n\n");
                order++;
            }

            return new ParsedFile(
                    DocumentFormat.EXCEL,
                    cleanText(plainText.toString()),
                    blocks,
                    fileMetadata(contentType, filename),
                    warnings,
                    pages,
                    tables,
                    List.of(),
                    false);
        } catch (IOException | RuntimeException e) {
            throw new FileParseException("Failed to parse Excel file: " + safeFilename(filename), e);
        }
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        return parseStructured(bytes, contentType, filename).plainText();
    }

    private SheetExtraction extractSheet(
            Sheet sheet,
            int sheetIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<ParseWarning> warnings) {
        Map<Integer, Map<Integer, CellExtraction>> rows = collectCells(sheet, sheetIndex, formatter, evaluator, warnings);
        if (rows.isEmpty()) {
            return SheetExtraction.empty();
        }

        int minColumn = rows.values().stream()
                .flatMap(row -> row.keySet().stream())
                .mapToInt(Integer::intValue)
                .min()
                .orElse(0);
        int maxColumn = rows.values().stream()
                .flatMap(row -> row.keySet().stream())
                .mapToInt(Integer::intValue)
                .max()
                .orElse(0);

        List<ExtractedTableCell> cells = new ArrayList<>();
        List<String> markdownRows = new ArrayList<>();
        StringBuilder sheetText = new StringBuilder();
        int relativeRow = 0;
        for (Map.Entry<Integer, Map<Integer, CellExtraction>> rowEntry : rows.entrySet()) {
            List<String> markdownCells = new ArrayList<>();
            List<String> plainCells = new ArrayList<>();
            Map<Integer, CellExtraction> rowCells = rowEntry.getValue();
            for (int columnIndex = minColumn; columnIndex <= maxColumn; columnIndex++) {
                CellExtraction cell = rowCells.get(columnIndex);
                String text = cell == null ? "" : cell.text();
                markdownCells.add(markdownText(text));
                if (cell != null) {
                    int relativeColumn = columnIndex - minColumn;
                    cells.add(extractedCell(
                            relativeRow,
                            relativeColumn,
                            rowEntry.getKey(),
                            columnIndex,
                            cell,
                            cells.size(),
                            relativeRow == 0));
                    plainCells.add(markdownText(text));
                }
            }
            markdownRows.add("| " + String.join(" | ", markdownCells) + " |");
            sheetText.append(String.join(" | ", plainCells)).append("\n");
            relativeRow++;
        }

        return new SheetExtraction(cleanText(sheetText.toString()), String.join("\n", markdownRows), cells, 1);
    }

    private Map<Integer, Map<Integer, CellExtraction>> collectCells(
            Sheet sheet,
            int sheetIndex,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<ParseWarning> warnings) {
        Map<Integer, Map<Integer, CellExtraction>> rows = new TreeMap<>();
        for (Row row : sheet) {
            Map<Integer, CellExtraction> rowCells = new TreeMap<>();
            for (Cell cell : row) {
                CellExtraction extraction = extractCell(sheet, sheetIndex, cell, formatter, evaluator, warnings);
                if (!extraction.text().isBlank()) {
                    rowCells.put(cell.getColumnIndex(), extraction);
                }
            }
            if (!rowCells.isEmpty()) {
                rows.put(row.getRowNum(), rowCells);
            }
        }
        return rows;
    }

    private CellExtraction extractCell(
            Sheet sheet,
            int sheetIndex,
            Cell cell,
            DataFormatter formatter,
            FormulaEvaluator evaluator,
            List<ParseWarning> warnings) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        CellType cellType = cell.getCellType();
        metadata.put(KEY_SHEET_NAME, sheet.getSheetName());
        metadata.put(KEY_SHEET_INDEX, sheetIndex);
        metadata.put(KEY_ROW_INDEX, cell.getRowIndex());
        metadata.put(KEY_COLUMN_INDEX, cell.getColumnIndex());
        metadata.put(KEY_CELL_ADDRESS, new CellAddress(cell).formatAsString());
        metadata.put(KEY_CELL_TYPE, cellType.name());
        if (cellType == CellType.FORMULA) {
            metadata.put(KEY_FORMULA, cell.getCellFormula());
        }

        String text;
        try {
            text = formatter.formatCellValue(cell, evaluator);
        } catch (RuntimeException ex) {
            if (cellType != CellType.FORMULA) {
                throw ex;
            }
            text = formatter.formatCellValue(cell);
            String sourceRef = cellPath(sheetIndex, cell.getRowIndex(), cell.getColumnIndex());
            warnings.add(ParseWarning.partial(
                    "EXCEL_FORMULA_EVALUATION_PARTIAL",
                    "Excel formula evaluation failed; cached or raw cell display text was used.",
                    sourceRef,
                    Map.of(KEY_FORMULA, metadata.getOrDefault(KEY_FORMULA, ""))));
        }
        return new CellExtraction(cleanText(text), metadata);
    }

    private ExtractedTableCell extractedCell(
            int row,
            int col,
            int rowIndex,
            int columnIndex,
            CellExtraction cell,
            int order,
            boolean header) {
        Map<String, Object> metadata = new LinkedHashMap<>(cell.metadata());
        String sourceRef = cellPath(
                (Integer) metadata.get(KEY_SHEET_INDEX),
                rowIndex,
                columnIndex);
        metadata.put(ExtractedTableCell.KEY_SOURCE_REF, sourceRef);
        metadata.put(ExtractedTableCell.KEY_ORDER, order);
        if (header) {
            metadata.put(ExtractedTableCell.KEY_HEADER, true);
        }
        return new ExtractedTableCell(row, col, 1, 1, cell.text(), metadata);
    }

    private Map<String, Object> excelTableMetadata(
            String sourceRef,
            String sheetName,
            int sheetIndex,
            List<ExtractedTableCell> cells,
            int headerRowCount) {
        Map<String, Object> metadata = new LinkedHashMap<>(tableMetadata(sourceRef, "excel", cells, headerRowCount));
        metadata.put(KEY_SHEET_NAME, sheetName);
        metadata.put(KEY_SHEET_INDEX, sheetIndex);
        return metadata;
    }

    private Map<String, Object> sheetMetadata(String sourceRef, String sheetName, int sheetIndex) {
        Map<String, Object> metadata = new LinkedHashMap<>(blockMetadata(sourceRef));
        metadata.put(KEY_SHEET_NAME, sheetName);
        metadata.put(KEY_SHEET_INDEX, sheetIndex);
        return metadata;
    }

    private String sheetPath(int sheetIndex) {
        return "sheet[" + sheetIndex + "]";
    }

    private String cellPath(int sheetIndex, int rowIndex, int columnIndex) {
        return sheetPath(sheetIndex) + "/row[" + rowIndex + "]/cell[" + columnIndex + "]";
    }

    private String markdownText(String text) {
        return text == null ? "" : text.replace('\n', ' ').trim();
    }

    private record CellExtraction(String text, Map<String, Object> metadata) {
        CellExtraction {
            text = text == null ? "" : text;
            metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        }
    }

    private record SheetExtraction(
            String plainText,
            String markdown,
            List<ExtractedTableCell> cells,
            int headerRowCount) {
        static SheetExtraction empty() {
            return new SheetExtraction("", "", List.of(), 0);
        }

        SheetExtraction {
            plainText = plainText == null ? "" : plainText;
            markdown = markdown == null ? "" : markdown;
            cells = cells == null ? List.of() : List.copyOf(cells);
        }

        boolean isEmpty() {
            return cells.isEmpty();
        }
    }
}
