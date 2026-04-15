package cn.hollis.llm.mentor.werewolf.live.entity;

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
        name = "werewolf_live_event",
        indexes = {
                @Index(name = "idx_live_event_session_time", columnList = "session_id,created_at"),
                @Index(name = "idx_live_event_type", columnList = "event_type")
        }
)
public class WerewolfLiveEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private WerewolfLiveSessionEntity session;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "speaker_player_id")
    private Integer speakerPlayerId;

    @Column(name = "speaker_label", length = 64)
    private String speakerLabel;

    @Lob
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name = "ai_payload", columnDefinition = "LONGTEXT")
    private String aiPayload;

    @Column(name = "highlight", nullable = false)
    private Boolean highlight = Boolean.FALSE;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (highlight == null) {
            highlight = Boolean.FALSE;
        }
    }

    public Long getId() {
        return id;
    }

    public WerewolfLiveSessionEntity getSession() {
        return session;
    }

    public void setSession(WerewolfLiveSessionEntity session) {
        this.session = session;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Integer getSpeakerPlayerId() {
        return speakerPlayerId;
    }

    public void setSpeakerPlayerId(Integer speakerPlayerId) {
        this.speakerPlayerId = speakerPlayerId;
    }

    public String getSpeakerLabel() {
        return speakerLabel;
    }

    public void setSpeakerLabel(String speakerLabel) {
        this.speakerLabel = speakerLabel;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getAiPayload() {
        return aiPayload;
    }

    public void setAiPayload(String aiPayload) {
        this.aiPayload = aiPayload;
    }

    public Boolean getHighlight() {
        return highlight;
    }

    public void setHighlight(Boolean highlight) {
        this.highlight = highlight;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

