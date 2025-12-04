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


package studio.one.platform.data.sqlquery.mapping;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MapperSource{

    private String id;
    
    private String name;
    
    private List<ParameterMapping> mappedFields;

    private Class<?> mappedClass;
    
    public String getName(){
	return name;
    }
        
    public String getId() {
        return id;
    }

    public void setId(String iD) {
        id = iD;
    }

    public List<ParameterMapping> getMappedFields() {
        return mappedFields;
    }

    
    public <T> RowMapper<T> createRowMapper(Class<T> requiredType){
		ParameterMappingRowMapper<T> mapper = new ParameterMappingRowMapper<>(requiredType);
		mapper.mapperSource = this;
		mapper.prepare();
		return mapper;
    }

    
    public static class Builder {

    	private MapperSource mappedRowMapper = new MapperSource();
		
    	public Builder(Class< ? > mappedClass, List<ParameterMapping> mappedFields) {
		    mappedRowMapper.mappedClass = mappedClass;
		    mappedRowMapper.mappedFields = mappedFields;
		}	
		
		public Builder name(String name) {
		    mappedRowMapper.name = name;
		    return this;
		}
		
		public MapperSource build() {
		    assert mappedRowMapper.mappedClass != null;
		    assert mappedRowMapper.mappedFields != null;
		    return mappedRowMapper;
		}
    }
        

    public static class ParameterMappingRowMapper<T> implements RowMapper<T> {
	/** The class we are mapping to */
	
	private Class<T> mappedClass;
	
	private MapperSource mapperSource;
	
	private Map<String, ParameterMapping> mappedFieldsMap;

	public ParameterMappingRowMapper() {
	    
	}
	
	public ParameterMappingRowMapper(Class<T> mappedClass) {
	    this.mappedClass = mappedClass;
	}
	
	public final Class<T> getMappedClass() {
	   return this.mappedClass;
	}
	
	protected void prepare(){
	    this.mappedFieldsMap = new HashMap<>();
	    for (ParameterMapping mapping : mapperSource.mappedFields) { 
		if( !StringUtils.isEmpty(mapping.getColumn())){
		    this.mappedFieldsMap.put( mapping.getColumn(), mapping );
		}
	    }
	}
	
	protected Object getColumnValue(ResultSet rs, int index, ParameterMapping pm) throws SQLException {
		if (pm.getJavaType() == String.class) {
			String value = rs.getString(index);
			if (value == null)
				return null;
			try {
				if (StringUtils.isNotEmpty(pm.getCipher())) {
					return decryptValue(value, pm);
				} else if (StringUtils.isNotEmpty(pm.getEncoding())) {
					return convertEncoding(value, pm.getEncoding());
				}
			} catch (Exception e) {
				log.error("Error decoding column value at index {}: {}", index, e.getMessage(), e);
				return value; // fallback: 원본 문자열 반환
			}
			return value;
		}
	
		if (pm.getJavaType() == Boolean.class) {
			return NumberUtils.toInt(rs.getString(index), 0) == 1;
		}
	
		return JdbcUtils.getResultSetValue(rs, index, pm.getJavaType());
	}
	
	private String decryptValue(String value, ParameterMapping pm) throws Exception {
		Cipher cipher = Cipher.getInstance(pm.getCipher());
		SecretKeySpec keySpec = new SecretKeySpec(Hex.decodeHex(pm.getCipherKey().toCharArray()), pm.getCipherKeyAlg());
		cipher.init(Cipher.DECRYPT_MODE, keySpec);
	
		byte[] raw = switch (pm.getEncoding() != null ? pm.getEncoding().toUpperCase() : "") {
			case "BASE64" -> Base64.decodeBase64(value);
			case "HEX" -> Hex.decodeHex(value.toCharArray());
			default -> value.getBytes();
		};
	
		return new String(cipher.doFinal(raw));
	}
	
	private String convertEncoding(String value, String encoding) throws Exception {
		String[] parts = StringUtils.split(encoding, ">");
		if (parts.length == 2) {
			return new String(value.getBytes(parts[0]), parts[1]);
		} else if (parts.length == 1) {
			return new String(value.getBytes(), parts[0]);
		}
		return value;
	}	
	
	protected Map<String, ParameterMapping> getMappedFieldsAsMap(){
	    return mappedFieldsMap;	    
	}
	
	public T mapRow(ResultSet rs, int rowNum) throws SQLException {	    
	    T mappedObject = BeanUtils.instantiateClass(mapperSource.mappedClass, mappedClass );
	    BeanWrapper bw = PropertyAccessorFactory.forBeanPropertyAccess(mappedObject);
	    
	    ResultSetMetaData rsmd = rs.getMetaData();
	    int columnCount = rsmd.getColumnCount();
		for (int index = 1; index <= columnCount; index++) {
		    String column = JdbcUtils.lookupColumnName(rsmd, index); 
		    if( getMappedFieldsAsMap().containsKey(column) ){
			ParameterMapping mapping = getMappedFieldsAsMap().get(column);
			log.debug( "{} set {} = {}" , mappedClass.getName() , mapping.getProperty(), getColumnValue(rs, index, mapping) );
			bw.setPropertyValue(mapping.getProperty(), getColumnValue(rs, index, mapping));
		    }
		}		
	    return mappedObject;
	}

    }
}