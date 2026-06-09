package com.br.face.models;

import java.time.LocalDateTime;

import com.br.face.request.ApiKeyRequest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Chave de API persistida. A chave em texto plano nunca é armazenada: guardamos
 * apenas o hash SHA-256 (para validação) e um prefixo curto (para identificação
 * visual). Suporta nome, descrição, validade (expiração) e revogação.
 */
@Entity
@Table(name = "tb_api_key")
@Schema(name = "ApiKey", description = "Chave de API com nome, descrição, validade e revogação.")
public class ApiKey {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "description")
	private String description;

	/** Hash SHA-256 (hex) da chave. Único; nunca guardamos a chave em texto. */
	@Column(name = "key_hash", nullable = false, unique = true, length = 64)
	private String keyHash;

	/** Prefixo curto da chave, apenas para identificação na listagem. */
	@Column(name = "key_prefix", nullable = false)
	private String keyPrefix;

	@Column(name = "active", nullable = false)
	private boolean active = true;

	@Column(name = "expires_at")
	private LocalDateTime expiresAt;

	@Column(name = "last_used_at")
	private LocalDateTime lastUsedAt;

	@Column(name = "created_at", updatable = false, nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	public ApiKey() {
	}

	public ApiKey(ApiKeyRequest request, String keyHash, String keyPrefix) {
		this.name = request.name();
		this.description = request.description();
		this.expiresAt = request.expiresAt();
		this.keyHash = keyHash;
		this.keyPrefix = keyPrefix;
		this.active = true;
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

	/** Indica se a chave está ativa e dentro da validade. */
	public boolean isUsable() {
		return active && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public String getKeyHash() {
		return keyHash;
	}

	public String getKeyPrefix() {
		return keyPrefix;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public LocalDateTime getExpiresAt() {
		return expiresAt;
	}

	public LocalDateTime getLastUsedAt() {
		return lastUsedAt;
	}

	public void setLastUsedAt(LocalDateTime lastUsedAt) {
		this.lastUsedAt = lastUsedAt;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

}
