package com.br.face.config;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.br.face.config.ApiExceptionHandler.ErrorResponse;
import com.br.face.security.ApiKeyAuthFilter;
import com.br.face.service.ApiKeyService;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Segurança stateless por API key. Todos os endpoints exigem o header
 * {@code X-API-Key} com uma das chaves configuradas em {@code api.keys}, exceto
 * a documentação (Swagger/OpenAPI) e o health. Sem chaves configuradas, a API
 * fica fechada (fail-closed).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	private static final String[] PUBLIC_PATHS = { "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
			"/actuator/health" };

	private final Set<String> bootstrapKeys;
	private final ObjectMapper objectMapper;
	private final ApiKeyService apiKeyService;

	public SecurityConfig(@Value("${api.keys:}") String apiKeysCsv, ObjectMapper objectMapper,
			ApiKeyService apiKeyService) {
		this.bootstrapKeys = parseKeys(apiKeysCsv);
		this.objectMapper = objectMapper;
		this.apiKeyService = apiKeyService;
		if (bootstrapKeys.isEmpty()) {
			log.warn("Nenhuma API key de bootstrap configurada (api.keys/API_KEYS): "
					+ "nao sera possivel gerenciar chaves nem acessar os endpoints ate cadastrar uma.");
		}
	}

	private static Set<String> parseKeys(String csv) {
		if (csv == null || csv.isBlank()) {
			return Set.of();
		}
		return Arrays.stream(csv.split(",")).map(String::trim).filter(key -> !key.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
	}

	@Bean
	public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth.requestMatchers(PUBLIC_PATHS).permitAll()
						.requestMatchers("/api-keys/**").hasRole("ADMIN").anyRequest().authenticated())
				.addFilterBefore(new ApiKeyAuthFilter(bootstrapKeys, apiKeyService),
						UsernamePasswordAuthenticationFilter.class)
				.exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorizedEntryPoint()));
		return http.build();
	}

	private AuthenticationEntryPoint unauthorizedEntryPoint() {
		return (request, response, authException) -> writeUnauthorized(response);
	}

	private void writeUnauthorized(HttpServletResponse response) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write(objectMapper.writeValueAsString(new ErrorResponse("API key ausente ou inválida.")));
	}

}
