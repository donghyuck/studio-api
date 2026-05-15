package studio.one.base.security.web.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import studio.one.base.security.audit.application.command.LoginFailQuery;
import studio.one.base.security.audit.application.usecase.LoginFailureQueryService;
import studio.one.base.security.web.dto.response.LoginFailureLogDto;
import studio.one.base.security.web.mapper.LoginFailureLogMapper;

class LoginFailureLogControllerTest {

    @Test
    void rejectsInvalidIpEqualsBeforeRepositoryExecution() {
        LoginFailureQueryService service = mock(LoginFailureQueryService.class);
        LoginFailureLogController controller = new LoginFailureLogController(
                service, mock(LoginFailureLogMapper.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.grouped(null, null, null, "not-an-ip", null, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(service, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsScopedIpv6IpEqualsBeforeRepositoryExecution() {
        LoginFailureQueryService service = mock(LoginFailureQueryService.class);
        LoginFailureLogController controller = new LoginFailureLogController(
                service, mock(LoginFailureLogMapper.class));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.grouped(null, null, null, "fe80::1%lo0", null, PageRequest.of(0, 20)));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        verify(service, never()).find(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void normalizesIpv4MappedIpv6IpEquals() {
        LoginFailureQueryService service = mock(LoginFailureQueryService.class);
        when(service.find(any(), any())).thenReturn(Page.empty());
        LoginFailureLogController controller = new LoginFailureLogController(
                service, mock(LoginFailureLogMapper.class));

        controller.grouped(null, null, null, "::ffff:192.0.2.128", null, PageRequest.of(0, 20));

        ArgumentCaptor<LoginFailQuery> query = ArgumentCaptor.forClass(LoginFailQuery.class);
        verify(service).find(query.capture(), any());
        assertEquals("192.0.2.128", query.getValue().getIpEquals());
    }

    @Test
    void sanitizesTextFiltersBeforeServiceExecution() {
        LoginFailureQueryService service = mock(LoginFailureQueryService.class);
        when(service.find(any(), any())).thenReturn(Page.empty());
        LoginFailureLogController controller = new LoginFailureLogController(
                service, mock(LoginFailureLogMapper.class));

        controller.grouped(null, null, "kim\nowner", null, "Bad\r\nCredentials", PageRequest.of(0, 20));

        ArgumentCaptor<LoginFailQuery> query = ArgumentCaptor.forClass(LoginFailQuery.class);
        verify(service).find(query.capture(), any());
        assertEquals("kim owner", query.getValue().getUsernameLike());
        assertEquals("Bad  Credentials", query.getValue().getFailureType());
    }

    @Test
    void serializesLoginFailureLogDtoFieldsForAdminAuditList() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LoginFailureLogDto dto = new LoginFailureLogDto(
                7L,
                OffsetDateTime.parse("2026-04-08T02:02:26.932Z"),
                "kim.owner",
                "192.0.2.10",
                "BAD_CREDENTIALS",
                "Bad credentials",
                "JUnit Agent");

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(dto));

        assertEquals(7L, json.get("id").asLong());
        assertTrue(json.hasNonNull("occurredAt"));
        assertEquals("kim.owner", json.get("username").asText());
        assertEquals("192.0.2.10", json.get("remoteIp").asText());
        assertEquals("BAD_CREDENTIALS", json.get("failureType").asText());
        assertEquals("Bad credentials", json.get("message").asText());
        assertEquals("JUnit Agent", json.get("userAgent").asText());
    }
}
