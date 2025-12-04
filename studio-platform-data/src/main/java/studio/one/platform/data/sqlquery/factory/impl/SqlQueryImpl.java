
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.CallableStatementCreatorFactory;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.util.Assert;

import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.data.sqlquery.mapping.BoundSql;
import studio.one.platform.data.sqlquery.mapping.ParameterMapping;
import studio.one.platform.data.sqlquery.ExtendedJdbcDaoSupport;
import studio.one.platform.data.sqlquery.SqlQuery;

/**
 * SqlQuery 인터페이스를 구현한 클래스로 SQL 쿼리를 실행하고 결과를 반환하는 기능을 제공한다.
 * 이 클래스는 스프링 프레임워크의 JdbcDaoSupport 클래스를 확장하고 있으며,
 * 단일 또는 복수의 결과를 반환할 수 있는 쿼리를 실행하고 결과를 반환한다.
 * 
 * 이 클래스는 SqlQueryFactory 클래스를 통해 생성되어야 한다.
 * 
 * SqlQuery sqlQuery = sqlQueryFactory.createSqlQuery();
 * 
 * sqlQuery.setStartIndex(0);
 * sqlQuery.setMaxResults(10);
 * 
 * List<Map<String, Object>> list = sqlQuery.queryForList("GET_USERS");
 * 
 * Map<String, Object> user = sqlQuery.queryForObject("GET_USER_BY_ID", 1004);
 * 
 * int count = sqlQuery.executeUpdate("UPDATE_USER", "Donghyuck Son", 1004);
 * 
 * sqlQuery.call("GET_USER_BY_ID", 1004);
 */
public class SqlQueryImpl extends ExtendedJdbcDaoSupport implements SqlQuery {

	private int startIndex = DEFAULT_START_INDEX;

	private int maxResults = DEFAULT_MAX_RESULTS;

	private final Map<String, Object> additionalParameters = new ConcurrentHashMap<>();

	public SqlQueryImpl() {
		super();
	}

	public SqlQueryImpl(SqlQueryFactory sqlQueryFactory) {
		setSqlQueryFactory(sqlQueryFactory);
	}

	public SqlQuery setStartIndex(int startIndex) {
		this.startIndex = startIndex;
		return this;
	}

	public SqlQuery setMaxResults(int maxResults) {
		this.maxResults = maxResults;
		return this;
	}

	public SqlQuery setAdditionalParameters(Map<String, Object> additionalParameters) {
		this.additionalParameters.clear();
		this.additionalParameters.putAll(additionalParameters);
		return this;
	}

	@Override
	public BoundSql getBoundSql(String statement) {
		BoundSql sql;
		if (additionalParameters.size() > 0) {
			sql = super.getBoundSqlWithAdditionalParameter(statement, additionalParameters);
		} else {
			sql = super.getBoundSql(statement);
		}
		additionalParameters.clear();
		return sql;
	}

	private IllegalStateException newIllegalStateException(String statemenKey) {
		return new IllegalStateException("SQL text is null for query ID: " + statemenKey);
	}

	private IllegalArgumentException newIllegalArgumentException(String statemenKey) {
		return new IllegalArgumentException("Invalid query ID: " + statemenKey);
	}

