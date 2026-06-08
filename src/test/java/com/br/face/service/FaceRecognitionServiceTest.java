package com.br.face.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Testes do reconhecimento ArcFace ponta a ponta (detecção + alinhamento +
 * embedding), provando com fotos reais que a mesma pessoa é reconhecida e uma
 * pessoa diferente é rejeitada — sem depender de banco nem do contexto Spring.
 */
class FaceRecognitionServiceTest {

	private static final double THRESHOLD = 0.5;

	private static FaceRecognitionService service;

	@BeforeAll
	static void setUp() throws Exception {
		service = new FaceRecognitionService(2, 0, false);
		service.init();
	}

	@AfterAll
	static void tearDown() throws Exception {
		if (service != null) {
			service.shutdown();
		}
	}

	private static byte[] image(String name) throws Exception {
		try (InputStream in = FaceRecognitionServiceTest.class.getResourceAsStream("/faces/" + name)) {
			assertNotNull(in, "Imagem de teste ausente: " + name);
			return in.readAllBytes();
		}
	}

	@Test
	void cosineDistanceShouldBeZeroForIdenticalVectors() {
		float[] vector = { 0.1f, -0.2f, 0.3f, 0.4f };
		assertEquals(0.0, FaceRecognitionService.cosineDistance(vector, vector), 1e-6);
	}

	@Test
	void cosineDistanceShouldBeOneForOrthogonalVectors() {
		float[] a = { 1f, 0f };
		float[] b = { 0f, 1f };
		assertEquals(1.0, FaceRecognitionService.cosineDistance(a, b), 1e-6);
	}

	@Test
	void cosineDistanceShouldBeMaxForDifferentDimensions() {
		assertEquals(2.0, FaceRecognitionService.cosineDistance(new float[] { 1f }, new float[] { 1f, 1f }), 1e-6);
	}

	@Test
	void embeddingBinarySerializationShouldRoundTrip() {
		float[] original = { 0.1f, -0.25f, 0.5f, -1.0f, 0.0f };
		float[] restored = FaceRecognitionService.fromBytes(FaceRecognitionService.toBytes(original));
		assertArrayEquals(original, restored, 0f);
	}

	@Test
	void embedShouldProduceNormalized512Vector() throws Exception {
		float[] embedding = service.embed(image("personA_1.jpg"));

		assertNotNull(embedding, "Nenhum rosto detectado na imagem de teste.");
		assertEquals(512, embedding.length);
		double norm = 0;
		for (float value : embedding) {
			norm += value * value;
		}
		assertEquals(1.0, Math.sqrt(norm), 1e-3);
	}

	@Test
	void shouldRecognizeSamePersonAndRejectDifferentPerson() throws Exception {
		float[] personA1 = service.embed(image("personA_1.jpg"));
		float[] personA2 = service.embed(image("personA_2.jpg"));
		float[] personB1 = service.embed(image("personB_1.jpg"));

		assertNotNull(personA1);
		assertNotNull(personA2);
		assertNotNull(personB1);

		double sameDistance = FaceRecognitionService.cosineDistance(personA1, personA2);
		double differentDistance = FaceRecognitionService.cosineDistance(personA1, personB1);

		// A mesma pessoa fica muito mais perto do que pessoas diferentes.
		assertTrue(sameDistance < differentDistance,
				"Esperado mesma pessoa mais próxima: same=" + sameDistance + " diff=" + differentDistance);
		// E dentro do limite de reconhecimento; a diferente, fora.
		assertTrue(sameDistance < THRESHOLD, "Mesma pessoa deveria ser reconhecida. Distância: " + sameDistance);
		assertTrue(differentDistance > THRESHOLD,
				"Pessoa diferente deveria ser rejeitada. Distância: " + differentDistance);
	}

}
