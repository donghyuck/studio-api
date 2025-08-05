package studio.api.config;

import javax.sql.DataSource;

import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperScannerConfigurer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@Configuration("config:egovMyBatis")
public class EgovMyBatisConfig {

    @Bean(name = { "sqlSessionFactory.primary"} )
    public SqlSessionFactory sqlSessionFactory(@Qualifier("datasource.primary") DataSource dataSource) throws Exception {
        SqlSessionFactoryBean sessionFactory = new SqlSessionFactoryBean();
        sessionFactory.setDataSource(dataSource);
        // Set the location of MyBatis mapper XML files
        // Adjust the path according to your project structure
        sessionFactory.setMapperLocations(
            new PathMatchingResourcePatternResolver().getResources("classpath*:mapper/**/*.xml")
        );
        return sessionFactory.getObject();
    }
    
    @Bean(name = "sqlSessionTemplate.primary")
	public SqlSessionTemplate egovSqlSessionTemplate(@Qualifier("sqlSessionFactory.primary") SqlSessionFactory sqlSession) {
		return new SqlSessionTemplate(sqlSession);
	}

    @Bean(name = "mapperScannerConfigurer.primary")
    public MapperScannerConfigurer mapperScannerConfigurer() {
        MapperScannerConfigurer configurer = new MapperScannerConfigurer();
        configurer.setBasePackage("studio.api.platform.components.mapper"); // 경로 정확히!
        configurer.setAnnotationClass(org.egovframe.rte.psl.dataaccess.mapper.Mapper.class);
        configurer.setSqlSessionFactoryBeanName("sqlSessionFactory.primary"); // 정확한 이름
        return configurer;
    }

}
