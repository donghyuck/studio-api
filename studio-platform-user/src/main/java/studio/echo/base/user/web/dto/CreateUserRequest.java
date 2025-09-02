package studio.echo.base.user.web.dto;

import java.util.Date;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateUserRequest {

    @NotBlank
    @Size( max = 50 )
    private String username;

    @NotBlank
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Size( min = 8 , max = 100)
    private String password;

    @NotBlank
    @Size(max = 100)
    private String name;

    @Email @NotBlank
    @Size(max=255)
    private String email;
 
    private Date creationDate;
 
    private Date modifiedDate;

}
