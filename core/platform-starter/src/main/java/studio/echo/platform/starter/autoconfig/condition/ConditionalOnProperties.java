package studio.echo.platform.starter.autoconfig.condition;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;
/**
 * {@code @ConditionalOnProperties} 어노테이션은 Spring 애플리케이션에서 특정 프로퍼티 설정에 따라 빈을 조건부로 등록할 수 있는 메커니즘을 제공합니다.
 *
 * <p>이 어노테이션을 사용하면 애플리케이션의 구성 요소가 특정 프로퍼티가 활성화되었을 때만 로드되도록 할 수 있습니다.
 * 이를 통해 다양한 환경에서 애플리케이션을 유연하게 구성할 수 있습니다.</p>
 *
 * <p><b>사용 예시:</b></p>
 * <pre>{@code
 * @Configuration
 * @ConditionalOnProperties(
 *     prefix = "example",
 *     value = {
 *         @ConditionalOnProperties.Property(name = "enabled", havingValue = "true"),
 *         @ConditionalOnProperties.Property(name = "feature.enabled", havingValue = "true")
 *     }
 * )
 * public class ExampleConfiguration {
 *
 *     @Bean
 *     public ExampleService exampleService() {
 *         return new ExampleService();
 *     }
 * }
 * }</pre>
 *
 * <p>위 예제는 {@code example.enabled}와 {@code example.feature.enabled} 프로퍼티가 모두 "true"일 때만 {@code ExampleConfiguration} 클래스를 빈으로 등록합니다.</p>
 *
 * 
 * @author  donghyuck, son
 * @since 2025-08-05
 * @version 1.0
 *
 * <pre> 
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *  ---------    --------    ---------------------------
 * 2025-08-05  donghyuck, son: 최초 생성.
 * </pre>
 */


@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Conditional(MultiplePropertiesCondition.class)
public @interface ConditionalOnProperties {

    String prefix() default "";

    Property[] value();

    @interface Property {
        
        String name();

        String havingValue() default "true";

        boolean matchIfMissing() default false;
    }
}
