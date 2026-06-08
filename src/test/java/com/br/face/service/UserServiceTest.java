package com.br.face.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.br.face.models.User;
import com.br.face.repository.UserRepository;
import com.br.face.request.UserRequest;

import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

	@Mock
	private UserRepository userRepository;

	@InjectMocks
	private UserService userService;

	@Test
	void findByIdShouldReturnUserWhenPresent() {
		User user = new User(new UserRequest("Maria Silva"));
		when(userRepository.findById(1L)).thenReturn(Optional.of(user));

		User result = userService.findById(1L);

		assertSame(user, result);
	}

	@Test
	void findByIdShouldThrowWhenMissing() {
		when(userRepository.findById(99L)).thenReturn(Optional.empty());

		assertThrows(EntityNotFoundException.class, () -> userService.findById(99L));
	}

	@Test
	void createShouldSaveUserFromRequest() {
		UserRequest request = new UserRequest("João Souza");
		when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

		userService.create(request);

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).save(captor.capture());
		assertEquals("João Souza", captor.getValue().getName());
	}

}
