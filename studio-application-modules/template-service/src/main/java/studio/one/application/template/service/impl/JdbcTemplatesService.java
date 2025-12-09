package studio.one.application.template.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import studio.one.application.template.domain.model.DefaultTemplate;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.service.TemplatesService;
import studio.one.platform.data.sqlquery.annotation.SqlStatement;
import studio.one.platform.exception.NotFoundException;

@Service(TemplatesService.SERVICE_NAME)
@Transactional
public class JdbcTemplatesService implements TemplatesService {

    @SqlStatement("data.template.insert")
    private String insertSql;

    @SqlStatement("data.template.update")
    private String updateSql;

    @SqlStatement("data.template.findById")
    private String findByIdSql;

    @SqlStatement("data.template.findByName")
    private String findByNameSql;

    @SqlStatement("data.template.delete")
    private String deleteSql;

    @SqlStatement("data.template.deleteProperties")
    private String deletePropertiesSql;

    @SqlStatement("data.template.insertProperty")
    private String insertPropertySql;

    @SqlStatement("data.template.findProperties")
    private String findPropertiesSql;

    private static final RowMapper<Template> ROW_MAPPER = (rs, rowNum) -> {
        DefaultTemplate template = new DefaultTemplate();
        template.setTemplateId(rs.getLong("TEMPLATE_ID"));
        template.setObjectType(rs.getInt("OBJECT_TYPE"));
        template.setObjectId(rs.getLong("OBJECT_ID"));
        template.setName(rs.getString("NAME"));
        template.setDisplayName(rs.getString("DISPLAY_NAME"));
        template.setDescription(rs.getString("DESCRIPTION"));
        template.setSubject(rs.getString("SUBJECT"));
        template.setBody(rs.getString("BODY"));
        Timestamp createdAt = rs.getTimestamp("CREATED_AT");
        Timestamp updatedAt = rs.getTimestamp("UPDATED_AT");
        template.setCreatedAt(createdAt != null ? createdAt.toInstant() : null);
        template.setUpdatedAt(updatedAt != null ? updatedAt.toInstant() : null);
        template.setCreatedBy(rs.getLong("CREATED_BY"));
        template.setUpdatedBy(rs.getLong("UPDATED_BY"));
        return template;
    };

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final Configuration configuration;

    public JdbcTemplatesService(NamedParameterJdbcTemplate jdbcTemplate,
            Optional<Configuration> configuration) {
        this.jdbcTemplate = jdbcTemplate;
        this.configuration = configuration.orElseGet(() -> new Configuration(Configuration.VERSION_2_3_32));
    }

    @Transactional(readOnly = true)
    @Override
    public Template getTemplatesByName(String name) throws NotFoundException {
        Template template = jdbcTemplate.query(findByNameSql, Map.of("name", name), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("template", name));
        template.setProperties(loadProperties(template.getTemplateId()));
        return template;
    }

    @Transactional(readOnly = true)
    @Override
    public Template getTemplates(long templateId) throws NotFoundException {
        Template template = jdbcTemplate.query(findByIdSql, Map.of("templateId", templateId), ROW_MAPPER)
                .stream()
                .findFirst()
                .orElseThrow(() -> NotFoundException.of("template", templateId));
        template.setProperties(loadProperties(templateId));
        return template;
    }

    @Override
    public Template createGenericTemplates(int objectType, long objectId, String name, String displayName,
            String description, String subject, InputStream file) throws IOException {
        DefaultTemplate template = new DefaultTemplate();
        template.setObjectType(objectType);
        template.setObjectId(objectId);
        template.setName(name);
        template.setDisplayName(displayName);
        template.setDescription(description);
        template.setSubject(subject);
        template.setBody(readBody(file));
        template.setCreatedAt(Instant.now());
        template.setUpdatedAt(template.getCreatedAt());
        template.setCreatedBy(0L);
        template.setUpdatedBy(0L);
        return saveInternal(template);
    }

