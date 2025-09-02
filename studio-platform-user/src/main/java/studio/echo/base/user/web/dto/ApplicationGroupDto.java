package studio.echo.base.user.web.dto;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Map;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApplicationGroupDto {

    Long groupId;

    @NotBlank
    @Size(max = 255)
    String name;

    @Size(max = 1000)
    String description;

    @Builder.Default
    Map<String, String> properties = Collections.emptyMap();

   OffsetDateTime creationDate;
   OffsetDateTime modifiedDate;
  
    int roleCount = 0;  
    int memberCount = 0; 
}
