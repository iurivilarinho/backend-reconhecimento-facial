package com.br.face.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Autenticação por API key via header {@code X-API-Key}. Quando a chave
 * apresentada corresponde a uma das chaves configuradas, a requisição é
 * autenticada; caso contrário segue sem autenticação e o Spring Security
 * responde 401 nos endpoints protegidos. A comparação é feita em tempo
 * constante para evitar timing attacks.
 */
public class ApiKeyAuthFilter extends OncePerRequestFilter {

	public static final String HEADER = "X-API-Key";

	private final Set<String> apiKeys;

	public ApiKeyAuthFilter(Set<String> apiKeys) {
		this.apiKeys = apiKeys;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		String presented = request.getHeader(HEADER);
		if (presented != null && isValid(presented)) {
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken("api-client",
					null, AuthorityUtils.createAuthorityList("ROLE_API"));
			SecurityContextHolder.getContext().setAuthentication(authentication);
		}
		filterChain.doFilter(request, response);
	}

	private boolean isValid(String presented) {
		byte[] presentedBytes = presented.getBytes(StandardCharsets.UTF_8);
		boolean valid = false;
		for (String key : apiKeys) {
			if (MessageDigest.isEqual(presentedBytes, key.getBytes(StandardCharsets.UTF_8))) {
				valid = true;
			}
		}
		return valid;
	}

}
