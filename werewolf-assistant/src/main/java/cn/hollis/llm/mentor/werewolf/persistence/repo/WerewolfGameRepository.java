package cn.hollis.llm.mentor.werewolf.persistence.repo;

import cn.hollis.llm.mentor.werewolf.persistence.entity.WerewolfGameEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WerewolfGameRepository extends JpaRepository<WerewolfGameEntity, Long> {

    Optional<WerewolfGameEntity> findBySessionUuid(String sessionUuid);

    Page<WerewolfGameEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(String status);
}
