package cn.hollis.llm.mentor.werewolf.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "werewolf_game",
        uniqueConstraints = @UniqueConstraint(name = "uk_session_uuid", columnNames = "session_uuid"),
        indexes = {
                @Index(name = "idx_werewolf_game_created", columnList = "created_at"),
                @Index(name = "idx_werewolf_game_status", columnList = "status")
        }
)
public class WerewolfGameEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_uuid", nullable = false, length = 64)
    private String sessionUuid;

    @Column(name = "total_players")
    private Integer totalPlayers;

    @Column(name = "game_mode", length = 128)
    private String gameMode;

    @Column(name = "board_template_id", length = 64)
    private String boardTemplateId;

    @Column(name = "my_player_id")
    private Integer myPlayerId;

    @Column(name = "my_role_hint", length = 64)
    private String myRoleHint;

    @Column(name = "winning_objective", length = 512)
    private String winningObjective;

    @Lob
    @Column(name = "role_composition_json")
    private String roleCompositionJson;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Lob
    @Column(name = "outcome_narrative")
    private String outcomeNarrative;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "game")
    private List<WerewolfSnapshotEntity> snapshots = new ArrayList<>();

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getSessionUuid() {
        return sessionUuid;
    }

    public void setSessionUuid(String sessionUuid) {
        this.sessionUuid = sessionUuid;
    }

    public Integer getTotalPlayers() {
        return totalPlayers;
    }

    public void setTotalPlayers(Integer totalPlayers) {
        this.totalPlayers = totalPlayers;
    }

    public String getGameMode() {
        return gameMode;
    }

    public void setGameMode(String gameMode) {
        this.gameMode = gameMode;
    }

    public String getBoardTemplateId() {
        return boardTemplateId;
    }

    public void setBoardTemplateId(String boardTemplateId) {
        this.boardTemplateId = boardTemplateId;
    }

    public Integer getMyPlayerId() {
        return myPlayerId;
    }

    public void setMyPlayerId(Integer myPlayerId) {
        this.myPlayerId = myPlayerId;
    }

    public String getMyRoleHint() {
        return myRoleHint;
    }

    public void setMyRoleHint(String myRoleHint) {
        this.myRoleHint = myRoleHint;
    }

    public String getWinningObjective() {
        return winningObjective;
    }

    public void setWinningObjective(String winningObjective) {
        this.winningObjective = winningObjective;
    }

    public String getRoleCompositionJson() {
        return roleCompositionJson;
    }

    public void setRoleCompositionJson(String roleCompositionJson) {
        this.roleCompositionJson = roleCompositionJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOutcomeNarrative() {
        return outcomeNarrative;
    }

    public void setOutcomeNarrative(String outcomeNarrative) {
        this.outcomeNarrative = outcomeNarrative;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public List<WerewolfSnapshotEntity> getSnapshots() {
        return snapshots;
    }

    /** 在仅追加快照时手动刷新对局更新时间 */
    public void markUpdated() {
        this.updatedAt = LocalDateTime.now();
    }
}
