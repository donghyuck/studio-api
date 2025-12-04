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
package studio.one.platform.data.sqlquery.factory;

import org.springframework.beans.factory.ObjectProvider;

import studio.one.platform.data.sqlquery.factory.impl.SqlQueryFactoryImpl;
import studio.one.platform.service.I18n;

public class SqlQueryFactoryBuilder {

	private SqlQueryFactoryBuilder() {
	}

	public static SqlQueryFactory build(ObjectProvider<I18n> i18nProvider) {
		Configuration config = new Configuration(i18nProvider);
		return new SqlQueryFactoryImpl(config);
	}

	public static SqlQueryFactory build(Configuration config) {
		return new SqlQueryFactoryImpl(config);
	}

}
