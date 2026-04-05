package cn.hollis.llm.mentor.agent.agent.websearch;

import cn.hollis.llm.mentor.agent.agent.BaseAgent;
import cn.hollis.llm.mentor.agent.entity.record.AgentState;
import cn.hollis.llm.mentor.agent.entity.record.RoundMode;
import cn.hollis.llm.mentor.agent.entity.record.RoundState;
import cn.hollis.llm.mentor.agent.entity.record.SearchResult;
import cn.hollis.llm.mentor.agent.prompts.ReactAgentPrompts;
import cn.hollis.llm.mentor.agent.entity.AiSession;
import cn.hollis.llm.mentor.agent.entity.vo.SaveQuestionRequest;
import cn.hollis.llm.mentor.agent.entity.vo.UpdateAnswerRequest;
import cn.hollis.llm.mentor.agent.service.AgentTaskManager;
import cn.hollis.llm.mentor.agent.service.AiSessionService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.util.CollectionUtils;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSearch React Agent
 * 基于联网搜索的智能问答Agent
 **
 * ---
 *
 * ## `doOnNext` 和 `doOnComplete` 的触发时机
 *
 * ```
 * LLM 流式输出（Flux<ChatResponse>）：
 *
 *   chunk1 → doOnNext(processChunk)
 *   chunk2 → doOnNext(processChunk)
 *   chunk3 → doOnNext(processChunk)
 *   ...
 *   模型输出完毕 → doOnComplete(finishRound)
 * ```
 *
 * **`doOnNext`** = 模型每吐出一个 token（chunk），就触发一次，没错。
 *
 * **`doOnComplete`** = 模型**这一轮**全部输出完毕后触发一次。注意是"这一轮"，不是整个会话结束。
 *
 * ---
 *
 * ## 关于你说的"发现要用工具"的时机
 *
 * 这里有个细节要纠正一下。**不是模型输出完才发现，而是模型一边输出就一边能发现**：
 *
 * ```java
 * // processChunk 里，每个 chunk 进来都检查
 * if (tc != null && !tc.isEmpty()) {
 *     state.mode = RoundMode.TOOL_CALL;  // 一旦发现 toolCall，立刻切换模式
 *     mergeToolCall(state, incoming);
 *     return;  // 直接 return，不往 sink 里推文本
 * }
 *
 * // 没有 toolCall 才推文本
 * if (text != null) {
 *     sink.tryEmitNext(createTextResponse(text));
 * }
 * ```
 *
 * 也就是说：**模型输出 toolCall chunk 的那一刻，`state.mode` 就已经切成 `TOOL_CALL` 了**，文本也停止向前端推送了。只是工具调用的**参数是碎片化的**，所以要等 `doOnComplete` 之后才能拿到完整参数去执行。
 *
 * 时序更准确的描述：
 *
 * ```
 * chunk1: text="我来查" → processChunk → 推给前端
 * chunk2: toolCall{id:"001", args:"{\"quer"} → processChunk → mode=TOOL_CALL，停止推文本
 * chunk3: toolCall{id:"001", args:"y\":\"北京"} → mergeToolCall 拼接
 * chunk4: toolCall{id:"001", args:"天气\"}"} → mergeToolCall 拼接
 * 模型输出完毕 → doOnComplete(finishRound) → 此时才有完整参数 → executeToolCalls
 * ```
 *
 * ---
 *
 * ## 关于 `executeToolCalls` 内部是不是多轮循环
 *
 * 这里也要纠正一下，`executeToolCalls` 本身**只执行一次当前轮的工具**，多轮逻辑是靠**递归调用 `scheduleRound`** 实现的：
 *
 * ```java
 * // executeToolCalls 执行完后的回调
 * executeToolCalls(sink, state.toolCalls, messages, hasSentFinalResult, state, agentState, () -> {
 *     if (!hasSentFinalResult.get()) {
 *         scheduleRound(...);  // ← 工具执行完，再发起下一轮 LLM 调用
 *     }
 * });
 * ```
 *
 * 整个多轮结构其实是这样的：
 *
 * ```
 * scheduleRound()           第1轮 LLM
 *     ↓ doOnComplete
 * finishRound()
 *     ↓ 有工具调用
 * executeToolCalls()        执行工具（只执行这一轮的）
 *     ↓ 全部执行完（回调）
 * scheduleRound()           第2轮 LLM（带上工具结果）
 *     ↓ doOnComplete
 * finishRound()
 *     ↓ 还有工具调用
 * executeToolCalls()
 *     ↓
 * scheduleRound()           第3轮 LLM
 *     ↓ doOnComplete
 * finishRound()
 *     ↓ 没有工具调用了 OR 达到 maxRounds
 *   直接输出参考 + 推荐 → sink.complete()
 * ```
 *
 * 所以**多轮是靠 `finishRound → executeToolCalls → scheduleRound` 这个链条递归驱动的**，不是在 `executeToolCalls` 内部循环。
 *
 * ---
 *
 * ## 你的理解总结
 *
 * | 你的理解 | 是否正确 |
 * |---|---|
 * | `doOnNext` = 模型还在输出就触发 | ✅ 正确 |
 * | 不需要工具 → 直接输出推荐和引用 | ✅ 正确 |
 * | 发现要用工具是在模型输出完毕之后 | ⚠️ 半对。发现是在输出中途，但执行是在输出完毕后 |
 * | `executeToolCalls` 内部是多轮循环直到结束 | ❌ 不是。它只执行当前轮的工具，多轮是靠递归 `scheduleRound` 驱动的 |
 */