    @Override
    public void saveOrUpdate(Template template) {
        saveInternal(template);
    }

    @Override
    public void remove(Template template) throws IOException {
        Map<String, Object> params = Map.of("templateId", template.getTemplateId());
        jdbcTemplate.update(deletePropertiesSql, params);
        jdbcTemplate.update(deleteSql, params);
    }

    @Override
    public String processBody(Template template, Map<String, Object> model) throws IOException, TemplateException {
        if (template == null || template.getBody() == null) {
            return null;
        }
        return processTemplate("body-" + template.getName(), template.getBody(), model);
    }

    @Override
    public String processSubject(Template template, Map<String, Object> model) throws IOException, TemplateException {
        if (template == null || template.getSubject() == null) {
            return null;
        }
        return processTemplate("subject-" + template.getName(), template.getSubject(), model);
    }

    private Template saveInternal(Template template) {
        if (template.getTemplateId() <= 0) {
            return insert(template);
        }
        return update(template);
    }

    private Template insert(Template template) {
        Instant now = template.getCreatedAt() == null ? Instant.now() : template.getCreatedAt();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("objectType", template.getObjectType())
                .addValue("objectId", template.getObjectId())
                .addValue("name", template.getName())
                .addValue("displayName", template.getDisplayName())
                .addValue("description", template.getDescription())
                .addValue("subject", template.getSubject())
                .addValue("body", template.getBody())
                .addValue("createdBy", template.getCreatedBy())
                .addValue("updatedBy", template.getUpdatedBy())
                .addValue("createdAt", Timestamp.from(now))
                .addValue("updatedAt", Timestamp.from(template.getUpdatedAt() == null ? now : template.getUpdatedAt()));
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(insertSql, params, keyHolder, new String[] { "TEMPLATE_ID" });
        Number key = keyHolder.getKey();
        if (key != null) {
            template.setTemplateId(key.longValue());
        }
        saveProperties(template.getTemplateId(), template.getProperties());
        return template;
    }

    private Template update(Template template) {
        Instant now = Instant.now();
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("templateId", template.getTemplateId())
                .addValue("objectType", template.getObjectType())
                .addValue("objectId", template.getObjectId())
                .addValue("name", template.getName())
                .addValue("displayName", template.getDisplayName())
                .addValue("description", template.getDescription())
                .addValue("subject", template.getSubject())
                .addValue("body", template.getBody())
                .addValue("updatedBy", template.getUpdatedBy())
                .addValue("updatedAt", Timestamp.from(now));
        jdbcTemplate.update(updateSql, params);
        saveProperties(template.getTemplateId(), template.getProperties());
        return template;
    }

    private void saveProperties(long templateId, Map<String, String> properties) {
        jdbcTemplate.update(deletePropertiesSql, Map.of("templateId", templateId));
        if (properties == null || properties.isEmpty()) {
            return;
        }
        MapSqlParameterSource[] batch = properties.entrySet().stream()
                .map(entry -> new MapSqlParameterSource()
                        .addValue("templateId", templateId)
                        .addValue("name", entry.getKey())
                        .addValue("value", entry.getValue()))
                .toArray(MapSqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(insertPropertySql, batch);
    }

    private Map<String, String> loadProperties(long templateId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(findPropertiesSql, Map.of("templateId", templateId));
        Map<String, String> props = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object name = row.get("PROPERTY_NAME");
            Object value = row.get("PROPERTY_VALUE");
            if (name != null) {
                props.put(name.toString(), value != null ? value.toString() : null);
            }
        }
        return props;
    }

    private String processTemplate(String templateName, String templateSource, Map<String, Object> model)
            throws IOException, TemplateException {
        freemarker.template.Template fmTemplate = new freemarker.template.Template(templateName, templateSource,
                configuration);
        StringWriter writer = new StringWriter();
        fmTemplate.process(model, writer);
        return writer.toString();
    }

    private String readBody(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        try (InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            return builder.toString();
        }
    }
}
