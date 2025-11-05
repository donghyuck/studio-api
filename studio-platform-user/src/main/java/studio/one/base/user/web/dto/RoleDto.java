package studio.one.base.user.web.dto;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoleDto {

	private Long roleId;
 
	private String name;
 
	private String description; 

	private OffsetDateTime creationDate;
 
	private OffsetDateTime modifiedDate; 

}
