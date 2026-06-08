package com.br.face.models;

import java.time.LocalDateTime;

import com.br.face.request.UserRequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * User with facial biometrics. Recognition relies on a single ArcFace embedding
 * (512 floats) per user — no training and no multiple photos.
 */
@Entity
@Table(name = "tb_user")
@Schema(name = "User", description = "Usuário com biometria facial baseada em embedding ArcFace.")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	/**
	 * ArcFace facial embedding (512 floats) stored as a compact binary blob
	 * (little-endian, 4 bytes per float). A single photo per user is enough — no
	 * training and no multiple photos.
	 */
	@Lob
	@Column(name = "face_embedding")
	private byte[] faceEmbedding;

	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public User() {
	}

	public User(UserRequest request) {
		this.name = request.name();
	}

	@PrePersist
	private void prePersist() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	private void preUpdate() {
		this.updatedAt = LocalDateTime.now();
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public byte[] getFaceEmbedding() {
		return faceEmbedding;
	}

	public void setFaceEmbedding(byte[] faceEmbedding) {
		this.faceEmbedding = faceEmbedding;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
