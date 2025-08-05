/**
 *
 *      Copyright 2025
 *
 *      Licensed under the Apache License, Version 2.0 (the 'License');
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an 'AS IS' BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *
 *      @file RepositoryImpl.java
 *      @date 2025
 *
 */
package studio.echo.platform.component;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.support.ServletContextResource;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.component.event.PropertiesRefreshedEvent;
import studio.echo.platform.component.event.StateChangeEvent;
import studio.echo.platform.constant.Colors;
import studio.echo.platform.constant.MessageCodes;
import studio.echo.platform.constant.PropertyKeys;
import studio.echo.platform.constant.ServiceNames;
import studio.echo.platform.exception.NotFoundException;
import studio.echo.platform.service.ApplicationProperties;
import studio.echo.platform.service.ConfigRoot;
import studio.echo.platform.service.I18n;
import studio.echo.platform.service.Repository;
import studio.echo.platform.util.LogUtil;

/**
 * Repository 구현체로, 애플리케이션의 설정 파일 및 환경 정보를 관리합니다.
 * <p>
 * 주요 기능:
 * <ul>
 *   <li>애플리케이션의 루트 리소스 및 설정 파일 관리</li>
 *   <li>환경 변수 및 프로퍼티 로깅 (민감 정보 마스킹 지원)</li>
 *   <li>애플리케이션 배너 및 로고 출력</li>
 *   <li>설정 변경 이벤트 및 상태 변경 이벤트 발행</li>
 *   <li>애플리케이션 구동 시간(uptime) 측정</li>
 * </ul>
 * 
 * <b>초기화 과정</b>
 * <ol>
 *   <li>배너 및 환경 정보 출력 여부에 따라 출력</li>
 *   <li>환경 변수에서 application home 설정 시도</li>
 *   <li>ServletContext를 통한 application home 설정 지원</li>
 *   <li>초기화 상태(State) 관리 및 이벤트 발행</li>
 * </ol>
 *
 * <b>환경 변수 로깅</b><br>
 * 환경 변수 로깅 시 {@link PropertyValidator#isSensitiveProperty(String)}를 통해
 * password, secret, key, token 등 민감한 프로퍼티는 값 대신 "***"로 마스킹 처리합니다.
 *
 * @author donghyuck, son
 * @since 2024-07-25
 * @version 1.1
 * 
 * <pre>
 * << 개정이력(Modification Information) >>
 *   수정일        수정자           수정내용
 *   2024-07-25 donghyuck, son: Initial creation.
 *   2024-09-19 donghyuck, son: Add support for reading the META-INF/logo
 * </pre>
 */
 
@Slf4j  
public class RepositoryImpl implements Repository, ServletContextAware {

	private static final String LOGO = "META-INF/logo";
	private AtomicBoolean initialized = new AtomicBoolean(false);

	private Resource rootResource ;

	private State state = State.NONE;

	private ApplicationEventPublisher applicationEventPublisher;

	private ApplicationProperties applicationProperties;

	private I18n i18n;

	private Environment env;

	private Instant startTime;

	public RepositoryImpl(
			@Qualifier(ServiceNames.APPLICATION_PROPERTIES) ApplicationProperties applicationProperties,
			@Qualifier(ServiceNames.I18N) I18n i18n,
			Environment env,
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.applicationProperties = applicationProperties;
		this.env = env;
		this.i18n = i18n;
		this.startTime = Instant.EPOCH;
		this.rootResource = null;
	}

	@Value("${" + PropertyKeys.Main.SHOW_BANNER + ":true}")
	public boolean showBanner;

	@Value("${" + PropertyKeys.Main.LOG_ENVIRONMENT + ":false}")
	public boolean showEnvironment;

	public void printLogo() {
		try {
			// Step 1: Read the META-INF/logo file
			ClassLoader cl = Thread.currentThread().getContextClassLoader();
			Enumeration<URL> resources = cl.getResources(LOGO);
			resources.asIterator().forEachRemaining(entry -> {
				// Use the MANIFEST.MF from the current JAR that contains the logo file
				String title = null;
				String version = null;
				try (InputStream in = entry.openStream()) {
					URL manifestUrl = getManifestUrl(entry);
					if (manifestUrl != null) {
						Manifest manifest = new Manifest(manifestUrl.openStream());
						Attributes attrs = manifest.getMainAttributes();
						title = attrs.getValue("Implementation-Title");
						version = attrs.getValue("Implementation-Version");
					}
					BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
					StringBuilder sb = new StringBuilder(Colors.BLACK_BACKGROUND).append(" ").append(version).append(" ").append(Colors.RESET);
					System.out.println(String.format(IOUtils.toString(reader), title, sb.toString()));

				} catch (IOException e) {
					// Log or handle the exception for MANIFEST issues
					log.warn(i18n.get(MessageCodes.Warn.MANIFEST_READ), e);
				}
			});
		} catch (IOException e) {
			log.warn(i18n.get(MessageCodes.Warn.LOGO_READ), e);
		}
	}

	private URL getManifestUrl(URL logoUrl) throws IOException {
		String urlStr = logoUrl.toString();
		if (urlStr.startsWith("jar:")) {
			String manifestPath = urlStr.substring(0, urlStr.indexOf("!/") + 2) + "META-INF/MANIFEST.MF";
			return new URL(manifestPath);
		}
		return null;
	}

