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
package studio.one.platform.component;

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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.context.support.ServletContextResource;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.event.PropertiesRefreshedEvent;
import studio.one.platform.component.event.StateChangeEvent;
import studio.one.platform.constant.Colors;
import studio.one.platform.constant.MessageCodes;
import studio.one.platform.constant.PropertyKeys;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.exception.NotFoundException;
import studio.one.platform.service.ApplicationProperties;
import studio.one.platform.service.ConfigRoot;
import studio.one.platform.service.DomainEvents;
import studio.one.platform.service.I18n;
import studio.one.platform.service.Repository;
import studio.one.platform.util.LogUtils;

/**
 * An implementation of the {@link Repository} interface that manages the
 * application's configuration files and environment information.
 * <p>
 * This class is responsible for:
 * <ul>
 * <li>Managing the application's root resource and configuration files.</li>
 * <li>Logging environment variables and properties, with support for masking
 * sensitive information.</li>
 * <li>Printing the application banner and logo.</li>
 * <li>Publishing configuration and state change events.</li>
 * <li>Measuring the application's uptime.</li>
 * </ul>
 *
 * <h2>Initialization Process</h2>
 * <ol>
 * <li>Prints the banner and environment information based on configuration.</li>
 * <li>Attempts to set the application home directory from environment
 * variables.</li>
 * <li>Supports setting the application home directory via the
 * {@link ServletContext}.</li>
 * <li>Manages and publishes initialization state changes.</li>
 * </ol>
 *
 * <h2>Environment Variable Logging</h2>
 * When logging environment variables, this class uses
 * {@link PropertyValidator#isSensitiveProperty(String)} to mask sensitive
 * properties (e.g., password, secret, key, token) with "***".
 *
 * @author donghyuck, son
 * @since 2024-07-25
 * @version 1.1
 */
@Slf4j
public class RepositoryImpl implements Repository, DomainEvents, ServletContextAware {

	private static final String LOGO = "META-INF/logo";

	private AtomicBoolean initialized = new AtomicBoolean(false);

	private Resource rootResource = null;

	private State state = State.NONE;

	private final ApplicationEventPublisher applicationEventPublisher;

	private final ApplicationProperties applicationProperties;

	private final  I18n i18n;
	
	private final Environment env;

	private Instant startTime = Instant.EPOCH;

	/**
	 * Creates a new {@code RepositoryImpl} instance.
	 *
	 * @param applicationProperties     the application properties component.
	 * @param i18n                      the internationalization component.
	 * @param env                       the application environment.
	 * @param applicationEventPublisher the event publisher.
	 */
	public RepositoryImpl(
			@Qualifier(ServiceNames.APPLICATION_PROPERTIES) ApplicationProperties applicationProperties,
			@Qualifier(ServiceNames.I18N) I18n i18n,
			Environment env,
			ApplicationEventPublisher applicationEventPublisher) {
		this.applicationEventPublisher = applicationEventPublisher;
		this.applicationProperties = applicationProperties;
		this.env = env;
		this.i18n = i18n; 
	}

	/**
	 * Whether to show the banner on startup. Defaults to {@code true}.
	 */
	@Value("${" + PropertyKeys.Main.SHOW_BANNER + ":true}")
	public boolean showBanner;

	/**
	 * Whether to log environment properties on startup. Defaults to {@code false}.
	 */
	@Value("${" + PropertyKeys.Main.LOG_ENVIRONMENT + ":false}")
	public boolean showEnvironment;

	/**
	 * Prints the application logo from the {@code META-INF/logo} resource.
	 */
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
					StringBuilder sb = new StringBuilder(Colors.BLACK_BACKGROUND).append(" ").append(version)
							.append(" ").append(Colors.RESET);
					System.out.println(String.format(IOUtils.toString(reader), title, sb.toString())); // NOSONAR:
																										// user-facing
																										// CLI output

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

