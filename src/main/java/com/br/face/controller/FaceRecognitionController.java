package com.br.face.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.br.face.models.User;
import com.br.face.response.RecognitionResponse;
import com.br.face.response.UserResponse;
import com.br.face.service.FaceRecognitionService;
import com.br.face.service.UserService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.persistence.EntityNotFoundException;

/**
 * Facial recognition with ArcFace: a single photo per user, no training.
 * Replaces the legacy flow (EigenFaceRecognizer with 10 training photos).
 */
@RestController
@RequestMapping("/face")
public class FaceRecognitionController {

	private final FaceRecognitionService faceRecognitionService;
	private final UserService userService;

	/** Maximum accepted cosine distance (the lower, the stricter). */
	@Value("${arcface.threshold:0.5}")
	private double threshold;

	public FaceRecognitionController(FaceRecognitionService faceRecognitionService, UserService userService) {
		this.faceRecognitionService = faceRecognitionService;
		this.userService = userService;
	}

	@Operation(summary = "Cadastrar biometria", description = "Gera e salva o embedding facial do usuário a partir de UMA foto.")
	@ApiResponse(responseCode = "200", description = "Biometria cadastrada")
	@ApiResponse(responseCode = "400", description = "Imagem ausente ou nenhum rosto detectado")
	@ApiResponse(responseCode = "404", description = "Usuário não encontrado")
	@PostMapping(value = "/{userId}/biometrics", consumes = "multipart/form-data")
	public ResponseEntity<UserResponse> enroll(@RequestPart MultipartFile file, @PathVariable Long userId)
			throws IOException {
		validateImage(file);
		User user = userService.findById(userId);
		float[] embedding = faceRecognitionService.embed(file.getBytes());
		if (embedding == null) {
			throw new IllegalArgumentException("Nenhum rosto detectado na imagem. Use uma foto frontal com boa luz.");
		}
		user.setFaceEmbedding(FaceRecognitionService.serialize(embedding));
		return ResponseEntity.ok(new UserResponse(userService.save(user)));
	}

	@Operation(summary = "Reconhecer", description = "Identifica (1:N) o usuário cuja biometria mais se aproxima da foto enviada.")
	@ApiResponse(responseCode = "200", description = "Usuário reconhecido")
	@ApiResponse(responseCode = "400", description = "Imagem ausente ou nenhum rosto detectado")
	@ApiResponse(responseCode = "404", description = "Nenhum usuário reconhecido")
	@PostMapping(value = "/recognize", consumes = "multipart/form-data")
	public ResponseEntity<RecognitionResponse> recognize(@RequestPart MultipartFile file) throws IOException {
		validateImage(file);
		float[] descriptor = faceRecognitionService.embed(file.getBytes());
		if (descriptor == null) {
			throw new IllegalArgumentException("Nenhum rosto detectado na imagem.");
		}

		List<User> users = userService.findEnrolled();
		User best = null;
		double smallestDistance = Double.MAX_VALUE;
		for (User user : users) {
			double distance = FaceRecognitionService.cosineDistance(descriptor,
					FaceRecognitionService.parse(user.getFaceEmbedding()));
			if (distance < smallestDistance) {
				smallestDistance = distance;
				best = user;
			}
		}

		if (best == null || smallestDistance > threshold) {
			throw new EntityNotFoundException("Nenhum usuário reconhecido.");
		}
		return ResponseEntity.ok(new RecognitionResponse(best, smallestDistance));
	}

	private void validateImage(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new IllegalArgumentException("Envie uma imagem no campo 'file'.");
		}
	}

}
