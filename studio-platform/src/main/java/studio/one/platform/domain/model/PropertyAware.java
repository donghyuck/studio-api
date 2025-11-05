package studio.one.platform.domain.model;

import java.util.Map;

/**
 * An interface for objects that have a map of properties.
 *
 * @author donghyuck, son
 * @since 2025-07-21
 * @version 1.0
 */
public interface PropertyAware {
    
	/**
	 * Returns the properties of the object.
	 *
	 * @return a map of properties
	 */
	Map<String, String> getProperties();

    /**
     * Sets the properties of the object.
     *
     * @param properties a map of properties
     */
    void setProperties(Map<String, String> properties);
    
}