	/**
	 * {@inheritDoc}
	 *
	 * @throws NotFoundException if the configuration root cannot be found.
	 */
	public ConfigRoot getConfigRoot() throws NotFoundException {
		try {
			File file = new File(getRootResource().getFile(), "config");
			log.debug(LogUtils.format(i18n, MessageCodes.Info.CONFIG_ROOT_ATTEMPT,
					LogUtils.blue(ConfigRoot.class, true), file.getPath()));
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

	/**
	 * {@inheritDoc}
	 *
	 * @throws IOException if the file cannot be accessed.
	 */
	public File getFile(String name) throws IOException {
		try {
			return new File(getRootResource().getFile(), name);
		} catch (IOException e) {
			throw new IOException(LogUtils.format(i18n, MessageCodes.Error.FILE_ACCESS, name), e);
		}
	}

	private Resource getRootResource() {
		if (!initialized.get() || rootResource == null) {
			throw new IllegalStateException(i18n.get(MessageCodes.Error.CONFIG_ROOT_NOT_INITIALIZED));
		}
		return rootResource;
	}

	/**
	 * Refreshes the repository configuration.
	 *
	 * @throws UnsupportedOperationException currently not implemented.
	 */
	public void refresh() {
		throw new UnsupportedOperationException();
	}

	/**
	 * {@inheritDoc}
	 */
	public ApplicationProperties getApplicationProperties() {
		return this.applicationProperties;
	}

	/**
	 * Initializes the repository after construction. This method is called by
	 * Spring and is responsible for setting up the repository's state, printing the
	 * banner, and logging environment properties.
	 */
	@PostConstruct
	void initialize() {
		if (State.NONE != state) {
			log.warn(i18n.get(MessageCodes.Warn.CONFIG_ALREADY_INITIALIZED));
			return;
		}
		if (showBanner) {
			printLogo();
		}

		state = State.INITIALIZING;
		log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true),
				LogUtils.red(state.toString())));

		if (showEnvironment && env instanceof AbstractEnvironment) {
			AbstractEnvironment abstractEnvironment = (AbstractEnvironment) env; // 명시적 캐스팅 유지
			logEnvironmentProperties(abstractEnvironment);
		}

		state = State.INITIALIZED;
		log.info(LogUtils.format(i18n, MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true),
				LogUtils.red(state.toString())));
		fireStateChangeEvent(State.INITIALIZING, State.INITIALIZED);
	}

	private void logEnvironmentProperties(AbstractEnvironment env) {

		log.debug(i18n.get(MessageCodes.Info.CONFIG_SOURCE_ENVIRONMENT));
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
										i18n.get(MessageCodes.Info.CONFIG_SOURCE_PROPERTY,
												StringUtils.rightPad(propName, 35, "."),
												secured ? "***" : propertyValue));
							});
				});
	}

	private void setHomeByEnvProperty() {
		String home = env.getProperty(PropertyKeys.Main.HOME);
		if (StringUtils.isNotEmpty(home)) {
			log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_ATTEMPT, home));
			File file = new File(home);
			if (file.exists()) {
				rootResource = new FileSystemResource(file);
				initialized.set(true);
				log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_SET, Colors.format(Colors.GREEN, home)));
			}
		}
	}

	/**
	 * Sets the application's home directory using the servlet context if it has not
	 * already been set by an environment property.
	 *
	 * @param servletContext the servlet context.
	 */
	public void setServletContext(ServletContext servletContext) {
		setHomeByEnvProperty();
		if (!initialized.get()) {
			log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_ATTEMPT_SERVLET));
			ServletContextResource resource = new ServletContextResource(servletContext, "/WEB-INF");
			try {
				File file = resource.getFile();
				log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_EXISTS, file.getPath()));
				rootResource = new FileSystemResource(file);
				initialized.set(true);
				log.debug(i18n.get(MessageCodes.Info.CONFIG_APPLICATION_HOME_SET,
						Colors.format(Colors.GREEN, file.getPath())));
			} catch (IOException e) {
				log.error(i18n.get(MessageCodes.Error.CONFIG_APPLICATION_HOME_FAILED), e);
			}
		}
	}

	private void fireStateChangeEvent(State oldState, State newState) {
		StateChangeEvent event = new StateChangeEvent(this, oldState, newState);
		if (applicationEventPublisher != null)
			applicationEventPublisher.publishEvent(event);
	}

	/**
	 * Handles the {@link PropertiesRefreshedEvent} to refresh the repository.
	 *
	 * @param event the properties refreshed event.
	 */
	@EventListener(condition = "#event.name eq 'startup'")
	public void handlePropertiesRefreshedEvent(PropertiesRefreshedEvent event) {
		this.refresh();
	}

	/**
	 * Handles the {@link ApplicationReadyEvent} to record the application's startup
	 * time.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void onApplicationReady() {
		this.startTime = Instant.now();
	}

	/**
	 * Returns the application's uptime.
	 *
	 * @return the duration since the application has been running.
	 */
	public Duration getUptime() {
		return Duration.between(startTime, Instant.now());
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void publish(Object event) {
		if (applicationEventPublisher != null)
			applicationEventPublisher.publishEvent(event);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void publishAfterCommit(Object event) {
		
		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			// 현재 트랜잭션 커밋 후에 발행
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					applicationEventPublisher.publishEvent(event);
				}
			});
		} else { 
			applicationEventPublisher.publishEvent(event);
		}
	}
}