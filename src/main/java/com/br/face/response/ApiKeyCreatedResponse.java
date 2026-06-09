package com.br.face.response;

import com.br.face.models.ApiKey;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Resposta da criação de uma chave de API. A chave em texto plano (`apiKey`) é
 * retornada UMA ÚNICA VEZ — não há como recuperá-la depois, apenas regenerar.
 */
@Schema(name = "ApiKeyCreatedResponse", description = "Chave recém-criada. A chave em texto plano é exibida só agora.")
public record ApiKeyCreatedResponse(
		@Schema(description = "Chave em texto plano. Guarde com segurança: não será exibida novamente.", example = "fk_8sJk2do9X1...") String apiKey,
		@Schema(description = "Metadados da chave criada.") ApiKeyResponse details,
		@Schema(description = "Aviso ao consumidor.", example = "Guarde esta chave; ela não será exibida novamente.") String warning) {

	public ApiKeyCreatedResponse(String apiKey, ApiKey entity) {
		this(apiKey, new ApiKeyResponse(entity), "Guarde esta chave com segurança; ela não será exibida novamente.");
	}
}
