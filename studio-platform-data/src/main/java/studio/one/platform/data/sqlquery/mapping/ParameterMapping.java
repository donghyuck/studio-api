/*
 * Copyright 2012 Donghyuck, Son
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

/**
 * A parameter mapping defines the mapping from a single parameter in a mapped
 * statement to a Java method argument or a key / value in a Java Map.
 * 
 * <p>
 * It may contain information about the property that is used in the parameter
 * object, the key that is used in the key / value pair in a map, the type handler
 * that should be used to map the parameter and additional configuration
 * information that may be used to customize the parameter mapping.
 * </p>
 */
public class ParameterMapping {

	public static class Builder {

		/**
		 * @uml.property name="parameterMapping"
		 * @uml.associationEnd
		 */
		private ParameterMapping parameterMapping = new ParameterMapping();

		public Builder(String property) {
			parameterMapping.property = property;
		}

		public Builder(String property, Class<?> javaType) {
			parameterMapping.property = property;
			parameterMapping.javaType = javaType;
		}

		public ParameterMapping build() {
			return parameterMapping;
		}

		public Builder cipher(String cipher) {
			parameterMapping.cipher = cipher;
			return this;
		}

		public Builder cipherKey(String cipherKey) {
			parameterMapping.cipherKey = cipherKey;
			return this;
		}

		public Builder cipherKeyAlg(String cipherKeyAlg) {
			parameterMapping.cipherKeyAlg = cipherKeyAlg;
			return this;
		}

		public Builder column(String column) {
			parameterMapping.column = column;
			return this;
		}

		public Builder digest(String digest) {
			parameterMapping.digest = digest;
			return this;
		}

		public Builder encoding(String encoding) {
			parameterMapping.encoding = encoding;
			return this;
		}

		public Builder index(int index) {
			parameterMapping.index = index;
			return this;
		}

		public Builder javaType(Class<?> javaType) {
			parameterMapping.javaType = javaType;
			return this;
		}

		public Builder jdbcType(JdbcType jdbcType) {
			parameterMapping.jdbcType = jdbcType;
			return this;
		}

		public Builder jdbcTypeName(String jdbcTypeName) {
			parameterMapping.jdbcTypeName = jdbcTypeName;
			parameterMapping.jdbcType = resolveJdbcType(jdbcTypeName);
			return this;
		}

		public Builder mode(String mode) {
			parameterMapping.mode = Mode.valueOf(mode);
			return this;
		}

		public Builder pattern(String pattern) {
			parameterMapping.pattern = pattern;
			return this;
		}

		public Builder primary(boolean primary) {
			parameterMapping.primary = primary;
			return this;
		}

		protected JdbcType resolveJdbcType(String alias) {
			if (alias == null)
				return null;
			try {
				return JdbcType.valueOf(alias.toUpperCase());
			} catch (IllegalArgumentException e) {
				return null;
			}
		}

		public Builder size(String sizeString) {
			parameterMapping.size = Integer.parseInt(sizeString);
			return this;
		}
	}

	public enum Mode {
		IN, OUT, NONE
	}

	private String cipher;

	private String cipherKey;

	private String cipherKeyAlg;

	private int size;

	private int index;

	private String property;

	private String column;

	private String encoding;

	private JdbcType jdbcType;

	private String jdbcTypeName;

	private String pattern;

	private String digest;

	private boolean primary;

	private Class<?> javaType = Object.class;

	private Mode mode = Mode.IN;

	public String getCipher() {
		return cipher;
	}

	public String getCipherKey() {
		return cipherKey;
	}

	public String getCipherKeyAlg() {
		return cipherKeyAlg;
	}

	public String getColumn() {
		return column;
	}

	public String getDigest() {
		return digest;
	}

	public String getEncoding() {
		return encoding;
	}

	public int getIndex() {
		return index;
	}

	public Class<?> getJavaType() {
		return javaType;
	}

	public JdbcType getJdbcType() {
		return jdbcType;
	}

	public String getJdbcTypeName() {
		return jdbcTypeName;
	}

	public Mode getMode() {
		return mode;
	}

	public String getPattern() {
		return pattern;
	}

	public String getProperty() {
		return property;
	}

	public int getSize() {
		return size;
	}

	public boolean isPrimary() {
		return primary;
	}

	public void setCipher(String cipher) {
		this.cipher = cipher;
	}

	public void setCipherKey(String cipherKey) {
		this.cipherKey = cipherKey;
	}

	public void setCipherKeyAlg(String cipherKeyAlg) {
		this.cipherKeyAlg = cipherKeyAlg;
	}

	public void setColumn(String column) {
		this.column = column;
	}

	public void setDigest(String digest) {
		this.digest = digest;
	}

	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	public void setJavaType(Class<?> javaType) {
		this.javaType = javaType;
	}

	public void setJdbcType(JdbcType jdbcType) {
		this.jdbcType = jdbcType;
	}

	public void setJdbcTypeName(String jdbcTypeName) {
		this.jdbcTypeName = jdbcTypeName;
	}

	public void setMode(Mode mode) {
		this.mode = mode;
	}

	public void setPattern(String pattern) {
		this.pattern = pattern;
	}

	public void setPrimary(boolean primary) {
		this.primary = primary;
	}

	public void setProperty(String property) {
		this.property = property;
	}

	public void setSize(int size) {
		this.size = size;
	}

	@Override
	public String toString() {
		return "ParameterMapping [cipher=" + cipher + ", cipherKey=" + cipherKey + ", cipherKeyAlg=" + cipherKeyAlg
				+ ", size=" + size + ", index=" + index + ", property=" + property + ", column=" + column
				+ ", encoding=" + encoding + ", jdbcType=" + jdbcType + ", jdbcTypeName=" + jdbcTypeName + ", pattern="
				+ pattern + ", digest=" + digest + ", primary=" + primary + ", javaType=" + javaType + ", mode=" + mode
				+ "]";
	}

}
