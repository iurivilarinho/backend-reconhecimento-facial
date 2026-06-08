package com.br.face.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.br.face.models.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

	List<User> findByFaceEmbeddingIsNotNull();

}
