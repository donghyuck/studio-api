package studio.one.platform.data.freemarker;

import java.util.HashMap;
import java.util.Map;

import freemarker.ext.beans.BeansWrapper;
import freemarker.template.TemplateHashModel;
import freemarker.template.TemplateModelException;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class StaticModels {

    private static final Map<String, String> models = new HashMap<>();

    private StaticModels() {
    }

    public static Map<String, String> getStaticModels() {
        return models;
    }

    public static void populateStatics(BeansWrapper wrapper, Map<String, Object> model) {
        TemplateHashModel staticHashModels = wrapper.getStaticModels();
        try {
            for (Map.Entry<String, String> entry : models.entrySet()) {
                model.put(entry.getKey(), staticHashModels.get(entry.getValue()));
            }
        } catch (TemplateModelException e) {
            log.error(e.getMessage(), e);
        }
    }
}
