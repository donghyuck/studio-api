package studio.one.application.template.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import freemarker.template.TemplateException;
import studio.one.application.template.domain.model.Template;
import studio.one.platform.constant.ServiceNames;
import studio.one.platform.exception.NotFoundException;

public interface TemplatesService {

	public static final String SERVICE_NAME = ServiceNames.Featrues.PREFIX + ":data:template-service";

	public Template getTemplatesByName(String name) throws NotFoundException;

	public Template getTemplates(long templateId) throws NotFoundException;

	public Template createGenericTemplates(int objectType, long objectId, String name, String displayName,
			String description, String subject, InputStream file) throws IOException;

	public void saveOrUpdate(Template template);

	public void remove(Template template) throws IOException;

	public Page<Template> page(Pageable pageable);

	public Page<Template> page(Pageable pageable, String query, String fields);

	public String processBody(Template template, Map<String, Object> model) throws IOException, TemplateException ;

	public String processSubject(Template template, Map<String, Object> model) throws IOException, TemplateException ;

}
