package com.br.face.response;

import com.br.face.service.EmbeddingIndex.Match;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "RecognitionResponse", description = "Resultado da identificação facial (1:N).")
public record RecognitionResponse(
		@Schema(description = "Identificador do usuário reconhecido.", example = "42") Long userId,
		@Schema(description = "Nome do usuário reconhecido.", example = "Maria Silva") String name,
		@Schema(description = "Similaridade do coseno (1 - distância). Quanto maior, mais confiante.", example = "0.62") double score,
		@Schema(description = "Distância do coseno até a biometria cadastrada.", example = "0.38") double distance) {

	public RecognitionResponse(Match match) {
		this(match.userId(), match.name(), 1.0 - match.distance(), match.distance());
	}
}
