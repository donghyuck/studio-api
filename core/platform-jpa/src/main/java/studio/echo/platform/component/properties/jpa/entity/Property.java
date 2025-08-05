package studio.echo.platform.component.properties.jpa.entity;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
@Table(name = "TB_APPLICATION_PROPERTY") 
public class Property {
    
    @Id 
    @Column(name = "PROPERTY_NAME", nullable = false)
    private String key;

    @Column(name = "PROPERTY_VALUE")
    private String value;
    
}
