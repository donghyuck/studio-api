package studio.one.base.user.web.dto;

import java.util.Map;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import studio.one.base.user.domain.model.Status;
import studio.one.base.user.domain.model.json.JsonStatusDeserializer;
import studio.one.base.user.domain.model.json.JsonStatusSerializer;

@Getter
@Setter
@ToString
public class UpdateUserRequest {

    @NotBlank
    @Size(max = 100)
    private String name;

    @Email
    @NotBlank
    @Size(max = 255)
    private String email;

  	boolean emailVisible;
	boolean nameVisible;
	boolean enabled;
	String lastName;
	String firstName;

    @JsonSerialize(using = JsonStatusSerializer.class)
    @JsonDeserialize(using = JsonStatusDeserializer.class)  
	Status status;

    private Map<String, String> properties;

}
