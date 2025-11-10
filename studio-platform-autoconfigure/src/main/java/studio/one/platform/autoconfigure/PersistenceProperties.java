package studio.one.platform.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

import lombok.Getter;
import lombok.Setter;
import studio.one.platform.constant.PropertyKeys;
@Getter
@Setter
@ConstructorBinding
@ConfigurationProperties(prefix = PropertyKeys.Persistence.PREFIX )
public class PersistenceProperties {

    private final Type type;

    public enum Type { jpa, mybatis, jdbc } 
    
    public PersistenceProperties(@DefaultValue("jpa") Type type) {
        this.type = type;
    }

    public static PersistenceProperties of (){
        return new PersistenceProperties(Type.jpa); 
    }
}
