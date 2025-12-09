package studio.one.application.template.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

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

	public String processBody(Template template, Map<String, Object> model) throws IOException, TemplateException ;

	public String processSubject(Template template, Map<String, Object> model) throws IOException, TemplateException ;

}
