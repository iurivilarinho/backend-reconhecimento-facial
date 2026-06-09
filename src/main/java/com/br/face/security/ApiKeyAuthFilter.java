package com.br.face.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.br.face.service.ApiKeyService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticação por API key via header {@code X-API-Key}. Duas origens de chave:
 * <ul>
 * <li><b>Bootstrap</b> (configuradas em {@code api.keys}/{@code API_KEYS}):
 * autenticam com {@code ROLE_ADMIN} + {@code ROLE_API} e gerenciam as chaves do
 * banco.</li>
 * <li><b>Banco</b> (geradas via {@code /api-keys}): autenticam com
 * {@code ROLE_API}.</li>
 * </ul>
 * Sem chave válida, a requisição segue sem autenticação e o Spring Security
 * responde 401 nos endpoints protegidos. A comparação das chaves de bootstrap é
 * feita em tempo constante.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final Set<String> bootstrapKeys;
	private final ApiKeyService apiKeyService;

	public ApiKeyAuthFilter(Set<String> bootstrapKeys, ApiKeyService apiKeyService) {
		this.bootstrapKeys = bootstrapKeys;
		this.apiKeyService = apiKeyService;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String presented = request.getHeader(HEADER);
		if (presented != null) {
			if (matchesBootstrap(presented)) {
				authenticate("bootstrap-admin",
						List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_API")));
			} else {
				apiKeyService.validate(presented).ifPresent(
						name -> authenticate(name, List.of(new SimpleGrantedAuthority("ROLE_API"))));
			}
		}
		filterChain.doFilter(request, response);
	}

	private void authenticate(String principal, List<GrantedAuthority> authorities) {
		UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(principal, null,
				authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

	private boolean matchesBootstrap(String presented) {
		byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
		boolean valid = false;
		for (String key : bootstrapKeys) {
			if (MessageDigest.isEqual(presentedBytes, key.getBytes(StandardCharsets.UTF_8))) {
				valid = true;
			}
		}
		return valid;
	}

}
