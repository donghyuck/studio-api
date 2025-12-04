package studio.one.platform.autoconfigure.perisitence.jdbc.sqlquery;

import java.util.List;
import java.util.Set;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.data.sqlquery.annotation.SqlMapper;

/**
 * Scans for interfaces annotated with {@link SqlMapper} and registers
 * {@link SqlMapperFactoryBean} instances for them.
 */
@Slf4j
public class SqlMapperRegistrar implements ImportBeanDefinitionRegistrar, ResourceLoaderAware, BeanFactoryAware {

    private ResourceLoader resourceLoader;
    private BeanFactory beanFactory;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        Set<String> basePackages = determineBasePackages(importingClassMetadata);
        if (basePackages.isEmpty()) {
            log.debug("No base packages found for SqlMapper scanning.");
            return;
        }
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(SqlMapper.class));
        scanner.setResourceLoader(resourceLoader);

        BeanNameGenerator nameGenerator = new AnnotationBeanNameGenerator();
        for (String basePackage : basePackages) {
            scanner.findCandidateComponents(basePackage).forEach(candidate -> {
                String className = candidate.getBeanClassName();
                try {
                    Class<?> mapperInterface = ClassUtils.forName(className, resourceLoader.getClassLoader());
                    registerMapperFactoryBean(registry, mapperInterface, nameGenerator);
                } catch (ClassNotFoundException e) {
                    log.warn("Failed to load @SqlMapper interface: {}", className, e);
                }
            });
        }
    }

    private void registerMapperFactoryBean(BeanDefinitionRegistry registry, Class<?> mapperInterface,
            BeanNameGenerator nameGenerator) {
        BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(SqlMapperFactoryBean.class);
        builder.addConstructorArgValue(mapperInterface);
        builder.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
        String beanName = nameGenerator.generateBeanName(builder.getBeanDefinition(), registry);
        if (registry.containsBeanDefinition(beanName)) {
            return;
        }
        registry.registerBeanDefinition(beanName, builder.getBeanDefinition());
        log.info("Registered SqlMapper proxy for {}", mapperInterface.getName());
    }

    private Set<String> determineBasePackages(AnnotationMetadata importingClassMetadata) {
        AnnotationAttributes attributes = AnnotationAttributes.fromMap(
                importingClassMetadata.getAnnotationAttributes(EnableSqlMappers.class.getName()));
        Set<String> basePackages = Set.of();
        if (attributes != null) {
            String[] declared = attributes.getStringArray("basePackages");
            if (declared != null && declared.length > 0) {
                basePackages = Set.of(declared);
            }
        }
        if (basePackages.isEmpty() && AutoConfigurationPackages.has(beanFactory)) {
            List<String> autoConfigPackages = AutoConfigurationPackages.get(beanFactory);
            basePackages = Set.copyOf(autoConfigPackages);
        }
        return basePackages.stream().filter(StringUtils::hasText).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}
