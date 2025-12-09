package studio.one.application.template.service.impl;

import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Map;

import javax.servlet.GenericServlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfig;

import freemarker.ext.beans.BeansWrapper;
import freemarker.ext.beans.BeansWrapperBuilder;
import freemarker.ext.servlet.AllHttpScopesHashModel;
import freemarker.ext.servlet.FreemarkerServlet;
import freemarker.ext.servlet.HttpRequestHashModel;
import freemarker.ext.servlet.HttpRequestParametersHashModel;
import freemarker.ext.servlet.HttpSessionHashModel;
import freemarker.ext.servlet.ServletContextHashModel;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapperBuilder;
import freemarker.template.ObjectWrapper;
import freemarker.template.SimpleHash;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateHashModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import studio.one.platform.data.sqlquery.factory.impl.StaticModels;

@Slf4j
@RequiredArgsConstructor
public class FreemarkerTemplateBuilder {

    private final ServletContext servletContext;

    private final FreeMarkerConfig freeMarkerConfig;

    private Configuration configuration = new Configuration(Configuration.VERSION_2_3_32);

    private static BeansWrapper wrapper = new BeansWrapperBuilder(Configuration.VERSION_2_3_32).build();

    protected void populateStatics(Map<String, Object> model) {
        try {
            TemplateHashModel enumModels = wrapper.getEnumModels();
            model.put("enums", enumModels);
        } catch (UnsupportedOperationException e) {
        }
        StaticModels.populateStatics(wrapper, model);
    }

    public Configuration getConfiguration() {
        if (freeMarkerConfig != null)
            return freeMarkerConfig.getConfiguration();
        return configuration;
    }

    public void processTemplate(Template template, Map<String, Object> model, Writer writer)
            throws TemplateException, IOException {
        // FreemarkerHelper.populateStatics(wrapper, model);
        SimpleHash root = new SimpleHash(wrapper);
        root.putAll(model);
        template.setNumberFormat("computer");
        template.process(root, writer);
    }

    protected ObjectWrapper getObjectWrapper(Configuration configuration) {
        ObjectWrapper ow = configuration.getObjectWrapper();
        return (ow != null ? ow
                : new DefaultObjectWrapperBuilder(Configuration.DEFAULT_INCOMPATIBLE_IMPROVEMENTS).build());
    }

    protected void processTemplate(Template template, Map<String, Object> model, HttpServletRequest request,
            HttpServletResponse response) throws TemplateException, IOException {
        ObjectWrapper wrapper = getObjectWrapper(getConfiguration());
        // FreemarkerHelper.populateStatics((BeansWrapper) wrapper, model);
        SimpleHash fmModel = buildTemplateModel(model, wrapper, request, response);
        template.process(fmModel, response.getWriter());
    }

    protected SimpleHash buildTemplateModel(Map<String, Object> model, ObjectWrapper wrapper,
            HttpServletRequest request, HttpServletResponse response) {
        AllHttpScopesHashModel fmModel = new AllHttpScopesHashModel(wrapper, servletContext, request);
        fmModel.put(FreemarkerServlet.KEY_APPLICATION, buildServletContextHashModel(wrapper, null));
        fmModel.put(FreemarkerServlet.KEY_SESSION, buildSessionModel(wrapper, request, response));
        fmModel.put(FreemarkerServlet.KEY_REQUEST, new HttpRequestHashModel(request, response, wrapper));
        fmModel.put(FreemarkerServlet.KEY_REQUEST_PARAMETERS, new HttpRequestParametersHashModel(request));
        fmModel.putAll(model);
        return fmModel;
    }

    protected void exposeModelAsRequestAttributes(Map<String, Object> model, HttpServletRequest request) {
        for (Map.Entry<String, Object> entry : model.entrySet()) {
            String modelName = entry.getKey();
            Object modelValue = entry.getValue();
            if (modelValue != null) {
                request.setAttribute(modelName, modelValue);
            } else {
                request.removeAttribute(modelName);
            }
        }
    }

    protected ServletContextHashModel buildServletContextHashModel(ObjectWrapper wrapper,
            GenericServlet genericServlet) {
        GenericServlet servlet = genericServlet;
        if (servlet == null) {
            servlet = new GenericServletAdapter();
            try {
                servlet.init(new DelegatingServletConfig());
            } catch (ServletException ex) {
                throw new BeanInitializationException("Initialization of GenericServlet adapter failed", ex);
            }
        }
        return new ServletContextHashModel(servlet, wrapper);
    }

    protected HttpSessionHashModel buildSessionModel(ObjectWrapper wrapper, HttpServletRequest request,
            HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            return new HttpSessionHashModel(session, wrapper);
        } else {
            return new HttpSessionHashModel(null, request, response, wrapper);
        }
    }

    @SuppressWarnings("serial")
    private static class GenericServletAdapter extends GenericServlet {
        @Override
        public void service(ServletRequest servletRequest, ServletResponse servletResponse) {
            // no-op
        }
    }

    /**
     * Internal implementation of the {@link ServletConfig} interface,
     * to be passed to the servlet adapter.
     */
    private class DelegatingServletConfig implements ServletConfig {
        @Override
        public String getServletName() {
            return "FreemarkerTemplateBuilder";
        }

        @Override
        public ServletContext getServletContext() {
            return FreemarkerTemplateBuilder.this.servletContext;
        }

        @Override
        public String getInitParameter(String paramName) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return Collections.enumeration(new HashSet<String>());
        }
    }
}
