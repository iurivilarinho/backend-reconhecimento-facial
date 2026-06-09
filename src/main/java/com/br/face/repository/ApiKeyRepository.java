package com.br.face.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.br.face.models.ApiKey;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

	List<ApiKey> findByActiveTrue();

	@Modifying
	@Transactional
	@Query("update ApiKey a set a.lastUsedAt = :now where a.id = :id")
	void touchLastUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

}