@Slf4j
public class WebSearchReactAgent extends BaseAgent {

    private ChatClient chatClient;
    private final List<ToolCallback> tools;
    private final String systemPrompt;
    private int maxRounds;
    private final List<Advisor> advisors;
    private final int maxReflectionRounds;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public WebSearchReactAgent(String name, ChatModel chatModel, List<ToolCallback> tools, String systemPrompt, int maxRounds,
                               ChatMemory chatMemory, List<Advisor> advisors, int maxReflectionRounds,
                               AiSessionService sessionService, AgentTaskManager taskManager) {
        super(name, chatModel, "websearch");
        this.tools = tools;
        this.systemPrompt = systemPrompt;
        this.maxRounds = maxRounds;
        this.advisors = advisors;
        this.maxReflectionRounds = maxReflectionRounds;
        this.chatMemory = chatMemory;
        this.sessionService = sessionService;
        this.taskManager = taskManager;

        // 初始化工具记录集合
        this.usedTools = new HashSet<>();

        initChatClient();

        if (this.chatClient == null) {
            throw new IllegalStateException("ChatClient 初始化失败！");
        }
    }

    private void initChatClient() {
        try {
            ToolCallingChatOptions toolOptions = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools)
                    .internalToolExecutionEnabled(false)
                    .build();

            ChatClient.Builder builder = ChatClient.builder(chatModel);
            if (!CollectionUtils.isEmpty(advisors)) {
                builder.defaultAdvisors(advisors);
            }
            this.chatClient = builder.defaultOptions(toolOptions).defaultToolCallbacks(tools).build();
        } catch (Exception e) {
            throw new RuntimeException("ChatClient 初始化失败：" + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> execute(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    /**
     * 流式输出
     */
    public Flux<String> stream(String question) {
        return streamInternal(null, question);
    }

    /**
     * 带会话记忆的流式输出
     */
    public Flux<String> stream(String conversationId, String question) {
        return streamInternal(conversationId, question);
    }

    private Flux<String> streamInternal(String conversationId, String question) {
        List<Message> messages = Collections.synchronizedList(new ArrayList<>());
        boolean useMemory = conversationId != null && chatMemory != null;

        // 检查是否已有任务在执行
        Flux<String> checkResult = checkRunningTask(conversationId);
        if (checkResult != null) {
            return checkResult;
        }

        // 初始化计时器
        initTimers();
        clearUsedTools();

        //创建一个可以发送多个数据的 Sink，对应 Flux（多值流）。如果是 Sinks.one() 就对应 Mono（单值）。
        //.unicast()
        //单播，意思是这个 Sink 只允许一个订阅者消费数据。如果有第二个人来订阅，会直接报错。
        //对应的还有 multicast()，允许多个订阅者同时消费同一份数据。
        //在流式对话场景里，一个会话对应一个 SSE 连接，只有一个前端在消费，所以用 unicast 就够了。
        //背压策略，选择缓冲模式。意思是如果消费者处理不过来，数据先放进缓冲区排队等待，不丢弃。
        Sinks.Many<String> sink = Sinks.many().unicast().onBackpressureBuffer();

        // 注册任务到管理器
        AgentTaskManager.TaskInfo taskInfo = registerTask(conversationId, sink);
        if (taskInfo == null && conversationId != null && taskManager != null) {
            return Flux.error(new IllegalStateException("该会话正在执行中，请稍后再试"));
        }

        // ===== 加载 System Prompt（始终放在最开始）=====
        messages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
            messages.add(new SystemMessage(systemPrompt));
        }

        // ===== 加载历史记忆 =====
        loadChatHistory(conversationId, messages, true, true);

        messages.add(new UserMessage("<question>" + question + "</question>"));
        currentQuestion = question;

        // 添加记忆并保存到数据库
        if (sessionService != null) {
            // 保存用户问题到数据库
            AiSession savedSession = sessionService.saveQuestion(
                    SaveQuestionRequest.builder()
                            .sessionId(conversationId)
                            .question(question)
                            .build()
            );
            currentSessionId = savedSession.getId();
        }

        // 迭代轮次
        AtomicLong roundCounter = new AtomicLong(0);
        // 是否发送最终结果标记位
        AtomicBoolean hasSentFinalResult = new AtomicBoolean(false);

        hasSentFinalResult.set(false);
        roundCounter.set(0);

        // 收集最终答案（纯文本），存储memory
        StringBuilder finalAnswerBuffer = new StringBuilder();
        // 收集思考过程
        StringBuilder thinkingBuffer = new StringBuilder();

        AgentState agentState = new AgentState();

        scheduleRound(messages, sink, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer);

        return sink.asFlux()
                .doOnNext(chunk -> {
                    recordFirstResponse();
                    // 解析 JSON，如果是 type=text，则只拼接 content；如果是 type=thinking，则拼接 thinking
                    try {
                        JSONObject json = JSON.parseObject(chunk);
                        String type = json.getString("type");
                        if ("text".equals(type)) {
                            finalAnswerBuffer.append(json.getString("content"));
                        } else if ("thinking".equals(type)) {
                            thinkingBuffer.append(json.getString("content"));
                        }
                    } catch (Exception e) {
                        // 解析失败，直接拼接
                        finalAnswerBuffer.append(chunk);
                    }
                })
                .doOnCancel(() -> {
                    hasSentFinalResult.set(true);
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                })
                .doFinally(signalType -> {
                    log.info("最终答案: {}", finalAnswerBuffer);
                    log.info("思考过程: {}", thinkingBuffer);

                    // 保存结果到会话
                    saveSessionResult(conversationId, finalAnswerBuffer, thinkingBuffer, agentState);

                    // 流结束时移除任务
                    if (taskManager != null) {
                        taskManager.stopTask(conversationId);
                    }
                });
    }

    /**
     * 保存会话结果
     */
    private void saveSessionResult(String conversationId, StringBuilder finalAnswerBuffer, StringBuilder thinkingBuffer, AgentState agentState) {
        if (sessionService != null && currentSessionId != null && finalAnswerBuffer.length() > 0) {
            long totalResponseTime = getTotalResponseTime();
            String toolsStr = getUsedToolsString();
            String referenceJson = "";
            if (!agentState.searchResults.isEmpty()) {
                referenceJson = createReferenceResponse(JSON.toJSONString(agentState.searchResults));
            }
            UpdateAnswerRequest request = UpdateAnswerRequest.builder()
                    .id(currentSessionId)
                    .answer(finalAnswerBuffer.toString())
                    .thinking(thinkingBuffer.toString())
                    .tools(toolsStr)
                    .reference(referenceJson)
                    .recommend(currentRecommendations)
                    .firstResponseTime(firstResponseTime)
                    .totalResponseTime(totalResponseTime)
                    .build();
            sessionService.updateAnswer(request);
            log.info("结果已保存到会话: sessionId={}", conversationId);
        }
    }

    private void scheduleRound(List<Message> messages, Sinks.Many<String> sink, AtomicLong roundCounter, AtomicBoolean hasSentFinalResult,
                               StringBuilder finalAnswerBuffer, boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {
        // 轮次+1
        roundCounter.incrementAndGet();
        RoundState state = new RoundState();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                //这行代码的意思是：把后续的操作切换到 boundedElastic 线程池上执行。
                //先理解为什么需要切换线程。模型流式输出的数据是在某个 Reactor 的调度线程上产生的，这个线程是非阻塞线程，不允许在上面做耗时操作。
                // 但是 doOnNext 里的 processChunk 可能涉及工具调用、数据库操作这类阻塞 IO，如果在非阻塞线程上做这些事，会卡住整个响应式管道。
                //publishOn 的作用就是在这行代码之后，把所有操作都切换到 boundedElastic 线程池，这个线程池专门用来处理阻塞 IO 任务，线程数量会根据需要动态扩展。
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> processChunk(chunk, sink, state))
                .doOnComplete(() -> finishRound(messages, sink, state, roundCounter, hasSentFinalResult, finalAnswerBuffer, useMemory, conversationId, agentState, thinkingBuffer))
                .doOnError(err -> {
                    if (!hasSentFinalResult.get()) {
                        hasSentFinalResult.set(true);
                        sink.tryEmitError(err);
                    }
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    private void processChunk(ChatResponse chunk, Sinks.Many<String> sink, RoundState state) {
        if (chunk == null || chunk.getResult() == null ||
                chunk.getResult().getOutput() == null) {
            return;
        }

        Generation gen = chunk.getResult();
        String text = gen.getOutput().getText();
        List<AssistantMessage.ToolCall> tc = gen.getOutput().getToolCalls();

        // 一旦发现 tool_call，立即进入 TOOL_CALL 模式
        // 马上开始记录TOOL调用的信息 先进行组装
        if (tc != null && !tc.isEmpty()) {
            state.mode = RoundMode.TOOL_CALL;

            for (AssistantMessage.ToolCall incoming : tc) {
                mergeToolCall(state, incoming);
            }
            return;
        }

        // 还没出现 tool_call，发送并缓存文本
        if (text != null) {
            sink.tryEmitNext(createTextResponse(text));
            state.textBuffer.append(text);
        }
    }


    /**
     * 这个方法是在处理流式工具调用的分块合并问题。
     * 原因是模型流式输出时，一个工具调用的参数不是一次性完整返回的，而是像 token 一样分多个 chunk 流式传输过来：
     * json// 第一个 chunk
     * {"id": "call_123", "name": "get_weather", "arguments": "{\"ci"}
     *
     * // 第二个 chunk
     * {"id": "call_123", "name": "", "arguments": "ty\":\"Bei"}
     *
     * // 第三个 chunk
     * {"id": "call_123", "name": "", "arguments": "jing\"}"}
     * 三个 chunk 合并之后才是完整的工具调用参数 {"city":"Beijing"}。
     * @param state
     * @param incoming
     */
    private void mergeToolCall(RoundState state, AssistantMessage.ToolCall incoming) {
        // 遍历已有的 toolcalls，找有没有相同 id 的
        for (int i = 0; i < state.toolCalls.size(); i++) {
            AssistantMessage.ToolCall existing = state.toolCalls.get(i);

            if (existing.id().equals(incoming.id())) {
                // 找到了相同 id，说明是同一个工具调用的后续分块
                // 把新来的 arguments 拼接到已有的 arguments 后面
                String mergedArgs = Objects.toString(existing.arguments(), "")
                        + Objects.toString(incoming.arguments(), "");

                // 用合并后的版本替换原来的
                state.toolCalls.set(i,
                        new AssistantMessage.ToolCall(existing.id(), "function", existing.name(), mergedArgs)
                );
                return;  // 合并完退出
            }
        }

        // 没找到相同 id，说明是全新的工具调用，直接加进去
        state.toolCalls.add(incoming);
    }

    /**
     * 轮次结束处理工具调用
     */
    private void finishRound(List<Message> messages, Sinks.Many<String> sink, RoundState state,
                             AtomicLong roundCounter, AtomicBoolean hasSentFinalResult, StringBuilder finalAnswerBuffer,
                             boolean useMemory, String conversationId, AgentState agentState, StringBuilder thinkingBuffer) {

        // 如果整轮都没有 tool_call，才是最终答案
        if (state.getMode() != RoundMode.TOOL_CALL) {
            String referenceJson = "";
            String toolsStr = getUsedToolsString();
            String finalText = state.textBuffer.toString();

            // 输出参考链接
            if (!agentState.searchResults.isEmpty()) {
                String reference = JSON.toJSONString(agentState.searchResults);
                referenceJson = createReferenceResponse(reference);
                sink.tryEmitNext(referenceJson);
            }

            // 输出推荐问题
            if (enableRecommendations) {
                String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                if (recommendations != null) {
                    currentRecommendations = recommendations; // 保存用于数据库存储
                    String recommendJson = createRecommendResponse(recommendations);
                    sink.tryEmitNext(recommendJson);
                }
            }

            sink.tryEmitComplete();
            hasSentFinalResult.set(true);
            return;
        }

        // TOOL_CALL
        AssistantMessage assistantMsg = AssistantMessage.builder().toolCalls(state.toolCalls).build();
        messages.add(assistantMsg);

        // 超过最大执行轮次 直接输出
        if (maxRounds > 0 && roundCounter.get() >= maxRounds) {
            forceFinalStream(messages, sink, hasSentFinalResult, state, conversationId, useMemory, agentState, thinkingBuffer);
            return;
        }

        // 如果有工具调用 就先循环吧工具都调用掉
        executeToolCalls(sink, state.toolCalls, messages, hasSentFinalResult, state, agentState, () -> {
            if (!hasSentFinalResult.get()) {
                scheduleRound(messages, sink, roundCounter,
                        hasSentFinalResult, finalAnswerBuffer,
                        useMemory, conversationId, agentState, thinkingBuffer);
            }
        });
    }

    private void forceFinalStream(List<Message> messages, Sinks.Many<String> sink, AtomicBoolean hasSentFinalResult, RoundState state,
                                  String conversationId, boolean useMemory, AgentState agentState, StringBuilder thinkingBuffer) {
        // 创建新的消息列表，确保系统提示词在最前面
        List<Message> newMessages = new ArrayList<>();

        // 添加系统提示词
        newMessages.add(new SystemMessage(ReactAgentPrompts.getWebSearchPrompt()));
        if (StringUtils.isNotBlank(systemPrompt)) {
            newMessages.add(new SystemMessage(systemPrompt));
        }

        // 添加原有消息（跳过系统消息）
        for (Message msg : messages) {
            if (!(msg instanceof SystemMessage)) {
                newMessages.add(msg);
            }
        }

        // 添加限制提示
        newMessages.add(new UserMessage("""
                你已达到最大推理轮次限制。
                请基于当前已有的上下文信息，
                直接给出最终答案。
                禁止再调用任何工具。
                如果信息不完整，请合理总结和说明。
                """));

        // 替换原消息列表
        messages.clear();
        messages.addAll(newMessages);

        // 收集最终文本
        StringBuilder finalTextBuffer = new StringBuilder();

        Disposable disposable = chatClient.prompt()
                .messages(messages)
                .stream()
                .chatResponse()
                .publishOn(Schedulers.boundedElastic())
                .doOnNext(chunk -> {
                    if (chunk == null || chunk.getResult() == null || chunk.getResult().getOutput() == null) {
                        return;
                    }

                    String text = chunk.getResult()
                            .getOutput()
                            .getText();

                    if (text != null && !hasSentFinalResult.get()) {
                        sink.tryEmitNext(createTextResponse(text));
                        finalTextBuffer.append(text);
                    }
                })
                .doOnComplete(() -> {
                    String referenceJson = "";
                    String finalText = finalTextBuffer.toString();

                    // 输出参考链接
                    if (!agentState.searchResults.isEmpty()) {
                        String reference = JSON.toJSONString(agentState.searchResults);
                        referenceJson = createReferenceResponse(reference);
                        sink.tryEmitNext(referenceJson);
                    }

                    // 输出推荐问题
                    if (enableRecommendations) {
                        String recommendations = generateRecommendations(conversationId, currentQuestion, finalText);
                        if (recommendations != null) {
                            currentRecommendations = recommendations; // 保存用于数据库存储
                            String recommendJson = createRecommendResponse(recommendations);
                            sink.tryEmitNext(recommendJson);
                        }
                    }

                    hasSentFinalResult.set(true);
                    // 消息全部发完信号
                    sink.tryEmitComplete();
                })
                .doOnError(err -> {
                    hasSentFinalResult.set(true);
                    sink.tryEmitError(err);
                })
                .subscribe();

        // 保存Disposable到任务管理器
        if (conversationId != null && taskManager != null) {
            taskManager.setDisposable(conversationId, disposable);
        }
    }

    /**
     * 执行工具调用
     * @param sink
     * @param toolCalls
     * @param messages
     * @param hasSentFinalResult
     * @param state
     * @param agentState
     * @param onComplete
     */
    private void executeToolCalls(Sinks.Many<String> sink, List<AssistantMessage.ToolCall> toolCalls, List<Message> messages, AtomicBoolean hasSentFinalResult, RoundState state, AgentState agentState, Runnable onComplete) {
        AtomicInteger completedCount = new AtomicInteger(0);
        int totalToolCalls = toolCalls.size();

        for (AssistantMessage.ToolCall tc : toolCalls) {
            //Schedulers.boundedElastic().schedule() 是 Reactor 的线程池调度，相当于 ExecutorService.submit()
            // 。多个工具会同时提交到线程池，并行跑，不是一个一个串行等待。
            Schedulers.boundedElastic().schedule(() -> {
                //如果在工具还没执行完时用户已经取消了（hasSentFinalResult = true），就不再真正执行工具，直接计数然后退出。这是一个提前短路的保护机制。
                if (hasSentFinalResult.get()) {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }

                String toolName = tc.name();
                String argsJson = tc.arguments();

                ToolCallback callback = findTool(toolName);
                if (callback == null) {
                    addErrorToolResponse(messages, tc, "工具未找到：" + toolName);
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                    return;
                }
                if (toolName.contains("search")) {
                    JSONObject args = JSON.parseObject(argsJson);
                    String query = (String) args.get("query");
                    // 发送 thinking 消息，表示正在搜索相关信息
                    String queryThink = StringUtils.isNotBlank(query) ? "🔍 正在搜索信息: " + query + "\n" : "🔍 正在搜索相关信息\n";
                    sink.tryEmitNext(createThinkingResponse(queryThink));
                }

                try {
                    Object result = callback.call(argsJson);
                    ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                            tc.id(), toolName, result.toString());
                    messages.add(ToolResponseMessage.builder()
                            .responses(List.of(tr))
                            .build());

                    // 记录使用的工具
                    recordUsedTool(toolName);

                    // 解析 tavily 搜索结果
                    if (toolName.contains("tavily")) {
                        parseSearchResult(result.toString(), agentState);
                    }

                } catch (Exception ex) {
                    addErrorToolResponse(messages, tc, "工具执行失败：" + ex.getMessage());
                } finally {
                    completeToolCall(completedCount, totalToolCalls, onComplete);
                }
            });
        }
    }

    private void completeToolCall(AtomicInteger completedCount, int total, Runnable onComplete) {
        int current = completedCount.incrementAndGet();
        if (current >= total) {
            onComplete.run();
        }
    }

    private void parseSearchResult(String resultJson, AgentState state) {
        try {
            JsonNode root = MAPPER.readTree(resultJson);

            if (!root.isArray() || root.isEmpty()) {
                return;
            }

            JsonNode first = root.get(0);
            JsonNode textNode = first.get("text");

            if (textNode == null || textNode.isNull()) {
                return;
            }

            JsonNode textJson;
            if (textNode.isTextual()) {
                textJson = MAPPER.readTree(textNode.asText());
            } else {
                textJson = textNode;
            }

            JsonNode results = textJson.get("results");
            if (results == null || !results.isArray()) {
                return;
            }

            for (JsonNode item : results) {
                String url = getSafe(item, "url");
                String title = getSafe(item, "title");
                String content = getSafe(item, "content");

                if (url != null && !url.isBlank()) {
                    state.searchResults.add(new SearchResult(url, title, content));
                }
            }

        } catch (Exception e) {
            log.warn("解析 tavily 搜索结果失败: {}", e.getMessage());
        }
    }

    private String getSafe(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private void addErrorToolResponse(List<Message> messages, AssistantMessage.ToolCall toolCall, String errMsg) {
        ToolResponseMessage.ToolResponse tr = new ToolResponseMessage.ToolResponse(
                toolCall.id(),
                toolCall.name(),
                "{ \"error\": \"" + errMsg + "\" }"
        );

        messages.add(ToolResponseMessage.builder()
                .responses(List.of(tr))
                .build());
    }

    private ToolCallback findTool(String name) {
        return tools.stream()
                .filter(t -> t.getToolDefinition().name().equals(name))
                .findFirst()
                .orElse(null);
    }

    public void setMaxRounds(int maxRounds) {
        this.maxRounds = maxRounds;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String name;
        private ChatModel chatModel;
        private List<ToolCallback> tools;
        private String systemPrompt = "";
        private int maxReflectionRounds;
        private int maxRounds;
        private List<Advisor> advisors;
        private ChatMemory chatMemory;
        private AiSessionService sessionService;
        private AgentTaskManager taskManager;

        public Builder chatMemory(ChatMemory chatMemory) {
            this.chatMemory = chatMemory;
            return this;
        }

        public Builder sessionService(AiSessionService sessionService) {
            this.sessionService = sessionService;
            return this;
        }

        public Builder taskManager(AgentTaskManager taskManager) {
            this.taskManager = taskManager;
            return this;
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder chatModel(ChatModel chatModel) {
            this.chatModel = chatModel;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            this.tools = Arrays.asList(tools);
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            this.tools = tools;
            return this;
        }

        public Builder advisors(List<Advisor> advisors) {
            this.advisors = advisors;
            return this;
        }

        public Builder advisors(Advisor... advisors) {
            this.advisors = Arrays.asList(advisors);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder maxReflectionRounds(int maxReflectionRounds) {
            this.maxReflectionRounds = maxReflectionRounds;
            return this;
        }

        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public WebSearchReactAgent build() {
            if (chatModel == null) {
                throw new IllegalArgumentException("chatModel 不能为空！");
            }
            return new WebSearchReactAgent(name, chatModel, tools, systemPrompt, maxRounds, chatMemory, advisors, maxReflectionRounds, sessionService, taskManager);
        }
    }
}
