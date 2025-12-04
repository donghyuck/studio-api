
/**
 *    Copyright 2015-2016 donghyuck
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
package studio.one.platform.data.sqlquery.builder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.one.platform.data.sqlquery.factory.Configuration;
import studio.one.platform.data.sqlquery.mapping.MappedStatement;
import studio.one.platform.data.sqlquery.mapping.MapperSource;
import studio.one.platform.data.sqlquery.mapping.SqlSource;
import studio.one.platform.data.sqlquery.mapping.StatementType;


public class SqlQueryBuilderAssistant extends AbstractBuilder {

	private String currentNamespace;

	private String resource;

	private Logger logger = LoggerFactory.getLogger(SqlQueryBuilderAssistant.class);

	public SqlQueryBuilderAssistant(Configuration configuration, String resource) {
		super(configuration);
		this.resource = resource;
	}

	public MapperSource addMapperSource(String id, MapperSource  mapperSource){
		String idToUse = applyCurrentNamespace(id);
		
		mapperSource.setId(idToUse);
		if(logger.isTraceEnabled())
			logger.trace( "Mapped Source {}", mapperSource.getId() );
		configuration.addMapper(mapperSource);
		return mapperSource;
	}
	
	public MappedStatement addMappedStatement(String id, String description, SqlSource sqlSource, StatementType statementType, Integer fetchSize, Integer timeout) {
		
		String idToUse = applyCurrentNamespace(id);		
		MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, idToUse, sqlSource, statementType);
		statementBuilder.resource(resource);
		statementBuilder.fetchSize(fetchSize);
		statementBuilder.description(description);
		setStatementTimeout(timeout, statementBuilder);
		MappedStatement statement = statementBuilder.build();		
		if(logger.isTraceEnabled())
			logger.trace( "Mapped Statement {}", statement.getId() );
		
		configuration.addMappedStatement(statement);
		
		return statement;
	}

	public String applyCurrentNamespace(String base) {
		if (base == null)
			return null;
		if (base.contains("."))
			return base;
		return currentNamespace + "." + base;
	}

	public String getCurrentNamespace() {
		return currentNamespace;
	}

	public void setCurrentNamespace(String currentNamespace) {
		if (currentNamespace == null) {
			throw new BuilderException("The mapper element requires a namespace attribute to be specified.");
		}
		if (this.currentNamespace != null && !this.currentNamespace.equals(currentNamespace)) {
			throw new BuilderException( "Wrong namespace. Expected '" + this.currentNamespace + "' but found '" + currentNamespace + "'.");
		}
		this.currentNamespace = currentNamespace;
	}

	private void setStatementTimeout(Integer timeout, MappedStatement.Builder statementBuilder) {
		if (timeout == null) {
			timeout = configuration.getDefaultStatementTimeout();
		}
		statementBuilder.timeout(timeout);
	}
}