package studio.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

@Configuration
public class EgovMessageSourceConfig {

    @Bean(name = "egovMessageSource")
	public ReloadableResourceBundleMessageSource messageSource() {
		ReloadableResourceBundleMessageSource reloadableResourceBundleMessageSource = new ReloadableResourceBundleMessageSource();
		reloadableResourceBundleMessageSource.setBasenames(
				"classpath:/egovframework/message/message-common",
				"classpath:/org/egovframe/rte/fdl/idgnr/messages/idgnr",
				"classpath:/org/egovframe/rte/fdl/property/messages/properties");
		reloadableResourceBundleMessageSource.setDefaultEncoding("UTF-8");
		reloadableResourceBundleMessageSource.setCacheSeconds(60);
		return reloadableResourceBundleMessageSource;
	}

}
