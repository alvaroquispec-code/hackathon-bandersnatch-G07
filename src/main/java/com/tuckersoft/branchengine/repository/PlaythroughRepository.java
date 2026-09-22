package com.tuckersoft.branchengine.repository;

import com.tuckersoft.branchengine.entity.Playthrough;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PlaythroughRepository extends JpaRepository<Playthrough, Long> {
    boolean existsByPlayerTag(String playerTag);
    List<Playthrough> findByUserIdOrderByCreatedAtDescIdDesc(Long userId);
    List<Playthrough> findAllByOrderByCreatedAtDescIdDesc();
}
