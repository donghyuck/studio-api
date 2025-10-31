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
 *      @file ConfigRootImpl.java
 *      @date 2025
 *
 */
package studio.echo.platform.component;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Optional;

import org.apache.commons.io.FilenameUtils;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import lombok.extern.slf4j.Slf4j;
import studio.echo.platform.exception.ConfigurationError;
import studio.echo.platform.service.ConfigRoot;

/**
 * An implementation of the {@link ConfigRoot} interface that provides access to
 * configuration files and directories. This class is responsible for resolving
 * configuration resources from a given root resource location.
 * 
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
@Slf4j
public class ConfigRootImpl implements ConfigRoot {

	 private final Resource rootResource;

	/**
	 * Creates a new {@code ConfigRootImpl} instance with the specified root
	 * resource.
	 *
	 * @param rootResource the root resource location for configuration files. Must
	 *                     not be {@code null}.
	 */
	public ConfigRootImpl(Resource rootResource) {
		Assert.notNull(rootResource, "Root resource must not be null");
		this.rootResource = rootResource;
	}

	/**
	 * Returns a {@link File} object for the specified configuration file name.
	 *
	 * @param name the name of the configuration file to retrieve.
	 * @return an {@link Optional} containing the configuration file, or an empty
	 *         {@link Optional} if the file cannot be resolved.
	 */
	public Optional<File> getFile(String name) {
		try {
			File file = new File(rootResource.getFile(), FilenameUtils.getName(name));
			return Optional.of(file);
		} catch (IOException e) {
			log.warn("Cannot resolve config file: {}", name, e);
			return Optional.empty();
		}
	}

	/**
	 * Returns the root directory for configuration files.
	 *
	 * @return an {@link Optional} containing the root directory, or an empty
	 *         {@link Optional} if the root directory cannot be resolved.
	 */
	public Optional<File> getRootFile() {
		try {
			return Optional.ofNullable(rootResource.getFile());
		} catch (IOException e) {
			log.warn("Cannot resolve root config file", e);
			return Optional.empty();
		}
	}

	/**
	 * Returns the URI of the root resource location.
	 *
	 * @return the URI of the root resource.
	 * @throws ConfigurationError if the root URI cannot be resolved.
	 */
	public URI getRootURI() {
		try {
			return rootResource.getURI();
		} catch (IOException e) {
			log.error("Failed to resolve root URI", e);
			throw new ConfigurationError("Cannot resolve config root URI", e);
		}
	}
}