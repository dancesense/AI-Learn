package cn.hollis.llm.mentor.werewolf.live.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "werewolf_live_session",
        uniqueConstraints = @UniqueConstraint(name = "uk_live_session_uuid", columnNames = "session_uuid"),
        indexes = {
                @Index(name = "idx_live_session_created", columnList = "started_at"),
                @Index(name = "idx_live_session_status", columnList = "status")
        }
)
public class WerewolfLiveSessionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_uuid", nullable = false, length = 64)
    private String sessionUuid;

    @Column(name = "total_players")
    private Integer totalPlayers;

    @Column(name = "game_mode", length = 128)
    private String gameMode;

    @Column(name = "my_player_id")
    private Integer myPlayerId;

    @Column(name = "my_role_hint", length = 64)
    private String myRoleHint;

    @Column(name = "current_speaker_id")
    private Integer currentSpeakerId;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        startedAt = now;
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

    public Integer getCurrentSpeakerId() {
        return currentSpeakerId;
    }

    public void setCurrentSpeakerId(Integer currentSpeakerId) {
        this.currentSpeakerId = currentSpeakerId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}

