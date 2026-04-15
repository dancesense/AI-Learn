package cn.hollis.llm.mentor.werewolf.live.repo;

import cn.hollis.llm.mentor.werewolf.live.entity.WerewolfLiveSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WerewolfLiveSessionRepository extends JpaRepository<WerewolfLiveSessionEntity, Long> {
    Optional<WerewolfLiveSessionEntity> findBySessionUuid(String sessionUuid);
}

