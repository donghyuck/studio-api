package studio.one.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;

@ConfigurationProperties(prefix = PropertyKeys.Persistence.PREFIX)
@Getter
@Setter
public class PersistenceProperties {

    private final Type type = Type.jpa;

    public enum Type { jpa, mybatis, jdbc } 
    
    public static PersistenceProperties of (){
        return new PersistenceProperties(); 
    }
}
