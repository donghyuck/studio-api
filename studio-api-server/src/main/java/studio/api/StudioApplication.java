package studio.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StudioApplication is the main entry point for the Studio API server.
 * It initializes the Spring Boot application with necessary configurations such as
 * asynchronous processing, scheduling, and caching.
 * This class extends SpringBootServletInitializer to support deployment in a servlet container.
 * It uses annotations to enable asynchronous execution, scheduling tasks, and caching capabilities.
 * The main method runs the application, starting the embedded server and loading the application context.
 * 
 * @author  donghyuck, son
 * @since 2025-07-08
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-07-08  donghyuck, son: 최초 생성.
 * </pre>
 */

@EnableAsync
@EnableScheduling
@EnableCaching
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class StudioApplication extends SpringBootServletInitializer  {

	  @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(StudioApplication.class);
    }

    public static void main(String[] args) {
		SpringApplication.run(StudioApplication.class, args);
	}

}
