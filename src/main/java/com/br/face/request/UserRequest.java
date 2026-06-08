package com.br.face.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "UserRequest", description = "Dados para criar um usuário.")
public record UserRequest(
		@Schema(description = "Nome do usuário.", example = "Maria Silva")
		@NotBlank(message = "O nome é obrigatório.")
		@Size(max = 150, message = "O nome deve ter no máximo 150 caracteres.") String name) {
}
