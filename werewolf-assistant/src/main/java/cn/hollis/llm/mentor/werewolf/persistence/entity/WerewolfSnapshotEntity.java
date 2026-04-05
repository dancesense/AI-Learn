package cn.hollis.llm.mentor.werewolf.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "werewolf_snapshot",
        indexes = @Index(name = "idx_snapshot_game_time", columnList = "game_id,created_at")
)
public class WerewolfSnapshotEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private WerewolfGameEntity game;

    @Column(name = "round_number")
    private Integer roundNumber;

    @Column(name = "phase_label", length = 128)
    private String phaseLabel;

    @Column(name = "snapshot_type", nullable = false, length = 32)
    private String snapshotType = "STATE";

    @Lob
    @Column(name = "request_payload", nullable = false, columnDefinition = "LONGTEXT")
    private String requestPayload;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (snapshotType == null) {
            snapshotType = "STATE";
        }
    }

    public Long getId() {
        return id;
    }

    public WerewolfGameEntity getGame() {
        return game;
    }

    public void setGame(WerewolfGameEntity game) {
        this.game = game;
    }

    public Integer getRoundNumber() {
        return roundNumber;
    }

    public void setRoundNumber(Integer roundNumber) {
        this.roundNumber = roundNumber;
    }

    public String getPhaseLabel() {
        return phaseLabel;
    }

    public void setPhaseLabel(String phaseLabel) {
        this.phaseLabel = phaseLabel;
    }

    public String getSnapshotType() {
        return snapshotType;
    }

    public void setSnapshotType(String snapshotType) {
        this.snapshotType = snapshotType;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
