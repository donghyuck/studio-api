package studio.one.platform.text.extractor.impl;

import java.io.IOException;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.MediaType;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.text.extractor.FileParseException;

@Slf4j
public class PdfFileParser extends AbstractFileParser {

    @Override
    public boolean supports(String contentType, String filename) {
        try {
            if (contentType != null) {
                MediaType mt = MediaType.parseMediaType(contentType);
                if (MediaType.APPLICATION_PDF.includes(mt)) {
                    return true;
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse media type: {}", contentType, e);
        }
        return hasExtension(filename, ".pdf");
    }

    @Override
    public String parse(byte[] bytes, String contentType, String filename) throws FileParseException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setStartPage(1);
            stripper.setEndPage(document.getNumberOfPages());
            return stripper.getText(document);
        } catch (IOException e) {
            throw new FileParseException("Failed to parse PDF file: " + safeFilename(filename), e);
        }
    }
}