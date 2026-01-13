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

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import studio.one.platform.data.sqlquery.factory.Configuration;

public class MappedStatement {

	private static final int DEFAULT_SQL_CACHE_SIZE = 256;
	private static final Logger log = LoggerFactory.getLogger(MappedStatement.class);

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

	private volatile SqlTextCache sqlTextCache;

	public BoundSql getBoundSql(Object parameterObject) {
		return sqlSource.getBoundSql(parameterObject);		
	}

	public BoundSql getBoundSql(Object parameterObject, Object additionalParameters) {
		Map<String, Object> params = Optional.ofNullable(additionalParameters)
			.filter(Map.class::isInstance)
			.map(map -> (Map<String, Object>) map)
			.orElseGet(() -> {
				if (additionalParameters == null) {
					return Collections.emptyMap();
				}
				Map<String, Object> defaultParams = new HashMap<>();
				defaultParams.put("additional_parameter", additionalParameters);
				return defaultParams;
			});
	
		return sqlSource.getBoundSql(parameterObject, params);
	}

	public BoundSql getBoundSqlCached(Object parameterObject, Object additionalParameters, Object cacheKey) {
		if (cacheKey == null) {
			return getBoundSql(parameterObject, additionalParameters);
		}
		String cachedSql = getSqlTextCache().get(cacheKey);
		if (cachedSql != null) {
			if (log.isDebugEnabled()) {
				log.debug("MappedStatement cache hit: id={}, key={}", id, cacheKey);
			}
			return buildCachedBoundSql(cachedSql, parameterObject);
		}
		long startNanos = System.nanoTime();
		BoundSql boundSql = getBoundSql(parameterObject, additionalParameters);
		getSqlTextCache().put(cacheKey, boundSql.getSql());
		if (log.isDebugEnabled()) {
			long elapsedMicros = (System.nanoTime() - startNanos) / 1_000;
			log.debug("MappedStatement cache miss: id={}, key={}, buildMicros={}", id, cacheKey, elapsedMicros);
		}
		return boundSql;
	}

	private BoundSql buildCachedBoundSql(String cachedSql, Object parameterObject) {
		return sqlSource.getBoundSqlFromCachedSql(cachedSql, parameterObject);
	}

	private SqlTextCache getSqlTextCache() {
		if (sqlTextCache == null) {
			synchronized (this) {
				if (sqlTextCache == null) {
					sqlTextCache = new SqlTextCache(DEFAULT_SQL_CACHE_SIZE);
				}
			}
		}
		return sqlTextCache;
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

	private static class SqlTextCache {

		private final Map<Object, String> cache;

		SqlTextCache(int maxSize) {
			this.cache = Collections.synchronizedMap(new LinkedHashMap<Object, String>(16, 0.75f, true) {
				private static final long serialVersionUID = 1L;

				@Override
				protected boolean removeEldestEntry(Map.Entry<Object, String> eldest) {
					return size() > maxSize;
				}
			});
		}

		String get(Object key) {
			return cache.get(key);
		}

		void put(Object key, String sql) {
			cache.put(key, sql);
		}
	}
}
