package studio.one.platform.autoconfigure;

import java.util.List;

import org.springframework.context.MessageSource;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
@Slf4j
final class ModularMessageSourceFactory {
     
    static MessageSource create(I18nProperties props) throws java.io.IOException {
        List<String> basenames = BasenameResolver.discover(props); // 패턴이면 스캔, 아니면 그대로
        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames(basenames.toArray(new String[0]));
        ms.setDefaultEncoding(props.getEncoding());
        ms.setCacheSeconds(props.getCacheSeconds());
        ms.setUseCodeAsDefaultMessage(props.isUseCodeAsDefaultMessage());
        ms.setFallbackToSystemLocale(props.isFallbackToSystemLocale());

        log.debug( "MessageSource created with basenames: {}", String.join(", ", basenames) );
        
        return ms;
    }
} 