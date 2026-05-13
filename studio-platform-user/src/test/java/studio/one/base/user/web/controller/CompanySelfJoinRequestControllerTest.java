package studio.one.base.user.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import studio.one.base.user.domain.model.company.CompanyJoinRequestRef;
import studio.one.base.user.domain.model.company.CompanyJoinRequestStatus;
import studio.one.base.user.domain.model.company.CompanyRole;
import studio.one.base.user.application.usecase.ApplicationCompanyJoinRequestService;
import studio.one.base.user.web.dto.request.CompanySelfJoinRequestCreateRequest;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

class CompanySelfJoinRequestControllerTest {

    private static final Long USER_ID = 7L;

    @Test
    void selfJoinRequestResolvesAuthenticatedUser() {
        ApplicationCompanyJoinRequestService service = mock(ApplicationCompanyJoinRequestService.class);
        when(service.createSelfRequest("cmk_test", USER_ID, "actor", null, "join"))
                .thenReturn(joinRequest(USER_ID));
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(USER_ID, "actor", Set.of())));
        CompanySelfJoinRequestController controller = new CompanySelfJoinRequestController(service, identityService);

        var principal = User.withUsername("actor").password("n/a").authorities("ROLE_USER").build();
        var response = controller.createSelfJoinRequest(new CompanySelfJoinRequestCreateRequest("cmk_test", "join"), principal);

        verify(service).createSelfRequest("cmk_test", USER_ID, "actor", null, "join");
        assertThat(response.getStatusCode().value()).isEqualTo(201);
        assertThat(response.getBody().getData().userId()).isEqualTo(USER_ID);
    }

    @Test
    void selfJoinRequestRequiresAuthenticatedPrincipalByContract() throws Exception {
        Method method = CompanySelfJoinRequestController.class.getMethod(
                "createSelfJoinRequest",
                CompanySelfJoinRequestCreateRequest.class,
                org.springframework.security.core.userdetails.UserDetails.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("isAuthenticated()");
    }

    @Test
    void selfJoinRequestValidationRejectsBlankKey() throws Exception {
        ApplicationCompanyJoinRequestService service = mock(ApplicationCompanyJoinRequestService.class);
        IdentityService identityService = mock(IdentityService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanySelfJoinRequestController(service, identityService))
                .setValidator(validator())
                .build();

        mvc.perform(post("/api/self/company-join-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberKey\":\"\",\"message\":\"hello\"}\\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void selfJoinRequestValidationRejectsOversizedKeyAndMessage() throws Exception {
        ApplicationCompanyJoinRequestService service = mock(ApplicationCompanyJoinRequestService.class);
        IdentityService identityService = mock(IdentityService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new CompanySelfJoinRequestController(service, identityService))
                .setValidator(validator())
                .build();

        mvc.perform(post("/api/self/company-join-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(
                                "{\"memberKey\":\"%s\",\"message\":\"%s\"}\n",
                                "k".repeat(129), "m".repeat(1001))))
                .andExpect(status().isBadRequest());
    }

    private CompanyJoinRequestRef joinRequest(Long userId) {
        return new CompanyJoinRequestRef(
                100L,
                10L,
                1L,
                userId,
                "actor",
                null,
                null,
                CompanyRole.MEMBER,
                CompanyJoinRequestStatus.PENDING,
                null,
                userId,
                null,
                null);
    }

    private LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return validator;
    }
}
