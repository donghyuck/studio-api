
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

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import studio.one.platform.data.sqlquery.factory.impl.StaticModels;
import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.template.Configuration;
import freemarker.template.TemplateException;

/**
 *	동적으로 SQL 을 생성하는 노드를 나타내는 클래스이다.
 *	동적으로 생성되는 SQL 은 Freemarker 를 사용하여 처리한다.
 *  
 * @change 
 * 2025-03-20 :
 * - 불필요한 예외 처리 로깅 개선 (예외 메시지 추가)
 * - Map<?, ?> → Map<String, Object> 변환 로직 안전성 향상
 * - Freemarker 템플릿 예외 처리 개선
 * - BeansWrapper 인스턴스 재사용 최적화
 */
public class DynamicSqlNode implements SqlNode {

	public enum Language {
		VELOCITY, FREEMARKER
	}
	
	private static BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.VERSION_2_3_25).build();
	
	protected static void populateStatics(Map<String, Object> model) {
		model.put("enums", wrapper.getEnumModels());
        StaticModels.populateStatics(wrapper, model);
        model.put("statics", wrapper.getStaticModels());
	}
	
	protected Logger log = LoggerFactory.getLogger(getClass());

	private String text;

	private Language language = Language.FREEMARKER;

	public DynamicSqlNode(String text) {
		this.text = text;
	}

	/**
	 * 다이나믹 구현은 Freemarker 을 사용하여 처리한다. 따라서 나이나믹 처리를 위해서는 반듯이 freemarker 의 규칙을
	 * 사용하여야 한다.
	 */
	public boolean apply(DynamicContext context) {
		Map<String, Object> map = new HashMap<>();
		// 추가적인 파라미터 처리
		extractParameters(context.getBindings().get(DynamicContext.ADDITIONAL_PARAMETER_OBJECT_KEY), map, "additional_parameters");
		// 기본 파라미터 처리
		extractParameters(context.getBindings().get(DynamicContext.PARAMETER_OBJECT_KEY), map, "parameters");
		// SQL 템플릿 적용
		context.appendSql(processTemplate(map));
		return true;
	}

	private void extractParameters(Object paramObject, Map<String, Object> map, String defaultKey) {
		if (paramObject == null) return;
		if (paramObject instanceof Map<?, ?>) {
			((Map<?, ?>) paramObject).forEach((key, value) -> {
				if (key instanceof String) {
					map.put((String) key, value);
				}
			});
		} else if (paramObject instanceof MapSqlParameterSource) {
			map.putAll(((MapSqlParameterSource) paramObject).getValues());
		} else {
			map.put(defaultKey, paramObject);
		}
	}

	protected String processTemplate(Map<String, Object> model) {
		StringReader reader = new StringReader(text);
		StringWriter writer = new StringWriter();
		if( language == Language.FREEMARKER ){
			try { 
				populateStatics(model);
				freemarker.template.SimpleHash root = new freemarker.template.SimpleHash(wrapper);
				root.putAll(model);
				freemarker.template.Template template = new freemarker.template.Template("dynamic", reader, null);
				template.setNumberFormat("computer"); 
				template.process(root, writer);
			} catch (IOException  | TemplateException e) {
				log.error("Freemarker 처리 중 오류 발생: {}", e.getMessage(), e);
			} 
		}
		return writer.toString();
	}

	@Override
	public String toString() {
		return "dynamic[" + text + "]";
	}
}
