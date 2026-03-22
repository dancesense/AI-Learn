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

- `POST /werewolf/analyze`

请求示例：

```json
{
  "totalPlayers": 6,
  "gameMode": "6人标准局",
  "phase": "第一天白天发言",
  "myPlayerId": 3,
  "myRoleHint": "好人视角",
  "winningObjective": "让狼人先出局",
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
