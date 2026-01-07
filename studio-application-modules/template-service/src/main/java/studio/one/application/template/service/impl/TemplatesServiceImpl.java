package studio.one.application.template.service.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import freemarker.template.TemplateException;
import studio.one.application.template.domain.model.DefaultTemplate;
import studio.one.application.template.domain.model.Template;
import studio.one.application.template.persistence.TemplatePersistenceRepository;
import studio.one.application.template.service.TemplatesService;
import studio.one.platform.exception.NotFoundException;

@Transactional
public class TemplatesServiceImpl implements TemplatesService {

    private final TemplatePersistenceRepository templateRepository;
    private final FreemarkerTemplateBuilder templateBuilder;

    public TemplatesServiceImpl(TemplatePersistenceRepository templateRepository,
            FreemarkerTemplateBuilder templateBuilder) {
        this.templateRepository = templateRepository;
        this.templateBuilder = templateBuilder;
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
        return templateRepository.save(template);
    }

    @Override
    public void saveOrUpdate(Template template) {
        templateRepository.save(template);
    }

    @Override
    public void remove(Template template) throws IOException {
        templateRepository.deleteById(template.getTemplateId());
    }

    @Override
    public Page<Template> page(Pageable pageable) {
        return templateRepository.page(pageable);
    }

    @Override
    public Page<Template> page(Pageable pageable, String query, String fields) {
        return templateRepository.page(pageable, query, fields);
    }

    @Override
    @Transactional(readOnly = true)
    public String processBody(Template template, Map<String, Object> model) throws IOException, TemplateException {
        if (template == null || template.getBody() == null) {
            return null;
        }
        return processTemplate("body-" + template.getName(), template.getBody(), model);
    }

    @Override
    @Transactional(readOnly = true)
    public String processSubject(Template template, Map<String, Object> model) throws IOException, TemplateException {
        if (template == null || template.getSubject() == null) {
            return null;
        }
        return processTemplate("subject-" + template.getName(), template.getSubject(), model);
    }

    private String processTemplate(String templateName, String templateSource, Map<String, Object> model)
            throws IOException, TemplateException {
        freemarker.template.Template fmTemplate = new freemarker.template.Template(templateName, templateSource,
                templateBuilder.getConfiguration());
        java.io.StringWriter writer = new java.io.StringWriter();
        templateBuilder.processTemplate(fmTemplate, model, writer);
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
