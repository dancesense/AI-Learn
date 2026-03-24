# werewolf-assistant

狼人杀局势分析助手模块，基于 Spring AI 实现。

## 启动

```bash
export DASHSCOPE_API_KEY=你的有效Key
mvn -pl werewolf-assistant spring-boot:run
```

如果你在 IDE 中直接运行，也请在 Run Configuration 里设置环境变量 `DASHSCOPE_API_KEY`。
如果你更习惯 Spring 命名，也可以设置：
- `SPRING_AI_DASHSCOPE_API_KEY`
- `SPRING_AI_DASHSCOPE_CHAT_API_KEY`

启动后会输出 API Key 自检日志（仅脱敏显示），用于确认配置已生效。

## API

- `POST /werewolf/analyze`：综合分析（角色概率 + 胜率 + 话术）
- `POST /werewolf/speech-advice`：只返回“我该怎么说”
- `POST /werewolf/role-probabilities`：只返回每个玩家角色概率
- `POST /werewolf/win-rates`：只返回当前角色/阵营胜率

通用请求体示例：

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
    {"playerId": 1, "speech": "我先跳预言家，昨晚查杀2号"},
    {"playerId": 2, "speech": "1号肯定是假预，我是平民"},
    {"playerId": 3, "speech": "我先听后置位逻辑"},
    {"playerId": 4, "speech": "1号发言不够自信，2号也像在硬顶"},
    {"playerId": 5, "speech": "我偏信1号，但要看警下票型"},
    {"playerId": 6, "speech": "先出2，明天看1的金水"}
  ],
  "extraContext": "当前无人自曝女巫信息"
}
```

## 前端页面

启动模块后可直接访问：

- `http://localhost:8012/werewolf.html`

页面能力：

- 赛前配置人数与角色构成
- 按号位录入每一轮发言并实时记录
- 每次提交发言后实时更新身份概率
- 全员发言完自动输出本轮话术建议、角色概率与胜率分析

说明：

- 若你在请求中提供了已知身份（`myPlayerId` + `myRoleHint` 且不是“未知”），后端会强制把“我自己”的身份概率固定为 100%，AI 按该第一视角进行决策。
