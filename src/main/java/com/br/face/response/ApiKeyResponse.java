package com.br.face.response;

import java.time.LocalDateTime;

import com.br.face.models.ApiKey;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ApiKeyResponse", description = "Metadados de uma chave de API (sem o segredo).")
public record ApiKeyResponse(
		@Schema(description = "Identificador da chave.", example = "7") Long id,
		@Schema(description = "Nome da chave.", example = "Terminal do portão 1") String name,
		@Schema(description = "Descrição da chave.") String description,
		@Schema(description = "Prefixo da chave (identificação visual).", example = "fk_8sJk2do") String keyPrefix,
		@Schema(description = "Se a chave está ativa.", example = "true") boolean active,
		@Schema(description = "Validade (null = nunca expira).") LocalDateTime expiresAt,
		@Schema(description = "Último uso registrado.") LocalDateTime lastUsedAt,
		@Schema(description = "Data de criação.") LocalDateTime createdAt) {

	public ApiKeyResponse(ApiKey apiKey) {
		this(apiKey.getId(), apiKey.getName(), apiKey.getDescription(), apiKey.getKeyPrefix(), apiKey.isActive(),
				apiKey.getExpiresAt(), apiKey.getLastUsedAt(), apiKey.getCreatedAt());
	}
}
