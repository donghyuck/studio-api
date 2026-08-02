package studio.one.platform.ai.web.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ExternalSourceOptionsDto(
        @Pattern(regexp = "[A-Za-z]{2,3}", message = "jurisdiction must be a 2 or 3 letter code")
        String jurisdiction,
        LocalDate asOfDate,
        @Size(max = 16, message = "language must be at most 16 characters")
        String language) {
}
