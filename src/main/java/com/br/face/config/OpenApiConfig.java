package com.br.face.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI customOpenAPI() {
		return new OpenAPI().info(new Info().title("API de Reconhecimento Facial")
				.description("Reconhecimento facial com ArcFace (InsightFace) via ONNX Runtime: uma foto por usuário, "
						+ "sem treino. Detecção e alinhamento com o detector YuNet do OpenCV.")
				.version("1"));
	}

}
