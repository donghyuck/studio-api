/* 
 *  
 *      Copyright 2022-2023 donghyuck.son
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


package studio.one.platform.data.sqlquery.factory.impl;

import java.util.HashMap;
import java.util.Map;

import freemarker.ext.beans.BeansWrapper;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModelException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StaticModels {
	
	private StaticModels() {
	}

	private static final Map<String, String> models = new HashMap<>();

	protected static Map<String, String> getStaticModels() {
		return models;
	}  
	
	public static void populateStatics(BeansWrapper wrapper , Map<String, Object> model) {  
		TemplateHashModel staticHashModels = wrapper.getStaticModels();
		try {
			for ( Map.Entry<String, String> entry : models.entrySet()) {
				String key = entry.getKey();
				String value = entry.getValue();
				model.put(key,	staticHashModels.get(value));
			}		
		} catch (TemplateModelException e) {
			log.error(e.getMessage(), e);
		} 
	} 
}
