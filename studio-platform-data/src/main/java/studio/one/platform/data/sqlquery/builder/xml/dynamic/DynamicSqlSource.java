
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
package studio.one.platform.data.sqlquery.builder.xml.dynamic;

import java.util.List;
import java.util.Map;

import studio.one.platform.data.sqlquery.mapping.BoundSql;
import studio.one.platform.data.sqlquery.mapping.ParameterMapping;
import studio.one.platform.data.sqlquery.mapping.ResultMapping;
import studio.one.platform.data.sqlquery.mapping.SqlSource;


public class DynamicSqlSource implements SqlSource {

	private SqlNode rootSqlNode;

	private List<ParameterMapping> parameterMappings;

	private List<ResultMapping> resultMappings;

	public DynamicSqlSource(SqlNode rootSqlNode, List<ParameterMapping> parameterMappings,	List<ResultMapping> resultMappings) {
		this.rootSqlNode = rootSqlNode;
		this.parameterMappings = parameterMappings;
		this.resultMappings = resultMappings;
	}
	
	public BoundSql getBoundSql(Object parameterObject) {
		// 1. 동적 쿼리 생성을 위한 컨텍스트 객체를 생성한다.
		DynamicContext context;
		if (parameterObject == null){
			context = new DynamicContext();
		}else{
			context = new DynamicContext(parameterObject);
		}
		// 2. 동적 쿼리를 생성한다.
		rootSqlNode.apply(context);
		// 3. 최종 쿼리를 리턴한다.
		return new BoundSql(context.getSql(), parameterMappings, parameterObject, resultMappings);
	}
	
	
	public BoundSql getBoundSql(Object parameterObject, Map<String, Object> additionalParameters) {
		// 1. 다이나믹 쿼리 생성을 위한 컨텍스트 객체를 생성한다.
		DynamicContext context = new DynamicContext(parameterObject, additionalParameters);
		// 2. 동적 쿼리를 생성한다.
		rootSqlNode.apply(context);
		// 3. 최종 쿼리를 리턴한다.
		return new BoundSql(context.getSql(), parameterMappings, parameterObject, resultMappings);
	}

	public BoundSql getBoundSqlFromCachedSql(String cachedSql, Object parameterObject) {
		return new BoundSql(cachedSql, parameterMappings, parameterObject, resultMappings);
	}
}
