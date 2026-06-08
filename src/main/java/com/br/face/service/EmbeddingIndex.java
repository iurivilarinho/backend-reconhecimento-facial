package com.br.face.service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.br.face.models.User;
import com.br.face.repository.UserRepository;

/**
 * Índice em memória dos embeddings faciais para a identificação 1:N. Carrega os
 * usuários com biometria uma única vez na inicialização e se mantém atualizado a
 * cada cadastro — assim o reconhecimento não toca no banco nem desserializa
 * embeddings a cada requisição. A busca é uma varredura linear (rápida para
 * dezenas de milhares de usuários, pois compara apenas 512 floats por usuário).
 */
@Component
public class EmbeddingIndex {

	private static final Logger log = LoggerFactory.getLogger(EmbeddingIndex.class);

	/** Candidato em memória, sem ida ao banco no reconhecimento. */
	public record Candidate(Long userId, String name, float[] embedding) {
	}

	/** Melhor correspondência encontrada. */
	public record Match(Long userId, String name, double distance) {
	}

	private final UserRepository userRepository;
	private final ConcurrentMap<Long, Candidate> candidates = new ConcurrentHashMap<>();

	public EmbeddingIndex(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@EventListener(ApplicationReadyEvent.class)
	void load() {
		try {
			List<User> enrolled = userRepository.findByFaceEmbeddingIsNotNull();
			for (User user : enrolled) {
				put(user.getId(), user.getName(), FaceRecognitionService.fromBytes(user.getFaceEmbedding()));
			}
			log.info("Índice facial carregado: {} usuário(s) com biometria.", candidates.size());
		} catch (Exception ex) {
			// Banco indisponível na subida não deve derrubar a aplicação: o índice
			// começa vazio e é preenchido conforme os cadastros acontecem.
			log.warn("Não foi possível carregar o índice facial na inicialização: {}", ex.getMessage());
		}
	}

	/** Insere ou atualiza a biometria de um usuário no índice. */
	public void put(Long userId, String name, float[] embedding) {
		if (embedding == null || embedding.length == 0) {
			candidates.remove(userId);
			return;
		}
		candidates.put(userId, new Candidate(userId, name, embedding));
	}

	/** Remove um usuário do índice. */
	public void remove(Long userId) {
		candidates.remove(userId);
	}

	/** Quantidade de usuários com biometria no índice. */
	public int size() {
		return candidates.size();
	}

	/**
	 * Encontra o usuário cuja biometria tem a menor distância do coseno até o
	 * descritor informado.
	 *
	 * @param descriptor Embedding do rosto consultado.
	 * @return A melhor correspondência, ou vazio se o índice estiver vazio.
	 */
	public Optional<Match> findBest(float[] descriptor) {
		Candidate best = null;
		double smallestDistance = Double.MAX_VALUE;
		for (Candidate candidate : candidates.values()) {
			double distance = FaceRecognitionService.cosineDistance(descriptor, candidate.embedding());
			if (distance < smallestDistance) {
				smallestDistance = distance;
				best = candidate;
			}
		}
		if (best == null) {
			return Optional.empty();
		}
		return Optional.of(new Match(best.userId(), best.name(), smallestDistance));
	}

}
