-- ============================================================
-- 狼人杀AI助手 - 数据库建表脚本 (MySQL 8.0+)
-- 生成时间: 2026-05-17
-- ============================================================

-- 创建数据库（如需）
-- CREATE DATABASE IF NOT EXISTS werewolf_assistant
--   DEFAULT CHARACTER SET utf8mb4
--   DEFAULT COLLATE utf8mb4_unicode_ci;
--
-- USE werewolf_assistant;

-- ============================================================
-- 1. 历史对局表
-- ============================================================
DROP TABLE IF EXISTS werewolf_snapshot;
DROP TABLE IF EXISTS werewolf_game;

CREATE TABLE werewolf_game (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    session_uuid        VARCHAR(64)     NOT NULL                 COMMENT '会话唯一标识UUID',
    total_players       INT             DEFAULT NULL             COMMENT '总玩家数',
    game_mode           VARCHAR(128)    DEFAULT NULL             COMMENT '游戏模式，如12人标准局',
    board_template_id   VARCHAR(64)     DEFAULT NULL             COMMENT '板子模板ID',
    my_player_id        INT             DEFAULT NULL             COMMENT '我的座位号',
    my_role_hint        VARCHAR(64)     DEFAULT NULL             COMMENT '我的身份提示',
    winning_objective   VARCHAR(512)    DEFAULT NULL             COMMENT '胜利目标描述',
    role_composition_json LONGTEXT      DEFAULT NULL             COMMENT '角色配置JSON',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FINISHED',
    outcome_narrative   LONGTEXT        DEFAULT NULL             COMMENT '结局叙述',
    created_at          DATETIME(6)     NOT NULL                 COMMENT '创建时间',
    updated_at          DATETIME(6)     NOT NULL                 COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_session_uuid (session_uuid),
    KEY idx_werewolf_game_created (created_at),
    KEY idx_werewolf_game_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='狼人杀-历史对局表';


-- ============================================================
-- 2. 对局快照表
-- ============================================================
CREATE TABLE werewolf_snapshot (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    game_id             BIGINT          NOT NULL                 COMMENT '关联对局ID',
    round_number        INT             DEFAULT NULL             COMMENT '轮次编号',
    phase_label         VARCHAR(128)    DEFAULT NULL             COMMENT '阶段标签，如白天发言/投票',
    snapshot_type       VARCHAR(32)     NOT NULL DEFAULT 'STATE' COMMENT '快照类型: STATE/ANALYSIS',
    request_payload     LONGTEXT        NOT NULL                 COMMENT '请求载荷JSON',
    created_at          DATETIME(6)     NOT NULL                 COMMENT '创建时间',

    PRIMARY KEY (id),
    KEY idx_snapshot_game_time (game_id, created_at),
    CONSTRAINT fk_snapshot_game FOREIGN KEY (game_id)
        REFERENCES werewolf_game (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='狼人杀-对局快照表';


-- ============================================================
-- 3. 实时会话表
-- ============================================================
DROP TABLE IF EXISTS werewolf_live_event;

CREATE TABLE werewolf_live_session (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    session_uuid        VARCHAR(64)     NOT NULL                 COMMENT '会话唯一标识UUID',
    total_players       INT             DEFAULT NULL             COMMENT '总玩家数',
    game_mode           VARCHAR(128)    DEFAULT NULL             COMMENT '游戏模式',
    my_player_id        INT             DEFAULT NULL             COMMENT '我的座位号',
    my_role_hint        VARCHAR(64)     DEFAULT NULL             COMMENT '我的身份提示',
    current_speaker_id  INT             DEFAULT NULL             COMMENT '当前发言玩家ID',
    status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/FINISHED',
    started_at          DATETIME(6)     NOT NULL                 COMMENT '开始时间',
    updated_at          DATETIME(6)     NOT NULL                 COMMENT '更新时间',

    PRIMARY KEY (id),
    UNIQUE KEY uk_live_session_uuid (session_uuid),
    KEY idx_live_session_created (started_at),
    KEY idx_live_session_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='狼人杀-实时会话表';


-- ============================================================
-- 4. 实时事件表
-- ============================================================
CREATE TABLE werewolf_live_event (
    id                  BIGINT          NOT NULL AUTO_INCREMENT  COMMENT '主键ID',
    session_id          BIGINT          NOT NULL                 COMMENT '关联实时会话ID',
    day                 INT             NOT NULL DEFAULT 1       COMMENT '游戏天数',
    event_type          VARCHAR(32)     NOT NULL                 COMMENT '事件类型: PLAYER_SPEECH/AI_INSIGHT/SYSTEM_PROMPT',
    speaker_player_id   INT             DEFAULT NULL             COMMENT '发言玩家座位号',
    speaker_label       VARCHAR(64)     DEFAULT NULL             COMMENT '发言者标签，如"3号"',
    content             LONGTEXT        NOT NULL                 COMMENT '事件内容（发言文本/AI分析）',
    ai_payload          LONGTEXT        DEFAULT NULL             COMMENT 'AI附加数据JSON',
    highlight           TINYINT(1)      NOT NULL DEFAULT 0       COMMENT '是否高亮: 0否1是',
    created_at          DATETIME(6)     NOT NULL                 COMMENT '创建时间',

    PRIMARY KEY (id),
    KEY idx_live_event_session_time (session_id, created_at),
    KEY idx_live_event_type (event_type),
    KEY idx_live_event_session_day (session_id, day, created_at),
    CONSTRAINT fk_live_event_session FOREIGN KEY (session_id)
        REFERENCES werewolf_live_session (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='狼人杀-实时事件表';
