package com.br.face.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.face.request.ApiKeyRequest;
import com.br.face.response.ApiKeyCreatedResponse;
import com.br.face.response.ApiKeyResponse;
import com.br.face.service.ApiKeyService;
import com.br.face.service.ApiKeyService.ApiKeyCreation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

/**
 * Gestão das chaves de API. Endpoints administrativos: exigem uma chave de
 * bootstrap (configurada em {@code api.keys}/{@code API_KEYS}), não uma chave
 * comum gerada aqui — ver a configuração de segurança.
 */
@RestController
@RequestMapping("/api-keys")
public class ApiKeyController {

	private final ApiKeyService apiKeyService;

	public ApiKeyController(ApiKeyService apiKeyService) {
		this.apiKeyService = apiKeyService;
	}

	@Operation(summary = "Gerar chave de API", description = "Cria e devolve uma chave nova. A chave em texto plano é exibida apenas nesta resposta.")
	@ApiResponse(responseCode = "201", description = "Chave criada")
	@ApiResponse(responseCode = "400", description = "Dados inválidos")
	@PostMapping
	public ResponseEntity<ApiKeyCreatedResponse> create(@Valid @RequestBody ApiKeyRequest request) {
		ApiKeyCreation creation = apiKeyService.create(request);
		return ResponseEntity.status(201).body(new ApiKeyCreatedResponse(creation.plaintext(), creation.apiKey()));
	}

	@Operation(summary = "Listar chaves de API", description = "Lista as chaves (apenas metadados, sem o segredo).")
	@ApiResponse(responseCode = "200", description = "Lista de chaves")
	@GetMapping
	public ResponseEntity<List<ApiKeyResponse>> findAll() {
		List<ApiKeyResponse> response = apiKeyService.findAll().stream().map(ApiKeyResponse::new).toList();
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Buscar chave por ID", description = "Retorna os metadados de uma chave.")
	@ApiResponse(responseCode = "200", description = "Chave encontrada")
	@ApiResponse(responseCode = "404", description = "Chave não encontrada")
	@GetMapping("/{id}")
	public ResponseEntity<ApiKeyResponse> findById(@PathVariable Long id) {
		return ResponseEntity.ok(new ApiKeyResponse(apiKeyService.findById(id)));
	}

	@Operation(summary = "Revogar chave de API", description = "Desativa a chave imediatamente.")
	@ApiResponse(responseCode = "204", description = "Chave revogada")
	@ApiResponse(responseCode = "404", description = "Chave não encontrada")
	@DeleteMapping("/{id}")
	public ResponseEntity<Void> revoke(@PathVariable Long id) {
		apiKeyService.revoke(id);
		return ResponseEntity.noContent().build();
	}

}
