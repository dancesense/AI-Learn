(function () {
  var API_ROOT = "";
  var PHASE = "白天发言";
  var silenceMillis = 1800;
  var voiceThreshold = 0.024;

  var SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  var statusText = document.getElementById("statusText");
  var timerText = document.getElementById("timerText");
  var messageBoard = document.getElementById("messageBoard");
  var probabilityChart = document.getElementById("probabilityChart");
  var voteAdviceText = document.getElementById("voteAdviceText");
  var votePointsList = document.getElementById("votePointsList");
  var suggestedSpeechText = document.getElementById("suggestedSpeechText");
  var wolfTalkList = document.getElementById("wolfTalkList");
  var hintText = document.getElementById("hintText");
  var queueText = document.getElementById("queueText");
  var toggleBtn = document.getElementById("toggleBtn");
  var flushBtn = document.getElementById("flushBtn");
  var recBtn = document.getElementById("recBtn");
  var closeBtn = document.getElementById("closeBtn");

  var sessionId = null;
  var sessionUuid = "live-" + Date.now();

  var recognition = null;
  var running = false;
  var started = false;
  var recognitionActive = false;

  var audioStream = null;
  var audioCtx = null;
  var analyser = null;
  var vadTimer = null;
  var timerJob = null;

  var currentSpeakerId = null;
  var currentParts = [];
  var draftLabel = "";
  var lastVoiceAt = Date.now();
  var elapsedSeconds = 0;

  var sending = false;
  var queue = [];

  var lastRenderedHash = "";

  function pad(v) {
    return v < 10 ? "0" + v : String(v);
  }

  function fmtTimer(total) {
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    return pad(h) + ":" + pad(m) + ":" + pad(s);
  }

  function setHint(text) {
    hintText.textContent = text;
  }

  function setStatus(text) {
    statusText.textContent = text;
  }

  function setQueueHint() {
    if (sending) {
      queueText.textContent = "后端分析中，新的语音片段会排队等待，避免请求错乱。排队数：" + queue.length;
      return;
    }
    if (queue.length > 0) {
      queueText.textContent = "待发送语音片段：" + queue.length;
      return;
    }
    queueText.textContent = "";
  }

  function addMessage(role, speaker, content, extraClass) {
    var wrap = document.createElement("div");
    wrap.className = "msg";

    var avatar = document.createElement("div");
    avatar.className = "avatar" + (role === "ai" ? " ai" : "");
    avatar.textContent = role === "ai" ? "AI" : ((speaker || "?") + "号");

    var bubble = document.createElement("div");
    bubble.className = "bubble" + (role === "ai" ? " ai" : "") + (extraClass ? (" " + extraClass) : "");
    bubble.textContent = content;

    wrap.appendChild(avatar);
    wrap.appendChild(bubble);
    messageBoard.appendChild(wrap);
    messageBoard.scrollTop = messageBoard.scrollHeight;
  }

  function renderProbabilityBars(items) {
    if (!probabilityChart) {
      return;
    }
    var list = Array.isArray(items) ? items : [];
    if (list.length === 0) {
      probabilityChart.innerHTML = "<div class=\"panel-main\">暂无概率数据</div>";
      return;
    }
    probabilityChart.innerHTML = "";
    list.forEach(function (bar) {
      var playerId = bar && bar.playerId ? bar.playerId : "?";
      var prob = bar && typeof bar.werewolfProbability === "number" ? bar.werewolfProbability : 0;
      prob = Math.max(0, Math.min(100, prob));

      var row = document.createElement("div");
      row.className = "prob-row";
      row.innerHTML =
        "<span>" + playerId + "号</span>" +
        "<div class=\"prob-track\"><div class=\"prob-fill\" style=\"width:" + prob + "%\"></div></div>" +
        "<span>" + prob + "%</span>";
      probabilityChart.appendChild(row);
    });
  }

  function renderTextList(container, items) {
    if (!container) {
      return;
    }
    var list = Array.isArray(items) ? items.filter(Boolean) : [];
    if (list.length === 0) {
      container.innerHTML = "<li>暂无</li>";
      return;
    }
    container.innerHTML = "";
    list.forEach(function (item) {
      var li = document.createElement("li");
      li.textContent = item;
      container.appendChild(li);
    });
  }

  function renderStrategyPanels(data) {
    renderProbabilityBars(data.probabilities || []);

    if (voteAdviceText) {
      voteAdviceText.textContent = data.voteAdvice || "暂无稳定归票建议";
    }
    if (suggestedSpeechText) {
      suggestedSpeechText.textContent = data.suggestedSpeech || "暂无建议发言";
    }
    renderTextList(votePointsList, data.votePoints || []);
    renderTextList(wolfTalkList, data.werewolfTalks || []);
  }

  function renderFromEvents(events) {
    if (!events || !events.length) {
      return;
    }

    var hash = JSON.stringify(events.map(function (e) {
      return [e.eventType, e.speakerPlayerId, e.content, e.createdAt];
    }));
    if (hash === lastRenderedHash) {
      return;
    }
    lastRenderedHash = hash;

    messageBoard.innerHTML = "";
    events.forEach(function (event) {
      if (event.eventType === "PLAYER_SPEECH") {
        addMessage("player", event.speakerPlayerId, event.content || "");
      } else if (event.eventType === "AI_INSIGHT") {
        addMessage("ai", null, event.content || "");
      }
    });
  }

  function parseSpeakerPrompt(text) {
    if (!text) {
      return { speakerId: null, cleaned: "", purePrompt: false };
    }
    var reg = /(轮到)?\s*(\d+)\s*号(?:玩家)?发言[：:，,\s]*/g;
    var found = null;
    var cleaned = text;

    cleaned = cleaned.replace(reg, function (_, __, id) {
      found = parseInt(id, 10);
      return "";
    }).trim();

    var purePrompt = !!found && cleaned.length === 0;

    return {
      speakerId: found,
      cleaned: cleaned,
      purePrompt: purePrompt
    };
  }

  function appendTranscript(rawText) {
    var parsed = parseSpeakerPrompt(rawText);
    if (parsed.speakerId) {
      currentSpeakerId = parsed.speakerId;
      draftLabel = currentSpeakerId + "号玩家";
    }
    if (parsed.purePrompt || !parsed.cleaned) {
      return;
    }

    currentParts.push(parsed.cleaned);
    lastVoiceAt = Date.now();

    var preview = currentParts.join(" ").slice(0, 80);
    setHint((draftLabel || "当前玩家") + "转写中：" + preview + (preview.length >= 80 ? "..." : ""));
  }

  function currentTranscript() {
    var text = currentParts.join(" ").replace(/\s+/g, " ").trim();
    return text;
  }

  function flushCurrentSegment(reason) {
    var text = currentTranscript();
    if (!text) {
      return;
    }

    var player = currentSpeakerId;
    addMessage("player", player, text, "pending");

    var payloadText = player ? (player + "号玩家：" + text) : text;
    var silenceSeconds = reason === "silence" ? Math.floor((Date.now() - lastVoiceAt) / 1000) : 0;

    queue.push({
      transcript: payloadText,
      phase: PHASE,
      silenceSeconds: silenceSeconds,
      speakerPlayerId: player
    });
    currentParts = [];
    setHint("已切段，等待后端分析...");
    setQueueHint();
    consumeQueue();
  }

  async function consumeQueue() {
    if (sending || queue.length === 0 || !sessionId) {
      return;
    }

    sending = true;
    setQueueHint();
    var payload = queue.shift();

    try {
      var res = await fetch(API_ROOT + "/werewolf/live/sessions/" + sessionId + "/chunks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        throw new Error("后端返回异常状态：" + res.status);
      }

      var data = await res.json();
      if (typeof data.elapsedSeconds === "number") {
        elapsedSeconds = data.elapsedSeconds;
        timerText.textContent = fmtTimer(elapsedSeconds);
      }
      if (data.currentSpeakerId) {
        currentSpeakerId = data.currentSpeakerId;
      }
      renderFromEvents(data.events || []);
      renderStrategyPanels(data);
      setHint("分析已更新，继续监听中...");
    } catch (err) {
      console.error(err);
      setHint("分析请求失败：" + err.message);
    } finally {
      sending = false;
      setQueueHint();
      if (queue.length > 0) {
        consumeQueue();
      }
    }
  }

  function initRecognition() {
    if (recognition) {
      return;
    }
    if (!SpeechRecognition) {
      setHint("当前浏览器不支持语音转文字，请使用 Chrome Edge 最新版。\n你仍可以点击“手动发送当前段”。");
      return;
    }

    recognition = new SpeechRecognition();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;

    recognition.onstart = function () {
      recognitionActive = true;
      setStatus("实时分析中");
      recBtn.textContent = "REC";
    };

    recognition.onresult = function (event) {
      for (var i = event.resultIndex; i < event.results.length; i++) {
        var result = event.results[i];
        var text = result[0].transcript ? result[0].transcript.trim() : "";
        if (!text) {
          continue;
        }
        if (result.isFinal) {
          appendTranscript(text);
        }
      }
    };

    recognition.onerror = function (event) {
      console.warn("speech error", event.error);
      setHint("语音识别异常：" + event.error + "，正在尝试恢复...");
    };

    recognition.onend = function () {
      recognitionActive = false;
      if (running) {
        setTimeout(function () {
          startRecognition();
        }, 300);
      }
    };
  }

  function startRecognition() {
    if (!recognition || recognitionActive) {
      return;
    }
    try {
      recognition.start();
    } catch (err) {
      console.warn("recognition restart ignored", err);
    }
  }

  function stopRecognition() {
    if (!recognition || !recognitionActive) {
      return;
    }
    recognition.stop();
  }

  function setupVad() {
    if (!analyser) {
      return;
    }
    var arr = new Uint8Array(analyser.fftSize);

    vadTimer = window.setInterval(function () {
      analyser.getByteTimeDomainData(arr);
      var sum = 0;
      for (var i = 0; i < arr.length; i++) {
        var v = (arr[i] - 128) / 128;
        sum += v * v;
      }
      var rms = Math.sqrt(sum / arr.length);
      var now = Date.now();

      if (rms > voiceThreshold) {
        lastVoiceAt = now;
      }

      if (currentParts.length > 0 && now - lastVoiceAt >= silenceMillis) {
        flushCurrentSegment("silence");
      }
    }, 250);
  }

  async function initAudio() {
    audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    var source = audioCtx.createMediaStreamSource(audioStream);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 2048;
    source.connect(analyser);
    setupVad();
  }

  async function createSession() {
    var body = {
      sessionUuid: sessionUuid,
      totalPlayers: 12,
      gameMode: "12人标准局",
      myPlayerId: 1,
      myRoleHint: "未知"
    };

    var res = await fetch(API_ROOT + "/werewolf/live/sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });

    if (!res.ok) {
      throw new Error("创建会话失败：" + res.status);
    }

    var data = await res.json();
    sessionId = data.sessionId;
  }

  function startTimer() {
    timerText.textContent = fmtTimer(elapsedSeconds);
    timerJob = window.setInterval(function () {
      elapsedSeconds += 1;
      timerText.textContent = fmtTimer(elapsedSeconds);
    }, 1000);
  }

  function cleanup() {
    running = false;
    stopRecognition();

    if (vadTimer) {
      window.clearInterval(vadTimer);
      vadTimer = null;
    }

    if (timerJob) {
      window.clearInterval(timerJob);
      timerJob = null;
    }

    if (audioStream) {
      audioStream.getTracks().forEach(function (t) { t.stop(); });
      audioStream = null;
    }

    if (audioCtx) {
      audioCtx.close();
      audioCtx = null;
    }
  }

  async function startAll() {
    setStatus("实时分析中");
    setHint("正在创建实时会话...");

    if (!started) {
      await createSession();
      await initAudio();
      started = true;
    }
    initRecognition();
    startRecognition();
    running = true;
    if (!timerJob) {
      startTimer();
    }

    setHint("已开始监听：检测到安静后会自动切段并发送 AI 分析。");
  }

  function pauseListening() {
    running = false;
    stopRecognition();
    flushCurrentSegment("manual");
    setStatus("已暂停");
    setHint("监听已暂停，点击“恢复监听”继续。");
    toggleBtn.textContent = "恢复监听";
    recBtn.textContent = "PAUSE";
  }

  async function toggleRun() {
    if (!running) {
      await startAll();
      toggleBtn.textContent = "暂停监听";
      recBtn.textContent = "REC";
      return;
    }

    pauseListening();
  }

  toggleBtn.addEventListener("click", function () {
    toggleRun().catch(function (err) {
      console.error(err);
      setHint("启动失败：" + err.message);
    });
  });

  recBtn.addEventListener("click", function () {
    toggleBtn.click();
  });

  flushBtn.addEventListener("click", function () {
    flushCurrentSegment("manual");
  });

  closeBtn.addEventListener("click", function () {
    cleanup();
  });

  window.addEventListener("beforeunload", function () {
    cleanup();
  });

  renderStrategyPanels({});

  startAll().catch(function (err) {
    console.error(err);
    setStatus("启动失败");
    setHint("无法启动实时分析：" + err.message + "。请检查麦克风权限和后端服务。\n你可点击“暂停监听/恢复监听”重试。");
  });
})();
