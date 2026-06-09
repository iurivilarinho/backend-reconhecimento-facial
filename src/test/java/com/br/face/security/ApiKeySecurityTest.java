package com.br.face.security;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.br.face.config.SecurityConfig;
import com.br.face.controller.UserController;
import com.br.face.service.UserService;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "api.keys=secret-key-123")
class ApiKeySecurityTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	void shouldRejectRequestWithoutApiKey() throws Exception {
		mockMvc.perform(get("/users")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRejectRequestWithWrongApiKey() throws Exception {
		mockMvc.perform(get("/users").header(ApiKeyAuthFilter.HEADER, "wrong")).andExpect(status().isUnauthorized());
	}

	@Test
	void shouldAllowRequestWithValidApiKey() throws Exception {
		when(userService.findAll()).thenReturn(List.of());
		mockMvc.perform(get("/users").header(ApiKeyAuthFilter.HEADER, "secret-key-123")).andExpect(status().isOk());
	}

}
