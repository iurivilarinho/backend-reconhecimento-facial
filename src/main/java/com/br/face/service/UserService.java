package com.br.face.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.br.face.models.User;
import com.br.face.repository.UserRepository;
import com.br.face.request.UserRequest;

import jakarta.persistence.EntityNotFoundException;

@Service
public class UserService {

	private final UserRepository userRepository;

	public UserService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Transactional(readOnly = true)
	public List<User> findAll() {
		return userRepository.findAll();
	}

	@Transactional(readOnly = true)
	public User findById(Long id) {
		return userRepository.findById(id)
				.orElseThrow(() -> new EntityNotFoundException("Usuário não encontrado para o ID: " + id));
	}

	@Transactional
	public User create(UserRequest request) {
		return userRepository.save(new User(request));
	}

	@Transactional
	public User save(User user) {
		return userRepository.save(user);
	}

}
