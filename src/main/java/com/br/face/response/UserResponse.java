package com.br.face.response;

import com.br.face.models.User;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UserResponse", description = "Dados de um usuário.")
public record UserResponse(
		@Schema(description = "Identificador do usuário.", example = "42") Long id,
		@Schema(description = "Nome do usuário.", example = "Maria Silva") String name,
		@Schema(description = "Indica se o usuário já possui biometria facial cadastrada.", example = "true") boolean faceEnrolled) {

	public UserResponse(User user) {
		this(user.getId(), user.getName(), user.getFaceEmbedding() != null && user.getFaceEmbedding().length > 0);
	}
}
