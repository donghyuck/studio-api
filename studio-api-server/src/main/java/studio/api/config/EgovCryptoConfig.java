package studio.api.config;

import javax.annotation.PostConstruct;

import org.egovframe.boot.crypto.service.EgovEnvCryptoService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import studio.api.platform.spring.autoconfigure.condition.ConditionalOnClassPresence;

@Slf4j
@ConditionalOnClassPresence("org.egovframe.boot.crypto.service.EgovEnvCryptoService")
@Configuration
@ComponentScan(basePackages = { "org.egovframe.boot.crypto" })
public class EgovCryptoConfig implements ApplicationContextAware {

    private ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }

    @PostConstruct
    public void debugCryptoBeans() {
        log.info("=== EgovFrame Crypto Beans ===");

        String[] beanNames = {
            "egovEnvPasswordEncoderService",
            "egovEnvARIACryptoService",
            "egovEnvCryptoService",
            "egovGeneralCryptoService",
            "egovDigestService"
        };

        for (String name : beanNames) {
            if (context.containsBean(name)) {
                Object bean = context.getBean(name);
                log.info("Bean found: {} -> {}", name, bean.getClass().getName());
            } else {
                log.info("Bean NOT found: {}", name);
            }
        }

        log.info("===============================");
        cryptoTest();
    }

    public void cryptoTest(){

        EgovEnvCryptoService egovEnvCryptoService = context.getBean("egovEnvCryptoService", EgovEnvCryptoService.class);
        String[] testString = { "This is a testing...\nHello!",
				"한글 테스트입니다...",
				"!@#$%^&*()_+|~{}:\"<>?-=\\`[];',./" }; 
			for (String str : testString) {			
				String encrypted = egovEnvCryptoService.encrypt(str);		
				String decrypted = egovEnvCryptoService.decrypt(encrypted);
				log.debug(encrypted);
				log.debug(decrypted);
			}
         log.debug("-----------------------------");
    }

}
