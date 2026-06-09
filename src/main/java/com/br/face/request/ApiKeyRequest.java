package com.br.face.request;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "ApiKeyRequest", description = "Dados para gerar uma chave de API.")
public record ApiKeyRequest(
		@Schema(description = "Nome/identificação da chave.", example = "Terminal do portão 1")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.") String name,

		@Schema(description = "Descrição opcional do uso da chave.", example = "Acesso somente ao reconhecimento.")
		@Size(max = 255, message = "A descrição deve ter no máximo 255 caracteres.") String description,

		@Schema(description = "Data/hora de expiração (ISO-8601). Vazio = nunca expira.", example = "2027-01-01T00:00:00")
		@Future(message = "A validade deve ser no futuro.") LocalDateTime expiresAt) {
}
