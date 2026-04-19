(function () {
  var API_ROOT = "";
  var PHASE = "白天发言";
  var silenceMillis = 1800;
  var voiceThreshold = 0.024;
  var playerCountOptions = [6, 8, 10, 12];

  var SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;

  var statusText = document.getElementById("statusText");
  var timerText = document.getElementById("timerText");
  var hintText = document.getElementById("hintText");
  var queueText = document.getElementById("queueText");
  var toggleBtn = document.getElementById("toggleBtn");
  var flushBtn = document.getElementById("flushBtn");
  var recBtn = document.getElementById("recBtn");
  var closeBtn = document.getElementById("closeBtn");

  var countChips = document.getElementById("countChips");
  var myRoleSelect = document.getElementById("myRoleSelect");
  var playerGrid = document.getElementById("playerGrid");
  var probabilityChart = document.getElementById("probabilityChart");
  var roleMatrix = document.getElementById("roleMatrix");
  var voteAdviceText = document.getElementById("voteAdviceText");
  var votePointsList = document.getElementById("votePointsList");
  var suggestedSpeechText = document.getElementById("suggestedSpeechText");
  var wolfTalkList = document.getElementById("wolfTalkList");

  var playerCount = 12;
  var myRoleHint = "未知";
  var selectedPlayerId = null;

  var sessionId = null;
  var sessionUuid = null;

  var recognition = null;
  var recognitionReady = false;
  var recognitionActive = false;
  var running = false;
  var resourcesReady = false;

  var audioStream = null;
  var audioCtx = null;
  var analyser = null;
  var vadTimer = null;
  var timerJob = null;

  var currentParts = [];
  var lastVoiceAt = Date.now();
  var elapsedSeconds = 0;

  var sending = false;
  var queue = [];

  var playerHistories = {};
  var playerSeconds = {};

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

  function ensurePlayerMap() {
    for (var i = 1; i <= playerCount; i++) {
      if (!playerHistories[i]) {
        playerHistories[i] = [];
      }
      if (!playerSeconds[i]) {
        playerSeconds[i] = 0;
      }
    }
  }

  function setQueueHint() {
    if (sending) {
      queueText.textContent = "后端分析中，新的语音片段会排队等待。排队数：" + queue.length;
      return;
    }
    if (queue.length > 0) {
      queueText.textContent = "待发送语音片段：" + queue.length;
      return;
    }
    queueText.textContent = "";
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

  function renderProbabilityBars(items) {
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

  function findRoleProb(assessment, roleName) {
    if (!assessment || !Array.isArray(assessment.roleProbabilities)) {
      return 0;
    }
    var aliases = {
      "平民": ["平民", "村民"],
      "狼人": ["狼人", "狼"],
      "预言家": ["预言家"],
      "女巫": ["女巫"],
      "猎人": ["猎人"],
      "守卫": ["守卫"]
    };
    var expected = aliases[roleName] || [roleName];
    var found = assessment.roleProbabilities.find(function (item) {
      if (!item || !item.role) {
        return false;
      }
      return expected.some(function (name) {
        return item.role === name;
      });
    });
    if (!found || typeof found.probability !== "number") {
      return 0;
    }
    return Math.max(0, Math.min(1, found.probability));
  }

  function renderRoleMatrix(playerAssessments) {
    var list = Array.isArray(playerAssessments) ? playerAssessments : [];
    if (list.length === 0) {
      roleMatrix.innerHTML = "<div class=\"panel-main\">暂无角色概率明细</div>";
      return;
    }
    var sorted = list.slice().sort(function (a, b) {
      return (a.playerId || 0) - (b.playerId || 0);
    });

    var html = "<div class=\"matrix-head\"><span>玩家</span><span>狼人</span><span>预言家</span><span>女巫</span><span>猎人</span><span>守卫</span><span>平民</span></div>";
    sorted.forEach(function (item) {
      html += "<div class=\"matrix-row\">" +
        "<span>" + (item.playerId || "?") + "号</span>" +
        "<span>" + Math.round(findRoleProb(item, "狼人") * 100) + "%</span>" +
        "<span>" + Math.round(findRoleProb(item, "预言家") * 100) + "%</span>" +
        "<span>" + Math.round(findRoleProb(item, "女巫") * 100) + "%</span>" +
        "<span>" + Math.round(findRoleProb(item, "猎人") * 100) + "%</span>" +
        "<span>" + Math.round(findRoleProb(item, "守卫") * 100) + "%</span>" +
        "<span>" + Math.round(findRoleProb(item, "平民") * 100) + "%</span>" +
        "</div>";
    });
    roleMatrix.innerHTML = html;
  }

  function renderStrategyPanels(data) {
    data = data || {};
    renderProbabilityBars(data.probabilities || []);
    renderRoleMatrix(data.playerAssessments || []);
    voteAdviceText.textContent = data.voteAdvice || "暂无稳定归票建议";
    suggestedSpeechText.textContent = data.suggestedSpeech || "暂无建议发言";
    renderTextList(votePointsList, data.votePoints || []);
    renderTextList(wolfTalkList, data.werewolfTalks || []);
  }

  function renderCountChips() {
    countChips.innerHTML = "";
    playerCountOptions.forEach(function (count) {
      var btn = document.createElement("button");
      btn.className = "count-chip" + (count === playerCount ? " active" : "");
      btn.textContent = count + "人";
      btn.addEventListener("click", function () {
        if (count === playerCount) {
          return;
        }
        switchPlayerCount(count);
      });
      countChips.appendChild(btn);
    });
  }

  function buildPreview(playerId) {
    var history = playerHistories[playerId] || [];
    if (running && selectedPlayerId === playerId && currentParts.length > 0) {
      return currentParts.join(" ");
    }
    if (history.length === 0) {
      return "点击右下角麦克风开始录音...";
    }
    return history.slice(-2).join("\n");
  }

  function renderPlayerGrid() {
    ensurePlayerMap();
    playerGrid.innerHTML = "";
    for (var i = 1; i <= playerCount; i++) {
      var card = document.createElement("article");
      card.className = "player-card" + (selectedPlayerId === i ? " active" : "");

      var top = document.createElement("div");
      top.className = "player-top";
      top.innerHTML = "<strong>" + i + "号玩家</strong><span>" + fmtTimer(playerSeconds[i] || 0) + "</span>";

      var body = document.createElement("div");
      body.className = "player-body";
      body.textContent = buildPreview(i);

      var micBtn = document.createElement("button");
      micBtn.className = "player-mic" + (running && selectedPlayerId === i ? " on" : "");
      micBtn.textContent = running && selectedPlayerId === i ? "录音中" : "录音";
      micBtn.addEventListener("click", (function (id) {
        return function () {
          onPlayerMicClick(id);
        };
      })(i));

      card.appendChild(top);
      card.appendChild(body);
      card.appendChild(micBtn);
      playerGrid.appendChild(card);
    }
  }

  function resetSessionData() {
    sessionId = null;
    sessionUuid = "live-" + Date.now() + "-" + playerCount;
    queue = [];
    sending = false;
    currentParts = [];
    selectedPlayerId = null;
    playerHistories = {};
    playerSeconds = {};
    ensurePlayerMap();
    setQueueHint();
    renderPlayerGrid();
    renderStrategyPanels({});
  }

  function pauseListening() {
    running = false;
    stopRecognition();
    flushCurrentSegment("manual");
    setStatus("已暂停");
    toggleBtn.textContent = "恢复监听";
    recBtn.textContent = "PAUSE";
    renderPlayerGrid();
  }

  function switchPlayerCount(count) {
    pauseListening();
    playerCount = count;
    renderCountChips();
    resetSessionData();
    setHint("已切换为" + count + "人局，请点击对应玩家卡片开始录音。");
  }

  function updateMyRoleHint(role) {
    if (!role || role === myRoleHint) {
      return;
    }
    pauseListening();
    myRoleHint = role;
    resetSessionData();
    setHint("我的身份已切换为“" + role + "”，后续分析将按该身份约束。");
  }

  function appendTranscript(rawText) {
    if (!selectedPlayerId) {
      return;
    }
    var text = (rawText || "").replace(/\s+/g, " ").trim();
    if (!text) {
      return;
    }
    currentParts.push(text);
    lastVoiceAt = Date.now();
    renderPlayerGrid();
  }

  function currentTranscript() {
    return currentParts.join(" ").replace(/\s+/g, " ").trim();
  }

  function flushCurrentSegment(reason) {
    if (!selectedPlayerId) {
      return;
    }
    var text = currentTranscript();
    if (!text) {
      return;
    }

    playerHistories[selectedPlayerId].push(text);
    if (playerHistories[selectedPlayerId].length > 8) {
      playerHistories[selectedPlayerId] = playerHistories[selectedPlayerId].slice(-8);
    }

    var silenceSeconds = reason === "silence" ? Math.floor((Date.now() - lastVoiceAt) / 1000) : 0;
    queue.push({
      transcript: text,
      phase: PHASE,
      silenceSeconds: silenceSeconds,
      speakerPlayerId: selectedPlayerId,
      myRoleHint: myRoleHint
    });

    currentParts = [];
    setHint(selectedPlayerId + "号玩家片段已提交，等待后端分析...");
    renderPlayerGrid();
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
      }
      renderStrategyPanels(data);
      setHint("分析已更新，可继续录音。");
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

  function setupVad() {
    if (!analyser) {
      return;
    }
    var arr = new Uint8Array(analyser.fftSize);

    vadTimer = window.setInterval(function () {
      if (!running || !selectedPlayerId) {
        return;
      }
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

  function initRecognition() {
    if (recognitionReady) {
      return;
    }
    if (!SpeechRecognition) {
      setHint("当前浏览器不支持语音转文字，请使用 Chrome/Edge 最新版。");
      return;
    }

    recognition = new SpeechRecognition();
    recognition.lang = "zh-CN";
    recognition.continuous = true;
    recognition.interimResults = true;

    recognition.onstart = function () {
      recognitionActive = true;
      recBtn.textContent = "REC";
    };

    recognition.onresult = function (event) {
      for (var i = event.resultIndex; i < event.results.length; i++) {
        var result = event.results[i];
        if (!result[0] || !result[0].transcript) {
          continue;
        }
        if (result.isFinal) {
          appendTranscript(result[0].transcript);
        }
      }
    };

    recognition.onerror = function (event) {
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

    recognitionReady = true;
  }

  function startRecognition() {
    if (!recognitionReady || recognitionActive) {
      return;
    }
    try {
      recognition.start();
    } catch (err) {
      console.warn("recognition restart ignored", err);
    }
  }

  function stopRecognition() {
    if (!recognitionReady || !recognitionActive) {
      return;
    }
    recognition.stop();
  }

  async function initAudioResources() {
    if (resourcesReady) {
      return;
    }
    audioStream = await navigator.mediaDevices.getUserMedia({ audio: true });
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
    var source = audioCtx.createMediaStreamSource(audioStream);
    analyser = audioCtx.createAnalyser();
    analyser.fftSize = 2048;
    source.connect(analyser);
    setupVad();
    initRecognition();
    resourcesReady = true;
  }

  async function createSessionIfNeeded() {
    if (sessionId) {
      return;
    }
    var body = {
      sessionUuid: sessionUuid,
      totalPlayers: playerCount,
      gameMode: playerCount + "人局",
      myPlayerId: 1,
      myRoleHint: myRoleHint
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

  async function startRecordingForPlayer(playerId) {
    selectedPlayerId = playerId;
    setStatus("实时分析中");
    setHint("正在启动 " + playerId + " 号玩家录音...");

    await initAudioResources();
    await createSessionIfNeeded();

    running = true;
    lastVoiceAt = Date.now();
    startRecognition();

    toggleBtn.textContent = "暂停监听";
    recBtn.textContent = "REC";
    setHint("正在录音：" + playerId + "号玩家。安静后会自动切段并分析。");
    renderPlayerGrid();
  }

  async function onPlayerMicClick(playerId) {
    if (running && selectedPlayerId === playerId) {
      pauseListening();
      return;
    }

    if (running && selectedPlayerId && selectedPlayerId !== playerId) {
      flushCurrentSegment("manual");
    }

    try {
      await startRecordingForPlayer(playerId);
    } catch (err) {
      console.error(err);
      setHint("启动录音失败：" + err.message);
    }
  }

  function startTimer() {
    if (timerJob) {
      return;
    }
    timerJob = window.setInterval(function () {
      elapsedSeconds += 1;
      if (running && selectedPlayerId) {
        playerSeconds[selectedPlayerId] = (playerSeconds[selectedPlayerId] || 0) + 1;
      }
      timerText.textContent = fmtTimer(elapsedSeconds);
      renderPlayerGrid();
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

  toggleBtn.addEventListener("click", function () {
    if (!running) {
      if (selectedPlayerId) {
        startRecordingForPlayer(selectedPlayerId).catch(function (err) {
          setHint("恢复失败：" + err.message);
        });
      } else {
        setHint("请先点击一个玩家卡片的录音按钮。");
      }
      return;
    }
    pauseListening();
  });

  flushBtn.addEventListener("click", function () {
    flushCurrentSegment("manual");
  });

  recBtn.addEventListener("click", function () {
    if (selectedPlayerId) {
      onPlayerMicClick(selectedPlayerId);
      return;
    }
    onPlayerMicClick(1);
  });

  myRoleSelect.addEventListener("change", function (e) {
    updateMyRoleHint((e.target && e.target.value) || "未知");
  });

  closeBtn.addEventListener("click", function () {
    cleanup();
  });

  window.addEventListener("beforeunload", function () {
    cleanup();
  });

  resetSessionData();
  renderCountChips();
  renderStrategyPanels({});
  timerText.textContent = fmtTimer(elapsedSeconds);
  startTimer();
})();
