/*
 * Copyright 2016 donghyuck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package studio.one.platform.data.sqlquery.mapping;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import studio.one.platform.data.sqlquery.factory.Configuration;

public class MappedStatement {

	public static class Builder {

		private MappedStatement mappedStatement = new MappedStatement();

		public Builder(Configuration configuration, String id, SqlSource sqlSource, StatementType statementType) {
			mappedStatement.configuration = configuration;
			mappedStatement.id = id;
			mappedStatement.sqlSource = sqlSource;
			mappedStatement.statementType = StatementType.PREPARED;
			mappedStatement.statementType = statementType;
			mappedStatement.timeout = configuration.getDefaultStatementTimeout();
		}

		public MappedStatement build() {
			assert mappedStatement.configuration != null;
			assert mappedStatement.id != null;
			assert mappedStatement.sqlSource != null;
			return mappedStatement;
		}

		public Builder description(String description) {
			mappedStatement.description = description;
			return this;
		}

		public Builder fetchSize(Integer fetchSize) {
			mappedStatement.fetchSize = fetchSize;
			return this;
		}

		public String id() {
			return mappedStatement.id;
		}

		public Builder resource(String resource) {
			mappedStatement.resource = resource;
			return this;
		}

		public Builder statementType(StatementType statementType) {
			mappedStatement.statementType = statementType;
			return this;
		}

		public Builder timeout(Integer timeout) {
			mappedStatement.timeout = timeout;
			return this;
		}
	}

	private String resource;

	private Configuration configuration;

	private String id;

	private Integer fetchSize;

	private Integer timeout;

	private SqlSource sqlSource;

	private StatementType statementType;

	private String description;

	public BoundSql getBoundSql(Object parameterObject) {
		return sqlSource.getBoundSql(parameterObject);		
	}

	public BoundSql getBoundSql(Object parameterObject, Object additionalParameters) {
		Map<String, Object> params = Optional.ofNullable(additionalParameters)
			.filter(Map.class::isInstance)
			.map(map -> (Map<String, Object>) map)
			.orElseGet(() -> {
				Map<String, Object> defaultParams = new HashMap<>();
				defaultParams.put("additional_parameter", additionalParameters);
				return defaultParams;
			});
	
		return sqlSource.getBoundSql(parameterObject, params);
	}

	public Configuration getConfiguration() {
		return configuration;
	}

	public String getDescription() {
		return this.description;
	}

	public Integer getFetchSize() {
		return fetchSize;
	}

	public String getId() {
		return this.id;
	}

	public String getResource() {
		return resource;
	}

	public SqlSource getSqlSource() {
		return sqlSource;
	}

	public StatementType getStatementType() {
		return statementType;
	}

	public Integer getTimeout() {
		return timeout;
	}
}
