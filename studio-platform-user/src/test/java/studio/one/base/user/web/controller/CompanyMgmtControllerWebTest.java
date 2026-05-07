package studio.one.base.user.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import studio.one.base.user.company.model.CompanyStatus;
import studio.one.base.user.domain.entity.ApplicationCompany;
import studio.one.base.user.service.ApplicationCompanyMemberService;
import studio.one.base.user.service.ApplicationCompanyPermissionService;
import studio.one.base.user.service.ApplicationCompanyService;

class CompanyMgmtControllerWebTest {

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

    private MockMvc mockMvc(ApplicationCompanyService companyService) {
        CompanyMgmtController controller = new CompanyMgmtController(
                companyService,
                mock(ApplicationCompanyMemberService.class),
                mock(ApplicationCompanyPermissionService.class),
                new EmptyObjectProvider<>(),
                new EmptyObjectProvider<>());
        return MockMvcBuilders.standaloneSetup(controller)
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .addPlaceholderValue("studio.features.user.web.base-path", "/api/mgmt")
                .build();
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
}
