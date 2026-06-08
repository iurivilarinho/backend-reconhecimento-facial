package com.br.face.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.br.face.request.UserRequest;
import com.br.face.response.UserResponse;
import com.br.face.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/users")
public class UserController {

	private final UserService userService;

	public UserController(UserService userService) {
		this.userService = userService;
	}

	@Operation(summary = "Criar usuário", description = "Cria um usuário sem biometria. Cadastre a biometria depois em /face.")
	@ApiResponse(responseCode = "201", description = "Usuário criado")
	@ApiResponse(responseCode = "400", description = "Dados inválidos")
	@PostMapping
	public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
		UserResponse response = new UserResponse(userService.create(request));
		return ResponseEntity.status(201).body(response);
	}

	@Operation(summary = "Listar usuários", description = "Lista todos os usuários cadastrados.")
	@ApiResponse(responseCode = "200", description = "Lista de usuários")
	@GetMapping
	public ResponseEntity<List<UserResponse>> findAll() {
		List<UserResponse> response = userService.findAll().stream().map(UserResponse::new).toList();
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Buscar usuário por ID", description = "Retorna um usuário pelo seu identificador.")
	@ApiResponse(responseCode = "200", description = "Usuário encontrado")
	@ApiResponse(responseCode = "404", description = "Usuário não encontrado")
	@GetMapping("/{id}")
	public ResponseEntity<UserResponse> findById(@PathVariable Long id) {
		return ResponseEntity.ok(new UserResponse(userService.findById(id)));
	}

}
