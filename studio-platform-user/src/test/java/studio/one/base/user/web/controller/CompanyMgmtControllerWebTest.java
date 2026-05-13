package studio.one.base.user.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import studio.one.base.user.domain.model.company.CompanyStatus;
import studio.one.base.user.domain.model.company.CompanyPermissionActions;
import studio.one.base.user.domain.model.ApplicationCompany;
import studio.one.base.user.application.usecase.ApplicationCompanyMemberService;
import studio.one.base.user.application.usecase.ApplicationCompanyPermissionService;
import studio.one.base.user.application.usecase.ApplicationCompanyService;
import studio.one.platform.identity.IdentityService;
import studio.one.platform.identity.UserRef;

class CompanyMgmtControllerWebTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listBindsEmptyQueryPagingSortAndSerializesProperties() throws Exception {
        ApplicationCompanyService companyService = mock(ApplicationCompanyService.class);
        ApplicationCompany company = company(10L, "acme", "Acme", Map.of("tier", "enterprise"));
        when(companyService.search(eq(""), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(company), pageable, 1);
                });
        MockMvc mvc = mockMvc(companyService);

        mvc.perform(get("/api/mgmt/companies")
                        .param("q", "")
                        .param("page", "0")
                        .param("size", "15")
                        .param("sort", "displayName,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].companyId").value(10))
                .andExpect(jsonPath("$.data.content[0].properties.tier").value("enterprise"))
                .andExpect(jsonPath("$.data.size").value(15))
                .andExpect(jsonPath("$.data.totalElements").value(1));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(companyService).search(eq(""), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getPageSize()).isEqualTo(15);
        org.assertj.core.api.Assertions.assertThat(pageable.getValue().getSort())
                .containsExactly(Sort.Order.asc("displayName"));
    }

    @Test
    void listUsesDefaultCompanyIdDescSortWhenSortIsOmitted() throws Exception {
        ApplicationCompanyService companyService = mock(ApplicationCompanyService.class);
        ApplicationCompany company = company(20L, "beta", "Beta", Map.of());
        when(companyService.search(eq(null), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable pageable = invocation.getArgument(1);
                    return new PageImpl<>(List.of(company), pageable, 1);
                });
        MockMvc mvc = mockMvc(companyService);

        mvc.perform(get("/api/mgmt/companies")
                        .param("page", "0")
                        .param("size", "15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].companyId").value(20));

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(companyService).search(eq(null), pageable.capture());
        org.assertj.core.api.Assertions.assertThat(pageable.getValue())
                .isEqualTo(PageRequest.of(0, 15, Sort.by(Sort.Order.desc("companyId"))));
    }

    @Test
    void updatePermissionPolicyRejectsMissingActions() throws Exception {
        MockMvc mvc = mockMvc(mock(ApplicationCompanyService.class));

        mvc.perform(put("/api/mgmt/companies/{companyId}/permissions/policy", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"role\":\"ADMIN\",\"override\":true}]}\\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePermissionPolicyRejectsBlankActions() throws Exception {
        MockMvc mvc = mockMvc(mock(ApplicationCompanyService.class));

        mvc.perform(put("/api/mgmt/companies/{companyId}/permissions/policy", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"role\":\"ADMIN\",\"actions\":[\" \"],\"override\":true}]}\\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePermissionPolicyRejectsMissingOverride() throws Exception {
        MockMvc mvc = mockMvc(mock(ApplicationCompanyService.class));

        mvc.perform(put("/api/mgmt/companies/{companyId}/permissions/policy", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"role\":\"ADMIN\",\"actions\":[\"company.read\"]}]}\\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updatePermissionPolicyMapsServiceValidationToBadRequest() throws Exception {
        ApplicationCompanyPermissionService permissionService = mock(ApplicationCompanyPermissionService.class);
        when(permissionService.updatePolicy(eq(10L), any(), eq(99L), eq(false)))
                .thenThrow(new IllegalArgumentException("duplicate role: ADMIN"));
        MockMvc mvc = mockMvc(mock(ApplicationCompanyService.class), permissionService, identityService());
        var principal = User.withUsername("actor").password("n/a").authorities("ROLE_USER").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_USER"))));

        mvc.perform(put("/api/mgmt/companies/{companyId}/permissions/policy", 10L)
                        .principal(() -> "actor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\\n" + "  {\"role\":\"ADMIN\",\"actions\":[\"company.read\"],\"override\":true},\\n" + "  {\"role\":\"ADMIN\",\"actions\":[\"company.read\"],\"override\":true}\\n" + "]}\\n"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void platformAdminUpdatePermissionPolicyUsesServiceValidationForInvalidCompanyId() throws Exception {
        ApplicationCompanyService companyService = mock(ApplicationCompanyService.class);
        ApplicationCompanyPermissionService permissionService = mock(ApplicationCompanyPermissionService.class);
        when(permissionService.updatePolicy(eq(0L), any(), eq(99L), eq(true)))
                .thenThrow(new IllegalArgumentException("companyId must be positive"));
        MockMvc mvc = mockMvc(companyService, permissionService, identityService());
        var principal = User.withUsername("actor").password("n/a").authorities("ROLE_ADMIN").build();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                principal,
                "n/a",
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));

        mvc.perform(put("/api/mgmt/companies/{companyId}/permissions/policy", 0L)
                        .principal(() -> "actor")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[{\"role\":\"ADMIN\",\"actions\":[\"company.read\"],\"override\":true}]}\\n"))
                .andExpect(status().isBadRequest());

        org.mockito.Mockito.verifyNoInteractions(companyService);
    }

    private MockMvc mockMvc(ApplicationCompanyService companyService) {
        return mockMvc(companyService, mock(ApplicationCompanyPermissionService.class), new EmptyObjectProvider<>());
    }

    private MockMvc mockMvc(
            ApplicationCompanyService companyService,
            ApplicationCompanyPermissionService permissionService,
            ObjectProvider<IdentityService> identityServiceProvider) {
        CompanyMgmtController controller = new CompanyMgmtController(
                companyService,
                mock(ApplicationCompanyMemberService.class),
                permissionService,
                identityServiceProvider,
                new EmptyObjectProvider<>());
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver(),
                        new TestPrincipalArgumentResolver())
                .setValidator(validator)
                .addPlaceholderValue("studio.features.user.web.base-path", "/api/mgmt")
                .build();
    }

    private ObjectProvider<IdentityService> identityService() {
        IdentityService identityService = mock(IdentityService.class);
        when(identityService.findByUsername("actor")).thenReturn(Optional.of(new UserRef(99L, "actor", Set.of())));
        return new SingletonObjectProvider<>(identityService);
    }

    private ApplicationCompany company(Long companyId, String name, String displayName, Map<String, String> properties) {
        ApplicationCompany company = new ApplicationCompany();
        company.setCompanyId(companyId);
        company.setName(name);
        company.setDisplayName(displayName);
        company.setStatus(CompanyStatus.ACTIVE);
        company.setProperties(properties);
        return company;
    }

    private static class EmptyObjectProvider<T> implements ObjectProvider<T> {
        @Override
        public T getObject(Object... args) {
            return null;
        }

        @Override
        public T getIfAvailable() {
            return null;
        }

        @Override
        public T getIfUnique() {
            return null;
        }

        @Override
        public T getObject() {
            return null;
        }
    }

    private static class SingletonObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        public SingletonObjectProvider(T value) {
            this.value = value;
        }

        public T value() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }
    
    }

    private static class TestPrincipalArgumentResolver implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return UserDetails.class.isAssignableFrom(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication == null ? null : authentication.getPrincipal();
        }
    }
}
