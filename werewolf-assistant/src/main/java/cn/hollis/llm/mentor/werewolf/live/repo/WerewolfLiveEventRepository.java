package cn.hollis.llm.mentor.werewolf.live.repo;

import cn.hollis.llm.mentor.werewolf.live.entity.WerewolfLiveEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WerewolfLiveEventRepository extends JpaRepository<WerewolfLiveEventEntity, Long> {

    List<WerewolfLiveEventEntity> findTop120BySession_IdAndEventTypeOrderByCreatedAtAsc(Long sessionId, String eventType);

    List<WerewolfLiveEventEntity> findTop80BySession_IdOrderByCreatedAtDesc(Long sessionId);

    List<WerewolfLiveEventEntity> findBySession_IdAndDayOrderByCreatedAtAsc(Long sessionId, Integer day);

    List<WerewolfLiveEventEntity> findBySession_IdAndDayAndEventTypeOrderByCreatedAtAsc(Long sessionId, Integer day, String eventType);

    List<WerewolfLiveEventEntity> findBySession_IdAndEventTypeOrderByCreatedAtAsc(Long sessionId, String eventType);
}

