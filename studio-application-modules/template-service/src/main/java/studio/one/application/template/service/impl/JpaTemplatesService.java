package studio.one.application.template.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import freemarker.template.Configuration;
import freemarker.template.TemplateException;
import studio.one.application.template.persistence.repository.TemplateRepository;
import studio.one.application.template.service.TemplatesService;
import studio.one.platform.exception.NotFoundException;
import studio.one.application.template.domain.entity.TemplateEntity;
import studio.one.application.template.domain.model.Template;

@Service(TemplatesService.SERVICE_NAME)
@Transactional
public class JpaTemplatesService implements TemplatesService {

    private final TemplateRepository templateRepository;
    private final Configuration configuration;

    public JpaTemplatesService(TemplateRepository templateRepository,
            ObjectProvider<Configuration> configurationProvider) {
        this.templateRepository = templateRepository;
        this.configuration = Optional.ofNullable(configurationProvider.getIfAvailable())
                .orElseGet(() -> new Configuration(Configuration.VERSION_2_3_32));
    }

    @Transactional(readOnly = true)
    @Override
    public Template getTemplatesByName(String name) throws NotFoundException {
        return templateRepository.findByName(name)
                .orElseThrow(() -> NotFoundException.of("template", name));
    }

    @Transactional(readOnly = true)
    @Override
    public Template getTemplates(long templateId) throws NotFoundException {
        return templateRepository.findById(templateId)
                .orElseThrow(() -> NotFoundException.of("template", templateId));
    }

    @Override
    public Template createGenericTemplates(int objectType, long objectId, String name, String displayName,
            String description, String subject, InputStream file) throws IOException {
        TemplateEntity entity = new TemplateEntity();
        entity.setObjectType(objectType);
        entity.setObjectId(objectId);
        entity.setName(name);
        entity.setDisplayName(displayName);
        entity.setDescription(description);
        entity.setSubject(subject);
        entity.setBody(readBody(file));
        Instant now = Instant.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setCreatedBy(0L);
        entity.setUpdatedBy(0L);
        return templateRepository.save(entity);
    }

    @Override
    public void saveOrUpdate(Template template) {
        TemplateEntity entity = toEntity(template);
        Instant now = Instant.now();
        if (entity.getCreatedAt() == null) {
            entity.setCreatedAt(now);
        }
        entity.setUpdatedAt(now);
        templateRepository.save(entity);
    }

    @Override
    public void remove(Template template) throws IOException {
        templateRepository.delete(toEntity(template));
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

    private TemplateEntity toEntity(Template template) {
        if (template instanceof TemplateEntity entity) {
            return entity;
        }
        TemplateEntity entity = new TemplateEntity();
        entity.setTemplateId(template.getTemplateId());
        entity.setObjectType(template.getObjectType());
        entity.setObjectId(template.getObjectId());
        entity.setName(template.getName());
        entity.setDisplayName(template.getDisplayName());
        entity.setDescription(template.getDescription());
        entity.setSubject(template.getSubject());
        entity.setBody(template.getBody());
        entity.setCreatedBy(template.getCreatedBy());
        entity.setUpdatedBy(template.getUpdatedBy());
        entity.setCreatedAt(template.getCreatedAt());
        entity.setUpdatedAt(template.getUpdatedAt());
        entity.setProperties(template.getProperties());
        return entity;
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
