package cn.hollis.llm.mentor.werewolf.persistence.repo;

import cn.hollis.llm.mentor.werewolf.persistence.entity.WerewolfSnapshotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WerewolfSnapshotRepository extends JpaRepository<WerewolfSnapshotEntity, Long> {

    List<WerewolfSnapshotEntity> findByGame_IdOrderByCreatedAtAsc(Long gameId);

    long countByGame_Id(Long gameId);
}
