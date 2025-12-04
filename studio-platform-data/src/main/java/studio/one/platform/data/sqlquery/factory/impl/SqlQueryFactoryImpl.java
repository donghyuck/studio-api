
/**
 *    Copyright 2015-2017 donghyuck
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package studio.one.platform.data.sqlquery.factory.impl;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import lombok.extern.slf4j.Slf4j;
import studio.one.platform.component.State;
import studio.one.platform.constant.MessageCodes;
import studio.one.platform.data.sqlquery.SqlQuery;
import studio.one.platform.data.sqlquery.builder.BuilderException;
import studio.one.platform.data.sqlquery.builder.xml.XmlSqlSetBuilder;
import studio.one.platform.data.sqlquery.factory.Configuration;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.util.LogUtils;
 
@Slf4j
public class SqlQueryFactoryImpl implements SqlQueryFactory {

	private final Configuration configuration;

	private List<String> resourceLocations;

	public SqlQueryFactoryImpl(Configuration configuration) {
		this.configuration = configuration;
	}

	protected void buildFromResourceLocations() {
		DefaultResourceLoader loader = new DefaultResourceLoader();
		for (String location : resourceLocations) {
			Resource resource = loader.getResource(location);
			if (resource.exists() && !isResourceLoaded(resource)) { 
				try {					
					log.debug(LogUtils.format(configuration.getI18n(), "debug.data.sql.load", resource.getURI().toString()));
					XmlSqlSetBuilder builder = new XmlSqlSetBuilder(resource.getInputStream(), configuration, resource.getURI().toString(), null);
					builder.parse();
				} catch (IOException e) {
					throw new BuilderException(LogUtils.format(configuration.getI18n(), "error.data.sql.buildfail", resource ), e);
				}
			}
		}
	}

	public SqlQuery createSqlQuery() {
		return new SqlQueryImpl(this);
	}

	public SqlQuery createSqlQuery(DataSource dataSource) {
		SqlQueryImpl impl = new SqlQueryImpl(this);
		impl.setDataSource(dataSource);
		return impl;
	}

	protected void fireStateChangeEvent(Object soruce, State oldState, State newState) {
		throw new UnsupportedOperationException("Not supported yet.");
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public List<String> getResourceLocations() {
		return resourceLocations;
	}

	public void setStaticModels(Map<String, String> staticModels) {
		if (staticModels != null && staticModels.size() > 0) {
			for (Map.Entry<String, String> entry : staticModels.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				StaticModels.getStaticModels().put(key, value);
			}
		}
		log.info("Set staticModels : {}, {}", staticModels != null && staticModels.size() > 0,
				StaticModels.getStaticModels());
	}

	public void initialize() {
		// log.info(LogLocalizer.format("002001", getClass().getName(),
		// Colors.format(Colors.RED, State.INITIALIZING.name()) ));
		// if ( resourceLocations!=null && !resourceLocations.isEmpty()) {
		// buildFromResourceLocations();
		// }
		// log.info(LogLocalizer.format("002001", getClass().getName(),
		// Colors.format(Colors.RED, State.INITIALIZED.name()) ));

		log.info(LogUtils.format(configuration.getI18n(), MessageCodes.Info.COMPONENT_STATE, LogUtils.blue(getClass(), true), LogUtils.red(State.INITIALIZED.toString())));
	}

	protected boolean isResourceLoaded(Resource resource) {
		try {
			return configuration.isResourceLoaded(resource.getURI().toString());
		} catch (IOException e) {
			return false;
		}
	}

	public void setResourceLocations(List<String> resourceLocations) {
		this.resourceLocations = resourceLocations;
	}

}
