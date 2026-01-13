package studio.one.platform.data.sqlquery;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class SqlParams {

	private SqlParams() {
	}

	public static Map<String, Object> of(Object... keyValues) {
		return buildMap(keyValues);
	}

	public static Map<String, Object> additional(Object... keyValues) {
		return buildMap(keyValues);
	}

	public static Map<String, Object> empty() {
		return Collections.emptyMap();
	}

	private static Map<String, Object> buildMap(Object... keyValues) {
		if (keyValues == null || keyValues.length == 0) {
			return Collections.emptyMap();
		}
		if (keyValues.length % 2 != 0) {
			throw new IllegalArgumentException("Key/value pairs must be even.");
		}
		Map<String, Object> params = new LinkedHashMap<>();
		for (int i = 0; i < keyValues.length; i += 2) {
			Object key = keyValues[i];
			if (!(key instanceof String)) {
				throw new IllegalArgumentException("Key must be String: " + key);
			}
			params.put((String) key, keyValues[i + 1]);
		}
		return params;
	}
}
