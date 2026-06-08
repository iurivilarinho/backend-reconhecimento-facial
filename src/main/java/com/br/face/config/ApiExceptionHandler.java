package com.br.face.config;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.persistence.EntityNotFoundException;

/**
 * Tratamento global de erros da API, padronizando o corpo de resposta com
 * timestamp e a lista de mensagens.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

	public record ErrorResponse(LocalDateTime timestamp, List<String> message) {
		public ErrorResponse(String message) {
			this(LocalDateTime.now(), List.of(message));
		}

		public ErrorResponse(List<String> message) {
			this(LocalDateTime.now(), message);
		}
	}

	@ExceptionHandler(EntityNotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(IllegalArgumentException.class)
	public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex) {
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ErrorResponse> handleConflict(DataIntegrityViolationException ex) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
		List<String> errors = ex.getBindingResult().getAllErrors().stream()
				.map(error -> error.getDefaultMessage()).toList();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse(errors));
	}

}
