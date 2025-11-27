package studio.one.platform.autoconfigure.features.text;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import studio.one.platform.autoconfigure.FeaturesProperties.FeatureToggle;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Features.PREFIX + ".text" )
@Getter @Setter
public class TextFeatureProperties extends FeatureToggle {

   
    private Tesseract tesseract = new Tesseract();


    @Getter
    @Setter
    @NoArgsConstructor
    public static class Tesseract { 
        private String datapath = "/usr/share/tesseract-ocr/4.00/tessdata";
        private String language = "kor+eng";

    }
}
