/**
 *    Copyright 2009-2016 the original author or authors.
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
package studio.one.platform.data.sqlquery.parser;
/**
 * GenericTokenParser is a general purpose parser for parsing tokens of the form #{propName}.
 * The open token is "#{", the close token is "}". The content between the open token and the close token is treated as a property name and passed to the TokenHandler.handleToken() method.
 * 
 * This class is very useful for the implementation of type handlers that require property substitution functionality.
 * 
 * @author Clinton Begin
 * @change donghyuck.son refactoring 2025. 3. 21.
 */
public class GenericTokenParser {

	private final String openToken;
	private final String closeToken;
	private final TokenHandler handler;

	public GenericTokenParser(String openToken, String closeToken, TokenHandler handler) {
		this.openToken = openToken;
		this.closeToken = closeToken;
		this.handler = handler;
	}

	public String parse(String text) {
		if (text == null || text.isEmpty()) {
			return "";
		}

		char[] src = text.toCharArray();
		StringBuilder builder = new StringBuilder();
		StringBuilder expression = new StringBuilder();

		int offset = 0;
		int start = text.indexOf(openToken, offset);

		while (start > -1) {
			if (isEscapedToken(src, start)) {
				builder.append(src, offset, start - offset - 1).append(openToken);
				offset = start + openToken.length();
			} else {
				builder.append(src, offset, start - offset);
				offset = start + openToken.length();
				int end = findCloseToken(src, offset);
				if (end == -1) {
					// No closing token found
					builder.append(src, start, src.length - start);
					offset = src.length;
				} else {
					expression.setLength(0);
					expression.append(src, offset, end - offset);
					builder.append(handler.handleToken(expression.toString()));
					offset = end + closeToken.length();
				}
			}
			start = text.indexOf(openToken, offset);
		}

		if (offset < src.length) {
			builder.append(src, offset, src.length - offset);
		}

		return builder.toString();
	}

	private boolean isEscapedToken(char[] src, int position) {
		return position > 0 && src[position - 1] == '\\';
	}

	private int findCloseToken(char[] src, int offset) {
		int start = offset;
		while (true) {
			int end = indexOf(src, closeToken, start);
			if (end == -1) return -1;
			if (isEscapedToken(src, end)) {
				start = end + closeToken.length();
			} else {
				return end;
			}
		}
	}

	private int indexOf(char[] src, String target, int fromIndex) {
		String srcString = new String(src);
		return srcString.indexOf(target, fromIndex);
	}
}
