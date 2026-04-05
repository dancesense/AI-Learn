# werewolf-assistant

狼人杀局势分析助手模块，基于 Spring AI + 本地蒙特卡洛推理实现（覆盖「决策大脑 / 话术教练 / 心理博弈 / 复盘」产品骨架）。

## MySQL 历史存储（本机默认）

1. 安装并启动 MySQL（默认端口 **3306**）。
2. 执行建库与建表脚本（也可依赖 JPA `ddl-auto: update` 自动建表，脚本用于对齐生产/审计）：

`werewolf-assistant/src/main/resources/db/mysql/schema.sql`

3. 配置账号密码（默认与本机常见 root/root 一致，可通过环境变量覆盖）：

| 环境变量 | 默认值 |
|----------|--------|
| `MYSQL_USER` | `root` |
| `MYSQL_PASSWORD` | `root` |

连接 URL 在 `application.yml`：`jdbc:mysql://localhost:3306/werewolf_assistant?...`

### 历史 API（落库）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/werewolf/history/games` | 创建对局（body 见下） |
| GET | `/werewolf/history/games` | 分页列表 `?page=0&size=20` |
| GET | `/werewolf/history/games/{id}` | 对局详情 |
| POST | `/werewolf/history/games/{id}/snapshots` | 保存局势快照（完整 `WerewolfAnalysisRequest` JSON） |
| GET | `/werewolf/history/games/{id}/snapshots` | 该对局全部快照 |
| POST | `/werewolf/history/games/{id}/close` | 结束对局，可选 `{"outcomeNarrative":"..."}` |

前端 `werewolf.html`：**开始对局** 时会创建 `werewolf_game`；**每轮完整分析结束** 会写入一条 `werewolf_snapshot`。

### 实体与表

- 实体：`cn.hollis.llm.mentor.werewolf.persistence.entity.WerewolfGameEntity`、`WerewolfSnapshotEntity`
- 表：`werewolf_game`、`werewolf_snapshot`

## 启动

```bash
export DASHSCOPE_API_KEY=你的有效Key
export MYSQL_PASSWORD=你的MySQL密码
mvn -pl werewolf-assistant spring-boot:run
```

如果你在 IDE 中直接运行，也请在 Run Configuration 里设置环境变量 `DASHSCOPE_API_KEY`。
如果你更习惯 Spring 命名，也可以设置：
- `SPRING_AI_DASHSCOPE_API_KEY`
- `SPRING_AI_DASHSCOPE_CHAT_API_KEY`

启动后会输出 API Key 自检日志（仅脱敏显示），用于确认配置已生效。

## API

- `POST /werewolf/analyze`：综合分析（角色概率 + 胜率 + 话术）
- `POST /werewolf/speech-advice`：话术 + **情绪/演技指导 + 防御/进攻/表水模板**（模块二）
- `POST /werewolf/role-probabilities`：每个玩家角色概率（LLM）
- `POST /werewolf/win-rates`：阵营/角色胜率
- `POST /werewolf/monte-carlo?samples=4000`：**蒙特卡洛边缘概率**（板子 multiset + 硬约束，均匀采样；可与 LLM 对照）（模块一）
- `POST /werewolf/psychology-coach`：观察清单、施压追问、反应预测与应对（模块三）
- `POST /werewolf/post-game-review`：赛后复盘报告（模块四）

### 与产品规格的对应关系（当前实现 vs 规划）

| 模块 | 已实现 | 后续可增强 |
|------|--------|------------|
| 模块一 实时概率 | LLM 概率 + 投票/技能/遗言进 Prompt；**MC 硬约束边缘分布**；前端热力条 | 发言 NLP 似然、投票一致性加权 MC；关键词自动抽取；概率时间序列存储 |
| 模块二 话术/演技 | 结构化 `emotionGuide` + 三类话术模板 | 更细阶段机（警上/归票/遗言）；板子专属技能台词库 |
| 模块三 心理博弈 | LLM 输出观察清单与施压问句（辅以用户录入） | 与「谎言特征库」规则引擎结合；语速/停顿需人工或录音分析再接 |
| 模块四 复盘 | 全量上下文 + `gameOutcomeNarrative` 生成复盘 | 服务端会话存档、时间轴 UI、评分卡、历史对局学习 |

### 请求体示例（含扩展字段）

```json
{
  "totalPlayers": 6,
  "gameMode": "6人标准局",
  "phase": "第一天白天发言",
  "myPlayerId": 3,
  "myRoleHint": "好人视角",
  "winningObjective": "让狼人先出局",
  "roleComposition": {
    "狼人": 2,
    "平民": 2,
    "预言家": 1,
    "女巫": 1
  },
  "speeches": [
    {"playerId": 1, "speech": "我先跳预言家，昨晚查杀2号"}
  ],
  "extraContext": "昨夜平安夜",
  "deadPlayers": [2],
  "revealedIdentities": {"2": "平民"},
  "knownWerewolfPlayers": [5],
  "boardTemplateId": "std6",
  "voteRecords": [
    {"round": 1, "voteType": "放逐投票", "voterId": 3, "targetId": 1}
  ],
  "skillEvents": [
    {"nightOrDay": 1, "phaseTag": "狼刀", "actionType": "刀人", "actorPlayerId": null, "targetPlayerIds": [4], "details": ""}
  ],
  "lastWordRecords": [
    {"playerId": 2, "roundOrDay": 1, "content": "我是民走的"}
  ],
  "observedSignals": [
    {"playerId": 5, "category": "语态", "description": "答关键问题时停顿约3秒"}
  ],
  "gameOutcomeNarrative": "好人胜；第三天误票猎人。"
}
```

说明：

- 已知身份（`myPlayerId + myRoleHint`、`revealedIdentities`、`knownWerewolfPlayers`）在 LLM 结果中会**强制 100%**；蒙特卡洛同样作为**硬约束**参与采样。
- `boardTemplateId` 仅作文本标签进入 Prompt；前端下拉会同步标准 6/9/12 人角色数量。
- `gameOutcomeNarrative` 主要供 **赛后复盘** 使用；其他接口也会带上但不强制填写。

## 前端页面

启动模块后访问：`http://localhost:8012/werewolf.html`

- 6 / 9 / 12 人、板子模板、死亡与翻牌、已知狼同伴
- **投票 / 技能 / 遗言** 录入并入请求
- LLM 身份表 + **蒙特卡洛热力条**（红狼面 / 绿非狼倾向）
- **话术 + 情绪演技 + 模板**（模块二）
- **心理博弈**一键生成（模块三）
- **赛后复盘**（模块四）
