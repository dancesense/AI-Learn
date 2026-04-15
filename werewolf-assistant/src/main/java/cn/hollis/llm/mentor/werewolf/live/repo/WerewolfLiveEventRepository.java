package cn.hollis.llm.mentor.werewolf.live.repo;

import cn.hollis.llm.mentor.werewolf.live.entity.WerewolfLiveEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WerewolfLiveEventRepository extends JpaRepository<WerewolfLiveEventEntity, Long> {

    List<WerewolfLiveEventEntity> findTop120BySession_IdAndEventTypeOrderByCreatedAtAsc(Long sessionId, String eventType);

    List<WerewolfLiveEventEntity> findTop80BySession_IdOrderByCreatedAtDesc(Long sessionId);
}

