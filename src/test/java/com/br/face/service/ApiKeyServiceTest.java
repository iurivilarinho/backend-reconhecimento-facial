package com.br.face.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.br.face.models.ApiKey;
import com.br.face.repository.ApiKeyRepository;
import com.br.face.request.ApiKeyRequest;
import com.br.face.service.ApiKeyService.ApiKeyCreation;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ApiKeyServiceTest {

	@Mock
	private ApiKeyRepository apiKeyRepository;

	@InjectMocks
	private ApiKeyService apiKeyService;

	@Test
	void createShouldGenerateValidatableKey() {
		when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(returnsFirstArg());

		ApiKeyCreation creation = apiKeyService.create(new ApiKeyRequest("Terminal 1", "desc", null));

		assertTrue(creation.plaintext().startsWith("fk_"));
		assertEquals(Optional.of("Terminal 1"), apiKeyService.validate(creation.plaintext()));
	}

	@Test
	void validateShouldRejectUnknownKey() {
		assertTrue(apiKeyService.validate("fk_inexistente").isEmpty());
	}

	@Test
	void validateShouldRejectExpiredKey() {
		when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(returnsFirstArg());

		ApiKeyCreation creation = apiKeyService
				.create(new ApiKeyRequest("Expirada", null, LocalDateTime.now().minusMinutes(1)));

		assertTrue(apiKeyService.validate(creation.plaintext()).isEmpty());
	}

}
