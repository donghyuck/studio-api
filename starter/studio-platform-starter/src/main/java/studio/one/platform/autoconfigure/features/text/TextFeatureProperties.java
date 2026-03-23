package studio.one.platform.autoconfigure.features.text;

import jakarta.validation.constraints.Min;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".text" )
@Validated
@Getter @Setter
public class TextFeatureProperties extends FeatureToggle {

    @Min(1)
    private int maxExtractBytes = 10 * 1024 * 1024;
   
    private Tesseract tesseract = new Tesseract();


    @Getter
    @Setter
    @NoArgsConstructor
    public static class Tesseract { 
        private String datapath = "/usr/share/tesseract-ocr/4.00/tessdata";
        private String language = "kor+eng";

    }
}
