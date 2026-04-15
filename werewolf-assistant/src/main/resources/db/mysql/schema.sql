-- 狼人杀助手历史对局库（MySQL 8+）
-- 手工建库（若尚未创建）：
CREATE DATABASE IF NOT EXISTS werewolf_assistant
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE werewolf_assistant;

-- ----------------------------
-- 对局主表：一局游戏一条记录
-- ----------------------------
CREATE TABLE IF NOT EXISTS werewolf_game (
    id                  BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    session_uuid        VARCHAR(64)     NOT NULL COMMENT '会话/客户端标识，可续局',
    total_players       INT             NULL COMMENT '总人数',
    game_mode           VARCHAR(128)    NULL COMMENT '模式名称',
    board_template_id   VARCHAR(64)     NULL COMMENT '板子模板ID',
    my_player_id        INT             NULL COMMENT '我是几号',
    my_role_hint        VARCHAR(64)     NULL COMMENT '我的身份提示',
    winning_objective   VARCHAR(512)    NULL COMMENT '胜利目标描述',
    role_composition_json TEXT          NULL COMMENT '角色构成 JSON 字符串，如 {"狼人":2,"平民":2}',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE=进行中 CLOSED=已结束',
    outcome_narrative   TEXT            NULL COMMENT '赛后结果叙述',
    created_at          DATETIME(6)     NOT NULL COMMENT '创建时间',
    updated_at          DATETIME(6)     NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_session_uuid (session_uuid),
    KEY idx_werewolf_game_created (created_at),
    KEY idx_werewolf_game_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='狼人杀对局';

-- ----------------------------
-- 快照表：每轮/每次分析保存完整请求体 JSON，便于复盘与概率曲线
-- ----------------------------
CREATE TABLE IF NOT EXISTS werewolf_snapshot (
    id              BIGINT          NOT NULL AUTO_INCREMENT COMMENT '主键',
    game_id         BIGINT          NOT NULL COMMENT '对局ID',
    round_number    INT             NULL COMMENT '轮次（可选）',
    phase_label     VARCHAR(128)    NULL COMMENT '阶段描述（可选）',
    snapshot_type   VARCHAR(32)     NOT NULL DEFAULT 'STATE' COMMENT 'STATE=局势快照 ANALYSIS=分析后等',
    request_payload LONGTEXT        NOT NULL COMMENT 'WerewolfAnalysisRequest 完整 JSON',
    created_at      DATETIME(6)     NOT NULL COMMENT '写入时间',
    PRIMARY KEY (id),
    KEY idx_snapshot_game_time (game_id, created_at),
    CONSTRAINT fk_werewolf_snapshot_game
        FOREIGN KEY (game_id) REFERENCES werewolf_game (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对局局势快照';

-- ----------------------------
-- live session table
-- ----------------------------
CREATE TABLE IF NOT EXISTS werewolf_live_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_uuid        VARCHAR(64)     NOT NULL,
    total_players       INT             NULL,
    game_mode           VARCHAR(128)    NULL,
    my_player_id        INT             NULL,
    my_role_hint        VARCHAR(64)     NULL,
    current_speaker_id  INT             NULL,
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    started_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_live_session_uuid (session_uuid),
    KEY idx_live_session_created (started_at),
    KEY idx_live_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- live event table
-- ----------------------------
CREATE TABLE IF NOT EXISTS werewolf_live_event (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    session_id          BIGINT          NOT NULL,
    event_type          VARCHAR(32)     NOT NULL,
    speaker_player_id   INT             NULL,
    speaker_label       VARCHAR(64)     NULL,
    content             LONGTEXT        NOT NULL,
    ai_payload          LONGTEXT        NULL,
    highlight           TINYINT(1)      NOT NULL DEFAULT 0,
    created_at          DATETIME(6)     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_live_event_session_time (session_id, created_at),
    KEY idx_live_event_type (event_type),
    CONSTRAINT fk_live_event_session
        FOREIGN KEY (session_id) REFERENCES werewolf_live_session (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
