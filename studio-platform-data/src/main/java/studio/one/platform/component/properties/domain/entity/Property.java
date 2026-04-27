package studio.one.platform.component.properties.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

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
