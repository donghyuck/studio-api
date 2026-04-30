package studio.one.platform.ai.service.visualization;

import java.util.Locale;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

final class JdbcVectorProjectionSql {

    private JdbcVectorProjectionSql() {
    }

    static boolean isPostgres(NamedParameterJdbcTemplate template) {
        try {
            return Boolean.TRUE.equals(template.getJdbcOperations().execute((ConnectionCallback<Boolean>) connection -> {
                String productName = connection.getMetaData().getDatabaseProductName();
                return productName != null && productName.toLowerCase(Locale.ROOT).contains("postgres");
            }));
        } catch (RuntimeException ex) {
            return true;
        }
    }

    static String jsonText(String alias, String keyExpression, boolean postgres) {
        String column = alias == null || alias.isBlank() ? "metadata" : alias + ".metadata";
        if (postgres) {
            if (keyExpression.startsWith(":")) {
                return column + " ->> " + keyExpression;
            }
            return column + " ->> '" + keyExpression.replace("'", "''") + "'";
        }
        String path = keyExpression.startsWith(":")
                ? "CONCAT('$.', " + keyExpression + ")"
                : "'$." + keyExpression + "'";
        return "JSON_UNQUOTE(JSON_EXTRACT(" + column + ", " + path + "))";
    }

    static String rowVectorItemId(String idExpression, boolean postgres) {
        if (postgres) {
            return "'row-' || " + idExpression;
        }
        return "CONCAT('row-', " + idExpression + ")";
    }

    static String orderByDisplayOrder(boolean postgres) {
        if (postgres) {
            return "p.display_order NULLS LAST, p.vector_item_id";
        }
        return "p.display_order IS NULL, p.display_order, p.vector_item_id";
    }

    static String orderByDisplayOrderClause(boolean postgres) {
        return " ORDER BY " + orderByDisplayOrder(postgres);
    }
}
