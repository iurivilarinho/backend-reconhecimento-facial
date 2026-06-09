package com.br.face.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {

	private static final String API_KEY = "apiKey";

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI()
				.info(new Info().title("API de Reconhecimento Facial")
						.description("Reconhecimento facial com ArcFace (InsightFace) via ONNX Runtime: uma foto por "
								+ "usuário, sem treino. Detecção e alinhamento com o detector YuNet do OpenCV. "
								+ "Autenticação por API key no header X-API-Key.")
						.version("1"))
				.components(new Components().addSecuritySchemes(API_KEY,
						new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
								.name("X-API-Key").description("Chave de API para autenticação.")))
				.addSecurityItem(new SecurityRequirement().addList(API_KEY));
	}

}
