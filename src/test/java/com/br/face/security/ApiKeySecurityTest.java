package com.br.face.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.br.face.config.SecurityConfig;
import com.br.face.controller.ApiKeyController;
import com.br.face.controller.UserController;
import com.br.face.service.ApiKeyService;
import com.br.face.service.UserService;

@WebMvcTest({ UserController.class, ApiKeyController.class })
@Import(SecurityConfig.class)
@TestPropertySource(properties = "api.keys=bootstrap-admin-key")
class ApiKeySecurityTest {

	private static final String BOOTSTRAP = "bootstrap-admin-key";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private ApiKeyService apiKeyService;

	@Test
	void shouldRejectRequestWithoutApiKey() throws Exception {
		mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectRequestWithWrongApiKey() throws Exception {
		when(apiKeyService.validate("wrong")).thenReturn(Optional.empty());
		mockMvc.perform(get("/users").header(ApiKeyAuthFilter.HEADER, "wrong")).andExpect(status().isUnauthorized());
	}

	@Test
	void bootstrapKeyShouldAccessRegularEndpoint() throws Exception {
		when(userService.findAll()).thenReturn(List.of());
		mockMvc.perform(get("/users").header(ApiKeyAuthFilter.HEADER, BOOTSTRAP)).andExpect(status().isOk());
	}

	@Test
	void bootstrapKeyShouldManageApiKeys() throws Exception {
		when(apiKeyService.findAll()).thenReturn(List.of());
		mockMvc.perform(get("/api-keys").header(ApiKeyAuthFilter.HEADER, BOOTSTRAP)).andExpect(status().isOk());
	}

	@Test
	void regularKeyShouldNotManageApiKeys() throws Exception {
		when(apiKeyService.validate("db-key")).thenReturn(Optional.of("Terminal 1"));
		mockMvc.perform(get("/api-keys").header(ApiKeyAuthFilter.HEADER, "db-key")).andExpect(status().isForbidden());
	}

}
