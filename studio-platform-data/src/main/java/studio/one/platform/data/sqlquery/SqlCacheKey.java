package studio.one.platform.data.sqlquery;

import java.util.Objects;

public final class SqlCacheKey {

	private SqlCacheKey() {
	}

	public static String of(String statementId, Object... parts) {
		StringBuilder builder = new StringBuilder();
		builder.append(Objects.requireNonNull(statementId, "statementId"));
		if (parts != null) {
			for (Object part : parts) {
				builder.append(':').append(String.valueOf(part));
			}
		}
		return builder.toString();
	}
}
