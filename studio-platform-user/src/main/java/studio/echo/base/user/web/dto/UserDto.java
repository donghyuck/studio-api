package studio.echo.base.user.web.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Value;
import studio.echo.base.user.domain.model.Status;

@Value
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserDto {

	Long userId;
	String username;
	String name;
	String email;
  	boolean emailVisible;
	boolean nameVisible;
	boolean enabled;
	String lastName;
	String firstName;
	Status statu;
	OffsetDateTime creationDate;
	OffsetDateTime modifiedDate;
	Map<String, String> properties;
	
}
