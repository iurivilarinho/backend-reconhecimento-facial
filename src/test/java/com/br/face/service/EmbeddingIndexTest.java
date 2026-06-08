package com.br.face.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.br.face.repository.UserRepository;
import com.br.face.service.EmbeddingIndex.Match;

@ExtendWith(MockitoExtension.class)
class EmbeddingIndexTest {

	@Mock
	private UserRepository userRepository;

	@Test
	void findBestShouldReturnNearestCandidate() {
		EmbeddingIndex index = new EmbeddingIndex(userRepository);
		index.put(1L, "A", new float[] { 1f, 0f, 0f });
		index.put(2L, "B", new float[] { 0f, 1f, 0f });

		Optional<Match> match = index.findBest(new float[] { 0.9f, 0.1f, 0f });

		assertTrue(match.isPresent());
		assertEquals(1L, match.get().userId());
		assertEquals("A", match.get().name());
	}

	@Test
	void findBestShouldBeEmptyWhenIndexIsEmpty() {
		EmbeddingIndex index = new EmbeddingIndex(userRepository);
		assertTrue(index.findBest(new float[] { 1f, 0f }).isEmpty());
	}

	@Test
	void putWithEmptyEmbeddingShouldRemoveCandidate() {
		EmbeddingIndex index = new EmbeddingIndex(userRepository);
		index.put(1L, "A", new float[] { 1f, 0f });
		assertEquals(1, index.size());

		index.put(1L, "A", new float[0]);

		assertEquals(0, index.size());
	}

}