	public Map<String, Object> queryForObject(String statemenKey, Object... params) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryForMap(sqlText, Optional.ofNullable(params).orElse(new Object[0]));
	}

	public <T> T queryForObject(String statemenKey, Class<T> elementType, Object... params) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryForObject(sqlText, elementType,
				Optional.ofNullable(params).orElse(new Object[0]));
	}

	@Override
	public List<Map<String, Object>> queryForList(String statemenKey) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		if (startIndex > 0 && maxResults > 0) {
			return getPagingJdbcTemplate().queryPage(sqlText, startIndex, maxResults);
		}
		return getPagingJdbcTemplate().queryForList(sqlText);
	}

	@Override
	public List<Map<String, Object>> queryForList(String statemenKey, int startIndex, int maxResults) {
		checkStartIndexAndMaxResults(startIndex, maxResults);
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryPage(sqlText, startIndex, maxResults);
	}

	public List<Map<String, Object>> queryForList(String statemenKey, Object... params) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryForList(sqlText, Optional.ofNullable(params).orElse(new Object[0]));
	}

	public List<Map<String, Object>> queryForList(String statemenKey, int startIndex, int maxResults,
			Object... params) {
		checkStartIndexAndMaxResults(startIndex, maxResults);
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryPage(sqlText, startIndex, maxResults,
				Optional.ofNullable(params).orElse(new Object[0]));
	}

	public <T> List<T> queryForList(String statemenKey, Class<T> elementType) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryForList(sqlText, elementType);
	}

	public <T> List<T> queryForList(String statemenKey, Class<T> elementType, Object... params) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryForList(sqlText, elementType,
				Optional.ofNullable(params).orElse(new Object[0]));
	}

	@Override
	public <T> List<T> queryForList(String statemenKey, int startIndex, int maxResults, Class<T> elementType) {
		checkStartIndexAndMaxResults(startIndex, maxResults);
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		return getPagingJdbcTemplate().queryPage(sqlText, startIndex, maxResults, elementType);
	}

	public <T> List<T> queryForList(String statemenKey, int startIndex, int maxResults, Class<T> elementType,
			Object... params) {
		checkStartIndexAndMaxResults(startIndex, maxResults);
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));
		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));
		return getPagingJdbcTemplate().queryPage(sqlText, startIndex, maxResults, elementType,
				Optional.ofNullable(params).orElse(new Object[0]));
	}

	private void checkStartIndexAndMaxResults(int startIndex, int maxResults) {
		Assert.isTrue(startIndex >= 0,
				() -> "startIndex is " + startIndex + " but it must be greater then or equal to zero.");
		Assert.isTrue(maxResults > 0,
				() -> "maxResults is " + maxResults + " but it must be greater then zero.");
	}

	@Override
	public int executeUpdate(String statemenKey) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));
		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));
		return getPagingJdbcTemplate().update(sqlText);
	}

	@Override
	public int executeUpdate(String statemenKey, Object... parameters) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));
		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));
		return getPagingJdbcTemplate().update(sqlText, parameters);
	}

	// @Override
	// public Object executeScript(String statemenKey, boolean stopOnError) {
	// BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
	// .orElseThrow(() -> newIllegalArgumentException(statemenKey));
	// String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
	// .orElseThrow(() -> newIllegalStateException(statemenKey));
	// return getPagingJdbcTemplate().executeScript(stopOnError, new
	// StringReader(sqlText));
	// }

	@Override
	public Object call(String statemenKey, Object... parameters) {
		BoundSql boundSql = Optional.ofNullable(getBoundSql(statemenKey))
				.orElseThrow(() -> newIllegalArgumentException(statemenKey));

		List<SqlParameter> declaredParameters = new ArrayList<>();
		Map<String, Object> paramsToUse = new HashMap<>();

		parameters = Optional.ofNullable(parameters).orElse(new Object[0]);

		// 메핑 파라메터에 따라 INPUT 과 OUTPU 을 설정한다.
		for (ParameterMapping mapping : boundSql.getParameterMappings()) {
			mapping.getProperty();
			mapping.getJdbcType();
			mapping.getMode();
			if (mapping.getMode() == ParameterMapping.Mode.IN) {
				SqlParameter input = new SqlParameter(mapping.getProperty(), mapping.getJdbcType().ordinal());
				declaredParameters.add(input);
				paramsToUse.put(mapping.getProperty(), parameters[mapping.getIndex() - 1]);

			} else if (mapping.getMode() == ParameterMapping.Mode.OUT) {
				SqlOutParameter output = new SqlOutParameter(mapping.getProperty(), mapping.getJdbcType().ordinal());
				declaredParameters.add(output);
			}
		}

		String sqlText = Optional.ofNullable(getBoundSqlText(boundSql))
				.orElseThrow(() -> newIllegalStateException(statemenKey));

		CallableStatementCreatorFactory callableStatementFactory = new CallableStatementCreatorFactory(sqlText,
				declaredParameters);
		return getPagingJdbcTemplate().call(callableStatementFactory.newCallableStatementCreator(paramsToUse),
				declaredParameters);
	}

	private static final Pattern UNSAFE_SQL_CHARS = Pattern.compile("[<>,*^&%$#@/\\\\+\\-()]");

	private String getBoundSqlText(BoundSql boundSql) {
		return (boundSql != null) ? UNSAFE_SQL_CHARS.matcher(boundSql.getSql()).replaceAll("") : null;
	}
}