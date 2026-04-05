package cn.hollis.llm.mentor.werewolf.persistence;

import cn.hollis.llm.mentor.werewolf.persistence.dto.CloseGameRequest;
import cn.hollis.llm.mentor.werewolf.persistence.dto.CreateGameRequest;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfGameResponse;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfSnapshotResponse;
import cn.hollis.llm.mentor.werewolf.persistence.dto.WerewolfSnapshotSaveRequest;
import cn.hollis.llm.mentor.werewolf.persistence.entity.WerewolfGameEntity;
import cn.hollis.llm.mentor.werewolf.persistence.entity.WerewolfSnapshotEntity;
import cn.hollis.llm.mentor.werewolf.persistence.repo.WerewolfGameRepository;
import cn.hollis.llm.mentor.werewolf.persistence.repo.WerewolfSnapshotRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class WerewolfHistoryService {

    private final WerewolfGameRepository gameRepository;
    private final WerewolfSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public WerewolfHistoryService(WerewolfGameRepository gameRepository,
                                WerewolfSnapshotRepository snapshotRepository,
                                ObjectMapper objectMapper) {
        this.gameRepository = gameRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WerewolfGameResponse createGame(CreateGameRequest req) throws JsonProcessingException {
        String uuid = StringUtils.hasText(req.sessionUuid()) ? req.sessionUuid().trim() : UUID.randomUUID().toString();
        if (uuid.length() > 64) {
            uuid = uuid.substring(0, 64);
        }
        if (gameRepository.findBySessionUuid(uuid).isPresent()) {
            throw new IllegalStateException("sessionUuid 已存在: " + uuid);
        }
        WerewolfGameEntity e = new WerewolfGameEntity();
        e.setSessionUuid(uuid);
        e.setTotalPlayers(req.totalPlayers());
        e.setGameMode(req.gameMode());
        e.setBoardTemplateId(req.boardTemplateId());
        e.setMyPlayerId(req.myPlayerId());
        e.setMyRoleHint(req.myRoleHint());
        e.setWinningObjective(req.winningObjective());
        if (req.roleComposition() != null && !req.roleComposition().isEmpty()) {
            e.setRoleCompositionJson(objectMapper.writeValueAsString(req.roleComposition()));
        }
        e.setStatus("ACTIVE");
        gameRepository.save(e);
        return toResponse(e, 0L);
    }

    @Transactional(readOnly = true)
    public Page<WerewolfGameResponse> listGames(Pageable pageable) {
        return gameRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(g -> toResponse(g, snapshotRepository.countByGame_Id(g.getId())));
    }

    @Transactional(readOnly = true)
    public WerewolfGameResponse getGame(Long gameId) {
        WerewolfGameEntity g = gameRepository.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("对局不存在: " + gameId));
        return toResponse(g, snapshotRepository.countByGame_Id(gameId));
    }

    @Transactional
    public WerewolfSnapshotResponse saveSnapshot(Long gameId, WerewolfSnapshotSaveRequest req) throws JsonProcessingException {
        if (req == null || req.payload() == null) {
            throw new IllegalArgumentException("payload 不能为空");
        }
        WerewolfGameEntity game = gameRepository.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("对局不存在: " + gameId));

        WerewolfSnapshotEntity snap = new WerewolfSnapshotEntity();
        snap.setGame(game);
        snap.setRoundNumber(req.roundNumber());
        snap.setPhaseLabel(req.phaseLabel());
        String type = StringUtils.hasText(req.snapshotType()) ? req.snapshotType().trim() : "STATE";
        if (type.length() > 32) {
            type = type.substring(0, 32);
        }
        snap.setSnapshotType(type);
        snap.setRequestPayload(objectMapper.writeValueAsString(req.payload()));
        snapshotRepository.save(snap);

        game.markUpdated();
        gameRepository.save(game);

        return new WerewolfSnapshotResponse(
                snap.getId(),
                gameId,
                snap.getRoundNumber(),
                snap.getPhaseLabel(),
                snap.getSnapshotType(),
                snap.getRequestPayload(),
                snap.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<WerewolfSnapshotResponse> listSnapshots(Long gameId) {
        if (!gameRepository.existsById(gameId)) {
            throw new NoSuchElementException("对局不存在: " + gameId);
        }
        return snapshotRepository.findByGame_IdOrderByCreatedAtAsc(gameId).stream()
                .map(s -> new WerewolfSnapshotResponse(
                        s.getId(),
                        gameId,
                        s.getRoundNumber(),
                        s.getPhaseLabel(),
                        s.getSnapshotType(),
                        s.getRequestPayload(),
                        s.getCreatedAt()
                ))
                .toList();
    }

    @Transactional
    public WerewolfGameResponse closeGame(Long gameId, CloseGameRequest req) {
        WerewolfGameEntity g = gameRepository.findById(gameId)
                .orElseThrow(() -> new NoSuchElementException("对局不存在: " + gameId));
        g.setStatus("CLOSED");
        if (req != null && StringUtils.hasText(req.outcomeNarrative())) {
            g.setOutcomeNarrative(req.outcomeNarrative().trim());
        }
        gameRepository.save(g);
        return toResponse(g, snapshotRepository.countByGame_Id(gameId));
    }

    private WerewolfGameResponse toResponse(WerewolfGameEntity e, long snapshotCount) {
        return new WerewolfGameResponse(
                e.getId(),
                e.getSessionUuid(),
                e.getTotalPlayers(),
                e.getGameMode(),
                e.getBoardTemplateId(),
                e.getMyPlayerId(),
                e.getMyRoleHint(),
                e.getWinningObjective(),
                e.getRoleCompositionJson(),
                e.getStatus(),
                e.getOutcomeNarrative(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                snapshotCount
        );
    }
}
