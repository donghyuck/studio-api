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
package studio.one.platform.data.sqlquery.mapping;

public class ResultMapping {

	/**
	 * @author donghyuck
	 */
	public static class Builder {

		private ResultMapping parameterMapping = new ResultMapping();

		public Builder(String property) {
			parameterMapping.property = property;
		}

		public Builder(String property, Class<?> javaType) {
			parameterMapping.property = property;
			parameterMapping.javaType = javaType;
		}

		public ResultMapping build() {
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

			return JdbcType.valueOf(alias.toUpperCase());
		}

		public Builder size(String sizeString) {
			parameterMapping.size = Integer.parseInt(sizeString);
			return this;
		}
	}

	private String cipher;

	private String cipherKey;

	private String cipherKeyAlg;

	private int size;

	private int index;

	private String property;

	private String encoding;

	private JdbcType jdbcType;

	private String jdbcTypeName;

	private String pattern;

	private boolean primary;

	private Class<?> javaType = Object.class;

	private ResultMapping() {
	}

	public String getCipher() {
		return cipher;
	}

	public String getCipherKey() {
		return cipherKey;
	}

	public String getCipherKeyAlg() {
		return cipherKeyAlg;
	}

	/**
	 * @return
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @return
	 */
	public int getIndex() {
		return index;
	}

	/**
	 * @return
	 */
	public Class<?> getJavaType() {
		return javaType;
	}

	/**
	 * @return
	 */
	public JdbcType getJdbcType() {
		return jdbcType;
	}

	/**
	 * @return
	 */
	public String getJdbcTypeName() {
		return jdbcTypeName;
	}

	/**
	 * @return
	 */
	public String getPattern() {
		return pattern;
	}

	/**
	 * @return
	 */
	public String getProperty() {
		return property;
	}

	public int getSize() {
		return size;
	}

	/**
	 * @return
	 */
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

	/**
	 * @param encoding
	 */
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}

	public void setSize(int size) {
		this.size = size;
	}
}