	public ConfigRoot getConfigRoot() throws NotFoundException {
		try {
			File file = new File(getRootResource().getFile(), "config");
			log.debug(LogUtil.format(i18n, MessageCodes.Info.CONFIG_ROOT_ATTEMPT, LogUtil.blue(ConfigRoot.class, true), file.getPath() ));
			Resource child = new FileSystemResource(file);
			return new ConfigRootImpl(child);
		} catch (NullPointerException e) { 
			throw new NotFoundException(i18n.get(MessageCodes.Error.CONFIG_ROOT_NULL), e);
		} catch (IOException e) { 
			throw new NotFoundException(i18n.get(MessageCodes.Error.CONFIG_IO), e);
		} catch (Exception e) { 
			throw new NotFoundException(i18n.get(MessageCodes.Error.CONFIG_UNKNOWN), e);
		}
	}

	public File getFile(String name) throws IOException {
		try {
            return new File(getRootResource().getFile(), name);
        } catch (IOException e) {
            throw new IOException(LogUtil.format(i18n, MessageCodes.Error.FILE_ACCESS , name), e);
        }
	}

	private Resource getRootResource() {
		if (!initialized.get() || rootResource == null) {
            throw new IllegalStateException(i18n.get(MessageCodes.Error.CONFIG_ROOT_NOT_INITIALIZED));
        }
        return rootResource;
	}

	public void refresh() {
		throw new UnsupportedOperationException();
	}

	public ApplicationProperties getApplicationProperties() {
		return this.applicationProperties;
	}

	@PostConstruct
	void initialize() { 
		if (State.NONE != state) {
            log.warn(i18n.get(MessageCodes.Warn.CONFIG_ALREADY_INITIALIZED));
            return;
        } 
		if (showBanner){
			printLogo();
		}

		state = State.INITIALIZING; 
		log.info(LogUtil.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtil.blue(getClass(), true), LogUtil.red(state.toString())));
		
		if (showEnvironment && env instanceof AbstractEnvironment) {
			AbstractEnvironment abstractEnvironment = (AbstractEnvironment) env; // 명시적 캐스팅 유지
			logEnvironmentProperties(abstractEnvironment);
		}

		state = State.INITIALIZED;
		log.info(LogUtil.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtil.blue(getClass(), true), LogUtil.red(state.toString())));
		fireStateChangeEvent(State.INITIALIZING, State.INITIALIZED);
	}

	private void logEnvironmentProperties(AbstractEnvironment env) {

        log.debug(i18n.get(MessageCodes.Info.CONFIG_SOURCE_ENVIRONMENT) );
        Properties props = new Properties();
        MutablePropertySources propSrcs = env.getPropertySources();
        StreamSupport.stream(propSrcs.spliterator(), false)
                .filter(EnumerablePropertySource.class::isInstance)
                .map(ps -> (EnumerablePropertySource<?>) ps)
                .forEach(propertySource -> {
                    log.debug(i18n.get(MessageCodes.Info.CONFIG_SOURCE_IMPORT, propertySource.getName()));
                    Arrays.stream(propertySource.getPropertyNames())
                            .forEach(propName -> {
                                String propertyValue = env.getProperty(propName);
                                boolean secured = PropertyValidator.isSensitiveProperty(propName);
                                props.setProperty(propName, propertyValue);
								log.debug(
									i18n.get(MessageCodes.Info.CONFIG_SOURCE_PROPERTY, StringUtils.rightPad(propName, 35, "."), secured ? "***" : propertyValue )
								);
							});
                });
    }


	private void setHomeByEnvProperty() {
		String home = env.getProperty(PropertyKeys.Main.HOME);
		if (StringUtils.isNotEmpty(home)) {
			log.debug( i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_ATTEMPT, home) );
			File file = new File(home);
			if (file.exists()) {
				rootResource = new FileSystemResource(file);
				initialized.set(true);
				log.debug( i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_SET, Colors.format(Colors.GREEN, home) ) ); 
			}
		}
	}

	public void setServletContext(ServletContext servletContext) {
		setHomeByEnvProperty(); 
		if (!initialized.get()) { 
 			log.debug( i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_ATTEMPT_SERVLET ));
			ServletContextResource resource = new ServletContextResource(servletContext, "/WEB-INF");
			try {
				File file = resource.getFile();
				log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_EXISTS, file.getPath()) ); 
				rootResource = new FileSystemResource(file);
				initialized.set(true); 
				log.debug( i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_SET, Colors.format(Colors.GREEN, file.getPath()) ) ); 
			} catch (IOException e) {
				log.error(i18n.get(MessageCodes.Error.CONFIG_APPLICATION_HOME_FAILED), e ) ;
			}
		}
	}

	private void fireStateChangeEvent(State oldState, State newState) {
		StateChangeEvent event = new StateChangeEvent(this, oldState, newState);
		if (applicationEventPublisher != null)
			applicationEventPublisher.publishEvent(event);
	}

	@EventListener(condition = "#event.name eq 'startup'")
	public void handlePropertiesRefreshedEvent(PropertiesRefreshedEvent event) {
		this.refresh();
	}

	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		this.startTime = Instant.now();
	}

	public Duration getUptime() { 
		return Duration.between(startTime, Instant.now());
	}

}