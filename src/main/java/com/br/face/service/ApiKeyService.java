package com.br.face.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.face.models.ApiKey;
import com.br.face.repository.ApiKeyRepository;
import com.br.face.request.ApiKeyRequest;

import jakarta.persistence.EntityNotFoundException;

/**
 * Geração, validação e gestão das chaves de API persistidas. A chave em texto
 * plano é gerada com {@link SecureRandom}, devolvida UMA única vez e nunca
 * armazenada — guardamos só o hash SHA-256. A validação usa um cache em memória
 * (hash → dados), carregado na subida e atualizado a cada criação/revogação,
 * para não tocar no banco a cada requisição.
 */
@Service
public class ApiKeyService {

	private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);

	private static final String PREFIX = "fk_";
	private static final int KEY_BYTES = 32;
	private static final int DISPLAY_PREFIX_LENGTH = 11;
	private static final long TOUCH_THROTTLE_MS = 60_000;

	/** Chave recém-criada: entidade persistida + texto plano (exibido uma vez). */
	public record ApiKeyCreation(ApiKey apiKey, String plaintext) {
	}

	private static final class CachedKey {
		private final Long id;
		private final String name;
		private final LocalDateTime expiresAt;
		private volatile long lastTouchPersistedAt;

		private CachedKey(ApiKey apiKey) {
			this.id = apiKey.getId();
			this.name = apiKey.getName();
			this.expiresAt = apiKey.getExpiresAt();
		}

		private boolean expired() {
			return expiresAt != null && expiresAt.isBefore(LocalDateTime.now());
		}
	}

	private final ApiKeyRepository apiKeyRepository;
	private final SecureRandom secureRandom = new SecureRandom();
	private final ConcurrentMap<String, CachedKey> cache = new ConcurrentHashMap<>();

	public ApiKeyService(ApiKeyRepository apiKeyRepository) {
		this.apiKeyRepository = apiKeyRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	void load() {
		try {
			for (ApiKey key : apiKeyRepository.findByActiveTrue()) {
				cache.put(key.getKeyHash(), new CachedKey(key));
			}
			log.info("Chaves de API carregadas: {} ativa(s).", cache.size());
		} catch (Exception ex) {
			log.warn("Não foi possível carregar as chaves de API na inicialização: {}", ex.getMessage());
		}
	}

	@Transactional
	public ApiKeyCreation create(ApiKeyRequest request) {
		byte[] raw = new byte[KEY_BYTES];
		secureRandom.nextBytes(raw);
		String plaintext = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
		String keyPrefix = plaintext.substring(0, DISPLAY_PREFIX_LENGTH);
		ApiKey entity = new ApiKey(request, sha256Hex(plaintext), keyPrefix);
		ApiKey saved = apiKeyRepository.save(entity);
		cache.put(saved.getKeyHash(), new CachedKey(saved));
		return new ApiKeyCreation(saved, plaintext);
	}

	@Transactional(readOnly = true)
	public List<ApiKey> findAll() {
		return apiKeyRepository.findAll();
	}

	@Transactional(readOnly = true)
	public ApiKey findById(Long id) {
		return apiKeyRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Chave de API não encontrada para o ID: " + id));
	}

	@Transactional
	public void revoke(Long id) {
		ApiKey entity = findById(id);
		entity.setActive(false);
		apiKeyRepository.save(entity);
		cache.remove(entity.getKeyHash());
	}

	/**
	 * Valida uma chave apresentada. Retorna o nome da chave (para o principal) se
	 * ela existir, estiver ativa e dentro da validade. Atualiza o último uso de
	 * forma limitada (no máximo a cada minuto por chave) para não onerar o banco.
	 *
	 * @param presented Chave em texto plano enviada no header.
	 * @return Nome da chave, ou vazio se inválida/expirada/revogada.
	 */
	public Optional<String> validate(String presented) {
		CachedKey cached = cache.get(sha256Hex(presented));
		if (cached == null) {
			return Optional.empty();
		}
		if (cached.expired()) {
			cache.remove(sha256Hex(presented));
			return Optional.empty();
		}
		touch(cached);
		return Optional.of(cached.name);
	}

	private void touch(CachedKey cached) {
		long now = System.currentTimeMillis();
		if (now - cached.lastTouchPersistedAt < TOUCH_THROTTLE_MS) {
			return;
		}
		cached.lastTouchPersistedAt = now;
		try {
			apiKeyRepository.touchLastUsed(cached.id, LocalDateTime.now());
		} catch (Exception ex) {
			log.debug("Falha ao atualizar último uso da chave {}: {}", cached.id, ex.getMessage());
		}
	}

	private static String sha256Hex(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder(digest.length * 2);
			for (byte b : digest) {
				hex.append(Character.forDigit((b >> 4) & 0xF, 16));
				hex.append(Character.forDigit(b & 0xF, 16));
			}
			return hex.toString();
		} catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 indisponível.", ex);
		}
	}

}
