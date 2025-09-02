package studio.echo.platform.domain.model;

import java.util.Map;

public interface PropertyAware {
    
	public abstract Map<String, String> getProperties();

    public abstract void setProperties(Map<String, String> properties);
}
