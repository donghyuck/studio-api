package studio.one.platform.textract.extractor.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CreationHelper;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.SheetVisibility;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import studio.one.platform.textract.extractor.DocumentFormat;
import studio.one.platform.textract.extractor.DocumentFormatDetector;
import studio.one.platform.textract.model.BlockType;
import studio.one.platform.textract.model.ExtractedTable;
import studio.one.platform.textract.model.ExtractedTableCell;
import studio.one.platform.textract.model.ParsedFile;

class ExcelFileParserTest {

    @Test
    void supportsXlsxAndXlsButLeavesCsvAsText() {
        ExcelFileParser parser = new ExcelFileParser();

        assertTrue(parser.supports(
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sample.xlsx"));
        assertTrue(parser.supports("application/vnd.ms-excel", "sample.xls"));
        assertFalse(parser.supports("text/csv", "sample.csv"));
        assertFalse(parser.supports("application/vnd.ms-excel", "sample.csv"));
        assertEquals(DocumentFormat.EXCEL, DocumentFormatDetector.detect(null, "sample.xlsx"));
        assertEquals(DocumentFormat.EXCEL, DocumentFormatDetector.detect(null, "sample.xls"));
        assertEquals(DocumentFormat.TEXT, DocumentFormatDetector.detect("text/csv", "sample.csv"));
        assertEquals(DocumentFormat.TEXT, DocumentFormatDetector.detect("application/vnd.ms-excel", "sample.csv"));
    }

    @Test
    void parseStructuredExtractsVisibleXlsxSheetWithFormulaDisplayValueAndMetadata() throws Exception {
        ParsedFile result = new ExcelFileParser().parseStructured(
                xlsxWithTypedCellsAndFormula(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "sample.xlsx");

        assertEquals(DocumentFormat.EXCEL, result.format());
        assertEquals(1, result.pages().size());
        assertEquals(BlockType.PAGE, result.pages().get(0).blockType());
        assertEquals(1, result.blocks().size());
        assertEquals(BlockType.TABLE, result.blocks().get(0).blockType());
        assertTrue(result.plainText().contains("Budget"));
        assertTrue(result.plainText().contains("alpha | 10 | TRUE | 2026-01-02 | 20"));

        ExtractedTable table = result.tables().get(0);
        assertEquals("excel", table.format());
        assertEquals("sheet[0]", table.sourceRef());
        assertEquals("Budget", table.metadata().get(ExcelFileParser.KEY_SHEET_NAME));
        assertEquals(0, table.metadata().get(ExcelFileParser.KEY_SHEET_INDEX));
        assertEquals(2, table.rowCount());
        assertEquals(10, table.cellCount());
        assertEquals(1, table.headerRowCount());
        assertEquals("""
                Name | Amount | Active | Date | Total
                Name: alpha | Amount: 10 | Active: TRUE | Date: 2026-01-02 | Total: 20""".strip(), table.vectorText());

        ExtractedTableCell formulaCell = table.cells().stream()
                .filter(cell -> "20".equals(cell.text()))
                .findFirst()
                .orElseThrow();
        assertEquals("sheet[0]/row[1]/cell[4]", formulaCell.sourceRef());
        assertEquals("E2", formulaCell.metadata().get(ExcelFileParser.KEY_CELL_ADDRESS));
        assertEquals("B2*2", formulaCell.metadata().get(ExcelFileParser.KEY_FORMULA));
        assertEquals("FORMULA", formulaCell.metadata().get(ExcelFileParser.KEY_CELL_TYPE));
    }

    @Test
    void parseStructuredExtractsLegacyXlsWorkbook() throws Exception {
        ParsedFile result = new ExcelFileParser().parseStructured(
                xlsWithSimpleTable(),
                "application/vnd.ms-excel",
                "legacy.xls");

        assertEquals(DocumentFormat.EXCEL, result.format());
        assertEquals(1, result.tables().size());
        assertEquals("legacy", result.pages().get(0).metadata().get(ExcelFileParser.KEY_SHEET_NAME));
        assertTrue(result.plainText().contains("A | B"));
        assertTrue(result.plainText().contains("1 | 2"));
    }

    @Test
    void parseStructuredSkipsHiddenSheets() throws Exception {
        ParsedFile result = new ExcelFileParser().parseStructured(
                xlsxWithHiddenSheets(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "hidden.xlsx");

        assertEquals(1, result.pages().size());
        assertEquals(1, result.tables().size());
        assertTrue(result.plainText().contains("visible value"));
        assertFalse(result.plainText().contains("hidden value"));
        assertFalse(result.plainText().contains("very hidden value"));
    }

    @Test
    void parseStructuredCompactsBlankRowsAndSkipsBlankCellsInsideUsedRange() throws Exception {
        ParsedFile result = new ExcelFileParser().parseStructured(
                xlsxWithBlankRowsAndCells(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "blank.xlsx");

        ExtractedTable table = result.tables().get(0);
        assertEquals(2, table.rowCount());
        assertEquals(4, table.cellCount());
        assertEquals("| A |  | C |\n| 1 |  | 3 |", table.markdown());
        assertEquals("A | C\nA: 1 | C: 3", table.vectorText());
        assertEquals(2, table.cells().get(1).col());
        assertEquals("sheet[0]/row[0]/cell[2]", table.cells().get(1).sourceRef());
        assertEquals("sheet[0]/row[2]/cell[2]", table.cells().get(3).sourceRef());
    }

    private byte[] xlsxWithTypedCellsAndFormula() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Budget");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Amount");
            header.createCell(2).setCellValue("Active");
            header.createCell(3).setCellValue("Date");
            header.createCell(4).setCellValue("Total");

            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue("alpha");
            row.createCell(1).setCellValue(10);
            row.createCell(2).setCellValue(true);
            Cell date = row.createCell(3);
            date.setCellValue(LocalDateTime.of(2026, 1, 2, 0, 0));
            date.setCellStyle(dateStyle(workbook));
            row.createCell(4).setCellFormula("B2*2");
            return write(workbook);
        }
    }

    private byte[] xlsWithSimpleTable() throws Exception {
        try (Workbook workbook = new HSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("legacy");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("A");
            header.createCell(1).setCellValue("B");
            Row row = sheet.createRow(1);
            row.createCell(0).setCellValue(1);
            row.createCell(1).setCellValue(2);
            return write(workbook);
        }
    }

    private byte[] xlsxWithHiddenSheets() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            addSingleCellSheet(workbook, "visible", "visible value");
            addSingleCellSheet(workbook, "hidden", "hidden value");
            addSingleCellSheet(workbook, "very-hidden", "very hidden value");
            workbook.setSheetHidden(1, true);
            workbook.setSheetVisibility(2, SheetVisibility.VERY_HIDDEN);
            return write(workbook);
        }
    }

    private byte[] xlsxWithBlankRowsAndCells() throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("blank");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("A");
            header.createCell(2).setCellValue("C");
            sheet.createRow(1);
            Row row = sheet.createRow(2);
            row.createCell(0).setCellValue(1);
            row.createCell(2).setCellValue(3);
            return write(workbook);
        }
    }

    private void addSingleCellSheet(Workbook workbook, String sheetName, String value) {
        Sheet sheet = workbook.createSheet(sheetName);
        sheet.createRow(0).createCell(0).setCellValue(value);
    }

    private CellStyle dateStyle(Workbook workbook) {
        CreationHelper helper = workbook.getCreationHelper();
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(helper.createDataFormat().getFormat("yyyy-mm-dd"));
        return style;
    }

    private byte[] write(Workbook workbook) throws Exception {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            workbook.write(out);
            return out.toByteArray();
        }
    }
}
