/* 
 *  
 *      Copyright 2023 donghyuck.son
 *  
 *      Licensed under the Apache License, Version 2.0 (the "License");
 *      you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *      Unless required by applicable law or agreed to in writing, software
 *      distributed under the License is distributed on an "AS IS" BASIS,
 *      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *      See the License for the specific language governing permissions and
 *      limitations under the License.
 *  
 *  
 */

package studio.one.platform.data.sqlquery;

import studio.one.platform.data.sqlquery.factory.Configuration;
import studio.one.platform.data.sqlquery.factory.SqlQueryFactory;
import studio.one.platform.data.sqlquery.mapping.BoundSql;
import studio.one.platform.data.sqlquery.mapping.MappedStatement;
import studio.one.platform.data.sqlquery.mapping.MapperSource;

public class SqlHelper {
    
    private Configuration configuration;

	private SqlHelper(Configuration configuration) {
        this.configuration = configuration;
    }

    private SqlHelper(SqlQueryFactory sqlQueryFactory) {
        this.configuration = sqlQueryFactory.getConfiguration();
    }

    public BoundSql getBoundSql(String statement) {
		if (isSetConfiguration()) {
			MappedStatement stmt = configuration.getMappedStatement(statement);
			return stmt.getBoundSql(null);
		}
		return null;
	}

	public BoundSql getBoundSql(String statement, Object... params) {
		if (isSetConfiguration()) {
			MappedStatement stmt = configuration.getMappedStatement(statement);
			return stmt.getBoundSql(params);
		}
		return null;
	}

	public BoundSql getBoundSqlWithAdditionalParameter(String statement, Object additionalParameter) {
		if (isSetConfiguration()) {
			MappedStatement stmt = configuration.getMappedStatement(statement);
			return stmt.getBoundSql(null, additionalParameter);
		}
		return null;
	}

	public BoundSql getBoundSqlWithAdditionalParameter(String statement, Object parameters,
			Object additionalParameter) {
		if (isSetConfiguration()) {
			MappedStatement stmt = configuration.getMappedStatement(statement);
			return stmt.getBoundSql(parameters, additionalParameter);
		}
		return null;
	}
	
	public MapperSource getMapperSource(String name) {
		MapperSource source = null;
		if (isSetConfiguration()) {
			source = configuration.getMapper(name); 
		}
		return source;
	}

    public boolean isSetConfiguration() {
		return configuration != null;
	}
    
    public static class Builder{	

		private Builder() {}
		
		public static SqlHelper build(SqlQueryFactory sqlQueryFactory) {
		    return new SqlHelper(sqlQueryFactory);
		}

		public static SqlHelper build(Configuration configuration) {
		    return new SqlHelper(configuration);
		}

	}
}
