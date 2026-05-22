(function () {
  "use strict";

  /* =====================================================
     常量 & 配置
  ===================================================== */
  var API_ROOT = "";
  var silenceMillis = 2500;  // 静音超时从1.8s增加到2.5s，避免过快截断
  var voiceThreshold = 0.015; // 降低阈值从0.024→0.015，更容易检测到声音

  // 身份关键词 → 红色
  var IDENTITY_KEYWORDS = [
    "金水", "银水", "铜水", "查杀", "悍跳", "跳预", "警徽", "自曝",
    "预言家", "女巫", "猎人", "守卫", "平民", "狼人", "好人",
    "白神", "铁狼", "骗信", "蛋牌", "猎人牌", "断剑", "决斗",
    "机械狼", "通灵师", "镜子", "模仿", "暴民", "神职"
  ];
  // 行为关键词 → 黄色
  var ACTION_KEYWORDS = [
    "跳", "对跳", "焊跳", "占边", "聊爆", "踩", "表水",
    "划水", "冲票", "归票", "压手", "抗推", "空守", "自守",
    "平安夜", "断剑", "pk", "爆点", "站边", "出局", "放逐",
    "投票", "警长", "竞选", "刀人"
  ];

  // ===== 性能优化：预编译关键词正则 =====
  var identityRegex = null;
  var actionRegex = null;
  (function compileKeywordRegex() {
    var escaped = IDENTITY_KEYWORDS.map(function(k) {
      return k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    });
    identityRegex = new RegExp('(' + escaped.join('|') + ')', 'g');

    var escaped2 = ACTION_KEYWORDS.map(function(k) {
      return k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    });
    actionRegex = new RegExp('(' + escaped2.join('|') + ')', 'g');
  })();

  /* =====================================================
     状态变量
  ===================================================== */
  var playerCount = 12;
  var myRoleHint = "未知";
  var gameMode = "12人标准局";
  var myPlayerId = 1;
  var selectedPlayerId = null;
  var currentDay = 1;
  var maxDay = 1;
  var sessionId = null;
  var sessionUuid = null;
  var roleComposition = null; // 板子角色配置

  var running = false;
  var resourcesReady = false;

  var audioStream = null;
  var audioCtx = null;
  var analyserNode = null;
  var vadTimer = null;
  var timerJob = null;

  // MediaRecorder 相关
  var mediaRecorder = null;
  var audioChunks = [];
  var isTranscribing = false;
  var recordingStartTime = 0;

  var lastVoiceAt = Date.now();
  var elapsedSeconds = 0;
  var sending = false;
  var queue = [];
  var abortController = null;

  // 每个玩家的文本历史（原始发言）—— 按天隔离 {day: {playerId: [text1, text2, ...]}}
  var dayPlayerHistories = {};

  /** 获取当前天的玩家历史 */
  function getPlayerHistories() {
    if (!dayPlayerHistories[currentDay]) dayPlayerHistories[currentDay] = {};
    return dayPlayerHistories[currentDay];
  }

  /** 获取指定天指定玩家的历史 */
  function getPlayerHistory(playerId) {
    var histories = getPlayerHistories();
    if (!histories[playerId]) histories[playerId] = [];
    return histories[playerId];
  }
  // 每个玩家的AI精简总结
  var playerAISummaries = {};
  // 每个玩家的录音秒数
  var playerSeconds = {};
  // 玩家存活状态 {id: true/false}
  var playerAlive = {};
  // 玩家已知身份
  var playerRoles = {};
  // 投票记录 [{from, to, day, round}]
  var voteRecords = [];
  // 技能记录
  var skillLogs = [];
  // 话术类型
  var speechType = "defense";
  // 话术内容 {defense, attack, tablewater, identity}
  var speechTemplates = {};
  // 概率数据缓存
  var latestProbabilities = {};
  // 玩家状态标签
  var playerStatusTags = {};
  // 当前Tab
  var currentTab = "speech";

  // ===== 性能优化：防抖 & 增量渲染 =====
  var renderPending = false;
  var renderTimer = null;

  function scheduleRender() {
    if (renderPending) return;
    renderPending = true;
    // 使用 requestAnimationFrame 优先，降级 setTimeout
    if (window.requestAnimationFrame) {
      requestAnimationFrame(function() {
        renderPending = false;
        renderSpeechList();
      });
    } else {
      renderTimer = setTimeout(function() {
        renderPending = false;
        renderSpeechList();
      }, 50);
    }
  }

  // SSE 流式更新节流
  var lastStreamUpdate = 0;
  var STREAM_THROTTLE_MS = 150; // 至少间隔150ms更新一次DOM

  /* =====================================================
     DOM 引用
  ===================================================== */
  var statusDot = document.getElementById("statusDot");
  var statusText = document.getElementById("statusText");
  var timerText = document.getElementById("timerText");
  var hintText = document.getElementById("hintText");
  var queueText = document.getElementById("queueText");
  var recBtn = document.getElementById("recBtn");
  var recBtnLabel = document.getElementById("recBtnLabel");
  var endGameBtn = document.getElementById("endGameBtn");
  var dayNav = document.getElementById("dayNav");
  var addDayBtn = document.getElementById("addDayBtn");
  var gameModeLabel = document.getElementById("gameModeLabel");
  var myRoleSelect = document.getElementById("myRoleSelect");
  var mySeatLabel = document.getElementById("mySeatLabel");
  var phaseSelect = document.getElementById("phaseSelect");
  var speechList = document.getElementById("speechList");
  var aliveCount = document.getElementById("aliveCount");
  var probabilityChart = document.getElementById("probabilityChart");
  var roleMatrix = document.getElementById("roleMatrix");
  var voteAdviceText = document.getElementById("voteAdviceText");
  var votePointsList = document.getElementById("votePointsList");
  var speechContent = document.getElementById("speechContent");
  var copySpeechBtn = document.getElementById("copySpeechBtn");
  var voteRecordList = document.getElementById("voteRecordList");
  var voteFrom = document.getElementById("voteFrom");
  var voteTo = document.getElementById("voteTo");
  var voteAddBtn = document.getElementById("voteAddBtn");
  var aiSummaryText = document.getElementById("aiSummaryText");
  var mindMapArea = document.getElementById("mindMapArea");
  var fullSpeechModal = document.getElementById("fullSpeechModal");
  var fullSpeechClose = document.getElementById("fullSpeechClose");
  var fullSpeechTitle = document.getElementById("fullSpeechTitle");
  var fullSpeechList = document.getElementById("fullSpeechList");
  var wolfProbPanel = document.getElementById("wolfProbPanel");

  /* =====================================================
     工具函数
  ===================================================== */
  function pad(v) { return v < 10 ? "0" + v : String(v); }

  function fmtTimer(total) {
    var h = Math.floor(total / 3600);
    var m = Math.floor((total % 3600) / 60);
    var s = total % 60;
    return pad(h) + ":" + pad(m) + ":" + pad(s);
  }

  function setHint(text) { if (hintText) hintText.textContent = text; }
  function setStatus(text) { if (statusText) statusText.textContent = text; }

  function setQueueHint() {
    if (!queueText) return;
    if (sending) {
      queueText.textContent = "AI分析中，队列：" + queue.length;
      return;
    }
    queueText.textContent = queue.length > 0 ? "待发送：" + queue.length + " 段" : "";
  }

  function highlightKeywords(text) {
    if (!text) return "";
    var escaped = text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    // 玩家编号高亮（如"3号""12号"）→ 红色
    escaped = escaped.replace(/(\d+)\s*号/g, '<span class="kw-player">$1号</span>');
    // 使用预编译正则代替循环replace
    escaped = escaped.replace(identityRegex, '<span class="kw-identity">$1</span>');
    escaped = escaped.replace(actionRegex, '<span class="kw-action">$1</span>');
    return escaped;
  }

  /** 格式化AI输出的三板块内容（自称、评价、期望） */
  function formatThreeBlocks(text) {
    if (!text) return "";
    // 检测是否包含三板块标记
    var hasClaim = text.indexOf("【自称】") >= 0;
    var hasEval = text.indexOf("【评价】") >= 0;
    var hasExpect = text.indexOf("【期望】") >= 0;
    if (!hasClaim && !hasEval && !hasExpect) return highlightKeywords(text);

    var html = '<div class="three-blocks">';
    // 按板块拆分
    var claimMatch = text.match(/【自称】(.+?)(?=【评价】|【期望】|$)/s);
    var evalMatch = text.match(/【评价】(.+?)(?=【自称】|【期望】|$)/s);
    var expectMatch = text.match(/【期望】(.+?)(?=【自称】|【评价】|$)/s);

    if (claimMatch && claimMatch[1].trim()) {
      html += '<div class="block-claim"><span class="block-label">🏷️ 自称</span><span class="block-text">' +
        highlightKeywords(claimMatch[1].trim()) + '</span></div>';
    }
    if (evalMatch && evalMatch[1].trim()) {
      html += '<div class="block-eval"><span class="block-label">🔍 评价</span><span class="block-text">' +
        highlightKeywords(evalMatch[1].trim()) + '</span></div>';
    }
    if (expectMatch && expectMatch[1].trim()) {
      html += '<div class="block-expect"><span class="block-label">🎯 期望</span><span class="block-text">' +
        highlightKeywords(expectMatch[1].trim()) + '</span></div>';
    }
    html += '</div>';
    return html;
  }

  /* =====================================================
     Tab 切换
  ===================================================== */
  function switchTab(tabName) {
    currentTab = tabName;
    document.querySelectorAll(".tab-btn").forEach(function (btn) {
      btn.classList.toggle("active", btn.getAttribute("data-tab") === tabName);
    });
    document.querySelectorAll(".tab-panel").forEach(function (panel) {
      panel.classList.toggle("active", panel.id === "tab-" + tabName);
    });
  }

  document.querySelectorAll(".tab-btn").forEach(function (btn) {
    btn.addEventListener("click", function () {
      switchTab(btn.getAttribute("data-tab"));
    });
  });

  /* =====================================================
     初始化
  ===================================================== */
  function init() {
    var storedCount = sessionStorage.getItem("werewolf_count");
    var storedRole = sessionStorage.getItem("werewolf_role");
    var storedMode = sessionStorage.getItem("werewolf_mode");
    var storedSeat = sessionStorage.getItem("werewolf_seat");
    var storedRoleComp = sessionStorage.getItem("werewolf_role_composition");

    if (storedCount) playerCount = parseInt(storedCount) || 12;
    if (storedRole) myRoleHint = storedRole;
    if (storedMode) gameMode = storedMode;
    if (storedSeat) myPlayerId = parseInt(storedSeat) || 1;
    if (storedRoleComp) {
      try { roleComposition = JSON.parse(storedRoleComp); } catch(e) { roleComposition = null; }
    }

    if (gameModeLabel) gameModeLabel.textContent = gameMode;
    if (myRoleSelect) myRoleSelect.value = myRoleHint;
    if (mySeatLabel) mySeatLabel.textContent = myPlayerId + "号位";

    sessionUuid = "live-" + Date.now() + "-" + playerCount;

    resetSessionData();
    renderCountOptions();
    renderSpeechList();
    renderDayNav();
    renderVoteSelects();
    renderVoteRecords();
    updateWolfProbVisibility();

    timerText.textContent = fmtTimer(0);
    startTimer();

    setHint("点击录音按钮开始，说话结束后自动识别并分析。");
  }

  function resetSessionData() {
    dayPlayerHistories = {};
    playerAISummaries = {};
    playerSeconds = {};
    playerAlive = {};
    playerRoles = {};
    playerStatusTags = {};
    for (var i = 1; i <= playerCount; i++) {
      getPlayerHistory(i);
      playerAISummaries[i] = [];
      playerSeconds[i] = 0;
      playerAlive[i] = true;
      playerStatusTags[i] = [];
    }
    updateAliveCount();
  }

  function updateAliveCount() {
    var cnt = 0;
    for (var i = 1; i <= playerCount; i++) {
      if (playerAlive[i]) cnt++;
    }
    if (aliveCount) aliveCount.textContent = cnt + "/" + playerCount;
  }

  /* =====================================================
     狼人概率条件显示
  ===================================================== */
  function updateWolfProbVisibility() {
    if (!wolfProbPanel) return;
    if (myRoleHint.indexOf("狼") >= 0) {
      wolfProbPanel.classList.add("hidden-panel");
    } else {
      wolfProbPanel.classList.remove("hidden-panel");
    }
  }

  /* =====================================================
     天数导航
  ===================================================== */
  function renderDayNav() {
    var chips = dayNav.querySelectorAll(".day-chip:not(.add-btn)");
    chips.forEach(function (c) { c.remove(); });

    for (var d = 1; d <= maxDay; d++) {
      var btn = document.createElement("button");
      btn.className = "day-chip" + (d === currentDay ? " active" : "");
      btn.setAttribute("data-day", d);
      btn.textContent = "第" + d + "天";
      btn.addEventListener("click", (function (day) {
        return function () { switchDay(day); };
      })(d));
      dayNav.insertBefore(btn, addDayBtn);
    }
  }

  function switchDay(day) {
    currentDay = day;
    renderDayNav();
    loadDaySpeeches(currentDay);
    setHint("已进入第" + day + "天");
  }

  async function loadDaySpeeches(day) {
    if (!sessionId) {
      renderSpeechList();
      return;
    }
    try {
      var res = await fetch(API_ROOT + "/werewolf/live/sessions/" + sessionId + "/speeches?day=" + day);
      if (!res.ok) { renderSpeechList(); return; }
      var data = await res.json();
      if (data.speeches) {
        if (!dayPlayerHistories[day]) dayPlayerHistories[day] = {};
        var speeches = data.speeches;
        Object.keys(speeches).forEach(function (pid) {
          var pidNum = parseInt(pid);
          if (!isNaN(pidNum)) {
            dayPlayerHistories[day][pidNum] = speeches[pid];
          }
        });
      }
      renderSpeechList();
    } catch (err) {
      console.error("loadDaySpeeches error:", err);
      renderSpeechList();
    }
  }

  if (addDayBtn) {
    addDayBtn.addEventListener("click", function () {
      maxDay++;
      currentDay = maxDay;
      renderDayNav();
      setHint("已进入第" + currentDay + "天，记得在左侧阶段选择正确的游戏阶段。");
    });
  }

  /* =====================================================
     玩家卡片渲染 —— 增量更新优化
  ===================================================== */

  // 卡片元素缓存 {playerId: cardElement}
  var cardCache = {};

  function renderSpeechList() {
    if (!speechList) return;

    // 首次渲染或playerCount变化时全量构建
    var needsFullRebuild = !speechList.children.length || speechList.children.length !== playerCount;

    if (needsFullRebuild) {
      speechList.innerHTML = "";
      cardCache = {};
      for (var i = 1; i <= playerCount; i++) {
        var card = createPlayerCard(i);
        speechList.appendChild(card);
        cardCache[i] = card;
      }
    } else {
      // 增量更新：只更新变化的卡片
      for (var i = 1; i <= playerCount; i++) {
        updatePlayerCard(i, cardCache[i]);
      }
    }
  }

  function createPlayerCard(id) {
    var alive = playerAlive[id] !== false;
    var isSelf = id === myPlayerId;
    var isRecording = running && selectedPlayerId === id;
    var history = getPlayerHistory(id);
    var tags = playerStatusTags[id] || [];

    var card = document.createElement("div");
    card.className = "pcard" +
      (isRecording ? " is-recording" : "") +
      (isSelf ? " is-self" : "") +
      (!alive ? " is-dead" : "");
    card.setAttribute("data-pid", id);

    // 头部
    var header = document.createElement("div");
    header.className = "pcard-header";

    var avatar = document.createElement("div");
    avatar.className = "pcard-avatar";
    avatar.textContent = id;

    var name = document.createElement("div");
    name.className = "pcard-name";
    name.textContent = id + "号" + (isSelf ? "(我)" : "") + (isRecording ? " 🔴" : "");

    var eyeBtn = document.createElement("button");
    eyeBtn.className = "pcard-eye";
    eyeBtn.textContent = "👁";
    eyeBtn.addEventListener("click", (function (pid) {
      return function (e) { e.stopPropagation(); openFullSpeech(pid); };
    })(id));

    header.appendChild(avatar);
    header.appendChild(name);
    header.appendChild(eyeBtn);

    // 发言预览
    var body = document.createElement("div");
    body.className = "pcard-body" + (history.length === 0 ? " empty" : "");
    updateCardBody(body, id, history, isRecording);

    // 状态标签
    var tagsRow = document.createElement("div");
    tagsRow.className = "pcard-tags";
    renderTags(tagsRow, id, tags);

    // 概率条
    var probBar = document.createElement("div");
    probBar.className = "pcard-prob";
    var probFill = document.createElement("div");
    probFill.className = "pcard-prob-fill";
    var prob = latestProbabilities[id] || 0;
    probFill.style.width = prob + "%";
    probBar.appendChild(probFill);

    // 底部
    var footer = document.createElement("div");
    footer.className = "pcard-footer";

    var probText = document.createElement("span");
    probText.className = "pcard-prob-text";
    probText.textContent = alive ? ("狼" + prob + "%") : "出局";

    var micBtn = document.createElement("button");
    micBtn.className = "pcard-mic" + (isRecording ? " on" : "") + (!alive ? " dead-mic" : "");
    micBtn.textContent = isRecording ? "●" : "🎤";
    micBtn.addEventListener("click", (function (pid) {
      return function (e) { e.stopPropagation(); onPlayerMicClick(pid); };
    })(id));

    footer.appendChild(probText);
    footer.appendChild(micBtn);

    card.appendChild(header);
    card.appendChild(body);
    card.appendChild(tagsRow);
    card.appendChild(probBar);
    card.appendChild(footer);

    card.addEventListener("click", (function (pid) {
      return function () { openFullSpeech(pid); };
    })(id));

    return card;
  }

  /** 增量更新单个卡片 */
  function updatePlayerCard(id, card) {
    if (!card) return;
    var alive = playerAlive[id] !== false;
    var isSelf = id === myPlayerId;
    var isRecording = running && selectedPlayerId === id;
    var history = getPlayerHistory(id);
    var tags = playerStatusTags[id] || [];

    // 更新className
    var newClass = "pcard" +
      (isRecording ? " is-recording" : "") +
      (isSelf ? " is-self" : "") +
      (!alive ? " is-dead" : "");
    if (card.className !== newClass) card.className = newClass;

    // 更新名字
    var nameEl = card.querySelector(".pcard-name");
    if (nameEl) {
      var newName = id + "号" + (isSelf ? "(我)" : "") + (isRecording ? " 🔴" : "");
      if (nameEl.textContent !== newName) nameEl.textContent = newName;
    }

    // 更新头像
    var avatarEl = card.querySelector(".pcard-avatar");
    if (avatarEl && avatarEl.textContent !== String(id)) avatarEl.textContent = id;

    // 更新body
    var bodyEl = card.querySelector(".pcard-body");
    if (bodyEl) updateCardBody(bodyEl, id, history, isRecording);

    // 更新概率
    var prob = latestProbabilities[id] || 0;
    var probFill = card.querySelector(".pcard-prob-fill");
    if (probFill && probFill.style.width !== prob + "%") probFill.style.width = prob + "%";

    var probText = card.querySelector(".pcard-prob-text");
    if (probText) {
      var newProbText = alive ? ("狼" + prob + "%") : "出局";
      if (probText.textContent !== newProbText) probText.textContent = newProbText;
    }

    // 更新麦克风按钮
    var micBtn = card.querySelector(".pcard-mic");
    if (micBtn) {
      var newMicClass = "pcard-mic" + (isRecording ? " on" : "") + (!alive ? " dead-mic" : "");
      if (micBtn.className !== newMicClass) micBtn.className = newMicClass;
      var newMicText = isRecording ? "●" : "🎤";
      if (micBtn.textContent !== newMicText) micBtn.textContent = newMicText;
    }

    // 更新标签（标签变化较少，简化处理）
    var tagsRow = card.querySelector(".pcard-tags");
    if (tagsRow) renderTags(tagsRow, id, tags);
  }

  function updateCardBody(bodyEl, id, history, isRecording) {
    if (isRecording && isTranscribing) {
      bodyEl.textContent = "正在识别...";
      bodyEl.className = "pcard-body";
    } else if (isRecording) {
      bodyEl.textContent = "正在录音...";
      bodyEl.className = "pcard-body";
    } else if (history.length > 0) {
      var preview = history[history.length - 1];
      if (preview.length > 40) preview = preview.substring(0, 40) + "...";
      bodyEl.innerHTML = highlightKeywords(preview);
      bodyEl.className = "pcard-body";
    } else {
      var alive = playerAlive[id] !== false;
      bodyEl.textContent = alive ? "等待发言..." : "已出局";
      bodyEl.className = "pcard-body empty";
    }
  }

  function renderTags(container, id, tags) {
    // 只在标签数变化时重建
    var tagDefs = [
      { key: "good", label: "好人", activeClass: "on-good" },
      { key: "kill", label: "查杀", activeClass: "on-kill" },
      { key: "claim", label: "跳预", activeClass: "on-claim" },
      { key: "dead", label: "出局", activeClass: "on-dead" }
    ];

    // 简单策略：始终重建标签（标签数量少，开销可忽略）
    container.innerHTML = "";
    tagDefs.forEach(function (td) {
      var tag = document.createElement("span");
      var isOn = tags.indexOf(td.key) >= 0;
      tag.className = "pcard-tag" + (isOn ? " " + td.activeClass : "");
      tag.textContent = td.label;
      tag.setAttribute("data-tag", td.key);
      tag.setAttribute("data-pid", id);
      tag.addEventListener("click", (function (pid, tagKey) {
        return function (e) { e.stopPropagation(); toggleStatusTag(pid, tagKey); };
      })(id, td.key));
      container.appendChild(tag);
    });
  }

  /* =====================================================
     状态标签切换
  ===================================================== */
  function toggleStatusTag(playerId, tagKey) {
    if (!playerStatusTags[playerId]) playerStatusTags[playerId] = [];
    var tags = playerStatusTags[playerId];
    var idx = tags.indexOf(tagKey);

    if (tagKey === "dead") {
      if (idx >= 0) {
        tags.splice(idx, 1);
        playerAlive[playerId] = true;
      } else {
        tags.push(tagKey);
        playerAlive[playerId] = false;
        if (selectedPlayerId === playerId) pauseListening();
      }
    } else {
      if (idx >= 0) { tags.splice(idx, 1); } else { tags.push(tagKey); }
    }

    updateAliveCount();
    scheduleRender();
    var state = tagKey === "dead" ? (playerAlive[playerId] ? "存活" : "出局") : (idx >= 0 ? "取消" : "标记");
    setHint(playerId + "号 " + tagKey + " " + state);
  }

  function togglePlayerAlive(playerId) {
    playerAlive[playerId] = !playerAlive[playerId];
    updateAliveCount();
    scheduleRender();
    var state = playerAlive[playerId] ? "存活" : "出局";
    setHint(playerId + "号玩家已标记为" + state + "（双击切换）");
    if (!playerAlive[playerId] && selectedPlayerId === playerId) {
      pauseListening();
    }
  }

  function openFullSpeech(playerId) {
    if (!fullSpeechModal || !fullSpeechTitle || !fullSpeechList) return;
    fullSpeechTitle.textContent = playerId + "号玩家详情";
    var history = getPlayerHistory(playerId);
    var summaries = playerAISummaries[playerId] || [];
    var tags = playerStatusTags[playerId] || [];

    var html = "";

    if (tags.length > 0) {
      html += "<div style='display:flex;gap:4px;flex-wrap:wrap;margin-bottom:8px;'>";
      tags.forEach(function (t) {
        var colors = { good: "#25d08f", kill: "#ff7794", claim: "#ffbc2d", dead: "#718096" };
        html += "<span style='font-size:11px;padding:2px 8px;border-radius:6px;background:" +
          (colors[t] || "#9eb3d7") + "22;color:" + (colors[t] || "#9eb3d7") + ";'>" + t + "</span>";
      });
      html += "</div>";
    }

    if (summaries.length > 0) {
      html += "<div style='font-size:12px;font-weight:700;color:#25d08f;margin-bottom:4px;'>AI总结</div>";
      summaries.forEach(function (s) {
        html += "<div class='full-speech-item' style='color:#25d08f;'>" + s + "</div>";
      });
      html += "<div class='divider'></div>";
    }

    if (history.length === 0) {
      html += "<div class='full-speech-empty'>暂无发言记录</div>";
    } else {
      html += "<div style='font-size:12px;font-weight:700;color:#c8d8f8;margin-bottom:4px;'>发言记录 (" + history.length + "段)</div>";
      history.forEach(function (text) {
        html += "<div class='full-speech-item'>" + highlightKeywords(text) + "</div>";
      });
    }

    fullSpeechList.innerHTML = html;
    fullSpeechModal.classList.remove("hidden");
  }

  if (fullSpeechClose) {
    fullSpeechClose.addEventListener("click", function () {
      fullSpeechModal.classList.add("hidden");
    });
  }
  if (fullSpeechModal) {
    fullSpeechModal.addEventListener("click", function (e) {
      if (e.target === fullSpeechModal) fullSpeechModal.classList.add("hidden");
    });
  }

  async function requestGlobalSummary() {
    if (!sessionId) return;
    try {
      var res = await fetch(API_ROOT + "/werewolf/live/sessions/" + sessionId + "/summary", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          day: currentDay,
          myPlayerId: myPlayerId,
          myRoleHint: myRoleHint,
          playerHistories: getPlayerHistories(),
          voteRecords: voteRecords,
          skillLogs: skillLogs
        })
      });
      if (!res.ok) return;
      var data = await res.json();
      renderStrategyPanels(data);
      if (data.playerSummaries) {
        Object.keys(data.playerSummaries).forEach(function (pid) {
          var pidNum = parseInt(pid);
          if (!isNaN(pidNum)) {
            playerAISummaries[pidNum] = playerAISummaries[pidNum] || [];
            playerAISummaries[pidNum].push(data.playerSummaries[pid]);
          }
        });
      }
      if (data.globalSummary) {
        if (aiSummaryText) aiSummaryText.textContent = data.globalSummary;
      }
      scheduleRender();
      setHint("全局AI汇总完成！请查看AI分析页。");
    } catch (err) {
      console.error("全局汇总失败", err);
    }
  }

  /* =====================================================
     投票记录
  ===================================================== */
  function renderVoteSelects() {
    [voteFrom, voteTo].forEach(function (sel) {
      if (!sel) return;
      var wasVal = sel.value;
      sel.innerHTML = "<option value=''>-</option>";
      for (var i = 1; i <= playerCount; i++) {
        var opt = document.createElement("option");
        opt.value = i;
        opt.textContent = i + "号";
        sel.appendChild(opt);
      }
      if (wasVal) sel.value = wasVal;
    });
  }

  function renderVoteRecords() {
    if (!voteRecordList) return;
    var byDay = {};
    voteRecords.forEach(function (r) {
      var key = r.day;
      if (!byDay[key]) byDay[key] = [];
      byDay[key].push(r);
    });
    if (Object.keys(byDay).length === 0) {
      voteRecordList.innerHTML = "<div style='color:var(--subtext);font-size:12px;'>暂无投票记录</div>";
      return;
    }
    var html = "";
    Object.keys(byDay).sort().forEach(function (day) {
      html += "<div class='vote-round-title'>第" + day + "天</div>";
      var rows = byDay[day];
      html += "<div class='vote-row'>";
      rows.forEach(function (r) {
        html += "<span class='vote-chip'>" + r.from + "号</span>";
        html += "<span class='vote-chip arrow'>→</span>";
        html += "<span class='vote-chip target'>" + r.to + "号</span>";
        html += "<span style='margin-right:6px;'></span>";
      });
      html += "</div>";
    });
    voteRecordList.innerHTML = html;
  }

  if (voteAddBtn) {
    voteAddBtn.addEventListener("click", function () {
      var from = voteFrom ? voteFrom.value : "";
      var to = voteTo ? voteTo.value : "";
      if (!from || !to) {
        setHint("请选择投票者和被投票者");
        return;
      }
      voteRecords.push({ from: parseInt(from), to: parseInt(to), day: currentDay });
      renderVoteRecords();
      setHint(from + "号投票给" + to + "号，已记录。");
    });
  }

  /* =====================================================
     话术模板
  ===================================================== */
  var speechTabBtns = document.querySelectorAll(".speech-tab");
  speechTabBtns.forEach(function (btn) {
    btn.addEventListener("click", function () {
      speechTabBtns.forEach(function (b) { b.classList.remove("active"); });
      btn.classList.add("active");
      speechType = btn.getAttribute("data-type");
      renderSpeechContent();
    });
  });

  function renderSpeechContent() {
    if (!speechContent) return;
    var text = speechTemplates[speechType];
    if (!text) {
      speechContent.textContent = "暂无" + getSpeechTypeName(speechType) + "话术，等待AI分析...";
      return;
    }
    speechContent.textContent = text;
  }

  function getSpeechTypeName(type) {
    var map = { defense: "防御型", attack: "进攻型", tablewater: "表水型", identity: "身份特定" };
    return map[type] || type;
  }

  if (copySpeechBtn) {
    copySpeechBtn.addEventListener("click", function () {
      var text = speechContent ? speechContent.textContent : "";
      if (!text || text.includes("暂无")) {
        setHint("暂无可复制的话术");
        return;
      }
      navigator.clipboard.writeText(text).then(function () {
        copySpeechBtn.textContent = "已复制！";
        setTimeout(function () { copySpeechBtn.textContent = "复制话术"; }, 1500);
      }).catch(function () {
        setHint("复制失败，请手动选择文字复制。");
      });
    });
  }

  /* =====================================================
     概率图渲染
  ===================================================== */
  function renderProbabilityBars(items) {
    var list = Array.isArray(items) ? items : [];
    if (list.length === 0) {
      probabilityChart.innerHTML = "<div style='color:var(--subtext);font-size:12px;'>暂无概率数据</div>";
      return;
    }
    probabilityChart.innerHTML = "";
    list.forEach(function (bar) {
      var playerId = bar && bar.playerId ? bar.playerId : "?";
      var prob = bar && typeof bar.werewolfProbability === "number" ? bar.werewolfProbability : 0;
      prob = Math.max(0, Math.min(100, prob));
      latestProbabilities[playerId] = prob;

      var row = document.createElement("div");
      row.className = "prob-row";
      row.innerHTML =
        "<span>" + playerId + "号</span>" +
        "<div class='prob-track'><div class='prob-fill' style='width:" + prob + "%'></div></div>" +
        "<span>" + prob + "%</span>";
      probabilityChart.appendChild(row);
    });
    scheduleRender();
  }

  function findRoleProb(assessment, roleName) {
    if (!assessment || !Array.isArray(assessment.roleProbabilities)) return 0;
    var aliases = {
      "平民": ["平民", "村民"], "狼人": ["狼人", "狼"],
      "预言家": ["预言家"], "女巫": ["女巫"], "猎人": ["猎人"], "守卫": ["守卫"]
    };
    var expected = aliases[roleName] || [roleName];
    var found = assessment.roleProbabilities.find(function (item) {
      return item && item.role && expected.some(function (n) { return item.role === n; });
    });
    if (!found || typeof found.probability !== "number") return 0;
    return Math.max(0, Math.min(1, found.probability));
  }

  function renderRoleMatrix(playerAssessments) {
    var list = Array.isArray(playerAssessments) ? playerAssessments : [];
    if (list.length === 0) {
      roleMatrix.innerHTML = "<div style='color:var(--subtext);font-size:12px;'>暂无角色概率明细</div>";
      return;
    }
    var sorted = list.slice().sort(function (a, b) { return (a.playerId || 0) - (b.playerId || 0); });

    var roleDefs = [
      { name: "狼人", aliases: ["狼人", "狼"], color: "#ff6080", short: "狼" },
      { name: "预言家", aliases: ["预言家"], color: "#ffd83a", short: "预" },
      { name: "女巫", aliases: ["女巫"], color: "#c39eff", short: "巫" },
      { name: "猎人", aliases: ["猎人"], color: "#4fd8a0", short: "猎" },
      { name: "守卫", aliases: ["守卫"], color: "#6ecbff", short: "守" },
      { name: "平民", aliases: ["平民", "村民"], color: "#b0bec5", short: "民" }
    ];

    var html = "<div style='display:flex;align-items:center;gap:6px;margin-bottom:8px;padding-bottom:6px;border-bottom:1px solid rgba(255,255,255,0.06);'>";
    html += "<span style='font-size:10px;color:#7a96c4;min-width:30px;'>图例</span>";
    roleDefs.forEach(function (rd) {
      html += "<div style='flex:1;min-width:0;text-align:center;'>";
      html += "<span style='display:inline-block;width:6px;height:6px;border-radius:999px;background:" + rd.color + ";margin-right:3px;vertical-align:middle;'></span>";
      html += "<span style='font-size:10px;color:" + rd.color + ";'>" + rd.short + "</span>";
      html += "</div>";
    });
    html += "</div>";

    sorted.forEach(function (item) {
      var pid = item.playerId || "?";
      html += "<div style='display:flex;align-items:center;gap:6px;margin-bottom:6px;'>";
      html += "<span style='font-size:12px;font-weight:700;color:#ceddf9;min-width:30px;'>" + pid + "号</span>";

      roleDefs.forEach(function (rd) {
        var prob = Math.round(findRoleProb(item, rd.name) * 100);
        html += "<div style='flex:1;min-width:0;'>";
        html += "<div style='font-size:9px;color:" + rd.color + ";margin-bottom:1px;'>" + rd.short + prob + "%</div>";
        html += "<div style='height:3px;border-radius:999px;background:rgba(255,255,255,0.08);overflow:hidden;'>";
        html += "<div style='height:100%;width:" + prob + "%;background:" + rd.color + ";border-radius:999px;transition:width 0.5s ease;'></div>";
        html += "</div></div>";
      });

      html += "</div>";
    });
    roleMatrix.innerHTML = html;
  }

  function renderTextList(container, items) {
    if (!container) return;
    var list = Array.isArray(items) ? items.filter(Boolean) : [];
    if (list.length === 0) { container.innerHTML = "<li>暂无</li>"; return; }
    container.innerHTML = "";
    list.forEach(function (item) {
      var li = document.createElement("li");
      li.textContent = item;
      container.appendChild(li);
    });
  }

  function renderStrategyPanels(data) {
    data = data || {};
    renderProbabilityBars(data.probabilities || []);
    renderRoleMatrix(data.playerAssessments || []);
    if (voteAdviceText) {
      var currentVoteText = voteAdviceText.textContent || "";
      if (!currentVoteText || currentVoteText === "等待分析..." || currentVoteText === "AI正在分析投票建议...") {
        voteAdviceText.textContent = data.voteAdvice || "暂无稳定归票建议";
      }
    }
    renderTextList(votePointsList, data.votePoints || []);

    if (aiSummaryText) {
      if (!aiSummaryText.textContent || aiSummaryText.textContent === "AI正在分析中..." || aiSummaryText.textContent === "等待分析数据...") {
        aiSummaryText.textContent = data.summary || data.voteAdvice || "AI正在分析中...";
      }
    }
    if (mindMapArea) {
      var points = data.votePoints || [];
      if (points.length > 0) {
        mindMapArea.innerHTML = "<div style='font-size:12px;color:#9eb3d7;margin-bottom:4px;'>关键线索：</div>" +
          points.map(function (p) {
            return "<div style='font-size:12px;color:#dde8ff;padding:4px 0;border-bottom:1px solid rgba(255,255,255,0.05);'>├─ " + p + "</div>";
          }).join("");
      } else {
        mindMapArea.innerHTML = "";
      }
    }

    if (data.playerSummaries) {
      Object.keys(data.playerSummaries).forEach(function (pid) {
        var pidNum = parseInt(pid);
        if (!isNaN(pidNum)) {
          playerAISummaries[pidNum] = playerAISummaries[pidNum] || [];
          // 限制每个玩家的AI总结数量
          playerAISummaries[pidNum].push(data.playerSummaries[pid]);
          if (playerAISummaries[pidNum].length > 5) {
            playerAISummaries[pidNum] = playerAISummaries[pidNum].slice(-5);
          }
        }
      });
      scheduleRender();
    }

    updateSpeechTemplatesFromResponse(data);
  }

  /* =====================================================
     录音 & 识别（MediaRecorder + 后端 DashScope ASR）
  ===================================================== */

  function getSupportedMimeType() {
    var types = ["audio/webm;codecs=opus", "audio/webm", "audio/ogg;codecs=opus", "audio/mp4"];
    for (var i = 0; i < types.length; i++) {
      if (MediaRecorder.isTypeSupported(types[i])) return types[i];
    }
    return "";
  }

  function startMediaRecorder() {
    if (!audioStream) return;
    // 确保之前的recorder已清理
    if (mediaRecorder && mediaRecorder.state !== "inactive") {
      try { mediaRecorder.stop(); } catch(e) {}
    }
    audioChunks = [];
    var mimeType = getSupportedMimeType();
    var options = mimeType ? { mimeType: mimeType } : {};
    try {
      mediaRecorder = new MediaRecorder(audioStream, options);
    } catch (e) {
      console.error("MediaRecorder 创建失败", e);
      setHint("浏览器不支持录音功能");
      return;
    }

    // 3秒一个数据片段，平衡实时性和稳定性
    var timeslice = 3000;
    var chunkCount = 0;

    mediaRecorder.ondataavailable = function (e) {
      if (e.data && e.data.size > 0) {
        audioChunks.push(e.data);
        chunkCount++;
      }
    };

    mediaRecorder.onstop = function () {
      if (audioChunks.length === 0) {
        if (running) {
          setTimeout(function() { if (running) startMediaRecorder(); }, 300);
        }
        return;
      }
      var blob = new Blob(audioChunks, { type: audioChunks[0].type || "audio/webm" });
      audioChunks = [];
      chunkCount = 0;

      var duration = (Date.now() - recordingStartTime) / 1000;
      // 最低0.5秒即可识别，降低门槛
      if (duration < 0.5) {
        if (running) startMediaRecorder();
        return;
      }

      isTranscribing = true;
      scheduleRender();
      setStatus("语音识别中...");
      var reader = new FileReader();
      reader.onloadend = function () {
        var base64Audio = reader.result;
        callBackendAsr(base64Audio);
      };
      reader.onerror = function () {
        console.error("FileReader error");
        isTranscribing = false;
        setStatus("实时分析中");
        if (running) startMediaRecorder();
      };
      reader.readAsDataURL(blob);
    };

    mediaRecorder.onerror = function (e) {
      console.error("MediaRecorder error", e);
      isTranscribing = false;
      if (running) {
        setHint("录音出错，1秒后重试...");
        setTimeout(function () { if (running) startMediaRecorder(); }, 1000);
      }
    };

    // 监听录音暂停事件
    mediaRecorder.onpause = function() {
      console.log("MediaRecorder paused");
    };

    recordingStartTime = Date.now();
    lastVoiceAt = Date.now();
    try {
      mediaRecorder.start(timeslice);
      setStatus("录音中 ●");
    } catch (e) {
      console.error("mediaRecorder.start error", e);
      setHint("启动录音失败，请重试");
    }
  }

  function stopMediaRecorder() {
    if (mediaRecorder && mediaRecorder.state === "recording") {
      try { mediaRecorder.stop(); } catch (e) { console.warn("stopMediaRecorder", e); }
    }
  }

  async function callBackendAsr(base64Audio) {
    try {
      var res = await fetch(API_ROOT + "/werewolf/live/asr", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ audio: base64Audio })
      });
      if (!res.ok) throw new Error("ASR 请求失败：" + res.status);
      var data = await res.json();
      var text = data.text || "";
      if (text) {
        processTranscribedText(text);
      }
    } catch (err) {
      console.error("ASR error:", err);
      setHint("语音识别失败：" + err.message);
    } finally {
      isTranscribing = false;
      if (running) startMediaRecorder();
    }
  }

  function processTranscribedText(rawText) {
    var text = (rawText || "").replace(/\s+/g, " ").trim();
    if (!text) return;
    if (!selectedPlayerId) return;

    var history = getPlayerHistory(selectedPlayerId);
    history.push(text);
    // 限制每个玩家每天最多10段发言
    if (history.length > 10) {
      dayPlayerHistories[currentDay][selectedPlayerId] = history.slice(-10);
    }

    saveSpeechToBackend(text, selectedPlayerId);

    setHint(selectedPlayerId + "号发言已识别");
    scheduleRender();

    // 如果刚发言完的是"我"的前一位，自动触发AI话术推荐
    var prevPlayerId = myPlayerId === 1 ? playerCount : myPlayerId - 1;
    if (selectedPlayerId === prevPlayerId) {
      setTimeout(function () { triggerSpeechAdvice(true); }, 500);
    }
  }

  async function saveSpeechToBackend(text, speakerId) {
    if (!sessionId) return;
    try {
      await fetch(API_ROOT + "/werewolf/live/sessions/" + sessionId + "/speech", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          transcript: text,
          phase: phaseSelect ? phaseSelect.value : "白天发言",
          silenceSeconds: 0,
          speakerPlayerId: speakerId,
          myRoleHint: myRoleHint,
          day: currentDay
        })
      });
    } catch (err) {
      console.error("save-speech error:", err);
    }
  }

  function buildExtraPayload() {
    var aliveMap = {};
    var rolesMap = {};
    for (var i = 1; i <= playerCount; i++) {
      aliveMap[i] = playerAlive[i] !== false;
      if (playerRoles[i]) rolesMap[i] = playerRoles[i];
    }
    var skillLogsText = (skillLogs || []).map(function (s) {
      return typeof s === "string" ? s : (s && s.text ? s.text : "");
    }).filter(function (t) { return t; });
    return {
      skillLogs: skillLogsText,
      voteRecords: voteRecords || [],
      playerAlive: aliveMap,
      playerRoles: rolesMap,
      previousDaysSummary: buildPreviousDaysSummary(),
      totalPlayers: playerCount,
      roleComposition: roleComposition
    };
  }

  function buildPreviousDaysSummary() {
    var sb = [];
    for (var d = 1; d < currentDay; d++) {
      var dayHist = dayPlayerHistories[d];
      var hasContent = false;
      var daySb = ["\n=== 第" + d + "天发言 ==="];
      if (dayHist) {
        for (var pid = 1; pid <= playerCount; pid++) {
          var hist = dayHist[pid];
          if (hist && hist.length > 0) {
            hasContent = true;
            hist.forEach(function (text) { daySb.push(pid + "号：" + text); });
          }
        }
      }
      var daySkills = skillLogs.filter(function (log) {
        return log && (typeof log === "object" ? log.day === d : false);
      });
      if (daySkills.length > 0) {
        hasContent = true;
        daySb.push("【技能记录】");
        daySkills.forEach(function (log) {
          var text = typeof log === "string" ? log : (log && log.text ? log.text : "");
          daySb.push("· " + text);
        });
      }
      var dayVotes = voteRecords.filter(function (r) { return r.day === d; });
      if (dayVotes.length > 0) {
        hasContent = true;
        daySb.push("【投票记录】");
        dayVotes.forEach(function (r) { daySb.push(r.from + "号 → " + r.to + "号"); });
      }
      if (hasContent) { sb.push(daySb.join("\n")); }
    }
    var allDeaths = [];
    for (var pid = 1; pid <= playerCount; pid++) {
      if (playerAlive[pid] === false) allDeaths.push(pid + "号");
    }
    if (allDeaths.length > 0) {
      sb.push("\n【全局已出局玩家】" + allDeaths.join("、"));
    }
    return sb.join("\n");
  }

  function cancelAllAiRequests() {
    if (abortController) {
      try { abortController.abort(); } catch (e) { /* ignore */ }
      abortController = null;
    }
    queue = [];
    sending = false;
    setQueueHint();
  }

  async function triggerSpeechAdvice(autoTriggered) {
    if (!sessionId) return;
    cancelAllAiRequests();
    if (autoTriggered) {
      setHint("前一位发言完毕，正在自动生成话术推荐...");
    } else {
      setHint("正在生成话术推荐...");
    }
    if (speechContent) speechContent.textContent = "AI正在生成话术...";
    switchTab("analysis");
    var extra = buildExtraPayload();
    queue.push({
      transcript: "",
      phase: phaseSelect ? phaseSelect.value : "白天发言",
      silenceSeconds: 0,
      speakerPlayerId: myPlayerId,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      analysisType: "speechAdvice",
      day: currentDay,
      skillLogs: extra.skillLogs,
      voteRecords: extra.voteRecords,
      playerAlive: extra.playerAlive,
      playerRoles: extra.playerRoles,
      previousDaysSummary: extra.previousDaysSummary,
      totalPlayers: extra.totalPlayers
    });
    setQueueHint();
    consumeQueue();
  }

  async function triggerRoleAnalysis() {
    if (!sessionId) return;
    cancelAllAiRequests();
    setHint("正在分析角色概率...");
    if (aiSummaryText) aiSummaryText.textContent = "AI正在分析角色概率...";
    switchTab("analysis");
    var extra = buildExtraPayload();
    queue.push({
      transcript: "",
      phase: phaseSelect ? phaseSelect.value : "白天发言",
      silenceSeconds: 0,
      speakerPlayerId: null,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      analysisType: "roleAnalysis",
      day: currentDay,
      skillLogs: extra.skillLogs,
      voteRecords: extra.voteRecords,
      playerAlive: extra.playerAlive,
      playerRoles: extra.playerRoles,
      previousDaysSummary: extra.previousDaysSummary,
      totalPlayers: extra.totalPlayers
    });
    setQueueHint();
    consumeQueue();
  }

  async function triggerRoundSummary() {
    if (!sessionId) return;
    cancelAllAiRequests();
    setHint("正在生成回合总结...");
    if (aiSummaryText) aiSummaryText.textContent = "AI正在总结本轮发言...";
    switchTab("analysis");
    var extra = buildExtraPayload();
    queue.push({
      transcript: "",
      phase: phaseSelect ? phaseSelect.value : "白天发言",
      silenceSeconds: 0,
      speakerPlayerId: null,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      analysisType: "roundSummary",
      day: currentDay,
      skillLogs: extra.skillLogs,
      voteRecords: extra.voteRecords,
      playerAlive: extra.playerAlive,
      playerRoles: extra.playerRoles,
      previousDaysSummary: extra.previousDaysSummary,
      totalPlayers: extra.totalPlayers
    });
    setQueueHint();
    consumeQueue();
  }

  async function triggerVoteAdvice() {
    if (!sessionId) return;
    cancelAllAiRequests();
    setHint("正在生成投票建议...");
    if (voteAdviceText) voteAdviceText.textContent = "AI正在分析投票建议...";
    switchTab("analysis");
    var extra = buildExtraPayload();
    queue.push({
      transcript: "",
      phase: phaseSelect ? phaseSelect.value : "投票阶段",
      silenceSeconds: 0,
      speakerPlayerId: null,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      analysisType: "voteAdvice",
      day: currentDay,
      skillLogs: extra.skillLogs,
      voteRecords: extra.voteRecords,
      playerAlive: extra.playerAlive,
      playerRoles: extra.playerRoles,
      previousDaysSummary: extra.previousDaysSummary,
      totalPlayers: extra.totalPlayers
    });
    setQueueHint();
    consumeQueue();
  }

  async function triggerDataPanelAnalysis() {
    if (!sessionId) return;
    cancelAllAiRequests();
    setHint("正在计算角色概率...");
    if (roleMatrix) roleMatrix.innerHTML = "<div style='color:var(--subtext);font-size:12px;'>AI正在计算角色概率...</div>";
    var extra = buildExtraPayload();
    queue.push({
      transcript: "",
      phase: phaseSelect ? phaseSelect.value : "白天发言",
      silenceSeconds: 0,
      speakerPlayerId: null,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      analysisType: "dataPanel",
      day: currentDay,
      skillLogs: extra.skillLogs,
      voteRecords: extra.voteRecords,
      playerAlive: extra.playerAlive,
      playerRoles: extra.playerRoles,
      previousDaysSummary: extra.previousDaysSummary,
      totalPlayers: extra.totalPlayers
    });
    setQueueHint();
    consumeQueue();
  }

  async function initAudioResources() {
    if (resourcesReady) return;
    try {
      audioStream = await navigator.mediaDevices.getUserMedia({
        audio: {
          echoCancellation: true,
          noiseSuppression: true,
          autoGainControl: true
        }
      });
    } catch (err) {
      console.error("getUserMedia error:", err);
      setHint("无法获取麦克风权限，请在浏览器设置中允许麦克风访问");
      return;
    }
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      var source = audioCtx.createMediaStreamSource(audioStream);
      analyserNode = audioCtx.createAnalyser();
      analyserNode.fftSize = 2048;
      source.connect(analyserNode);
      setupVad();
      resourcesReady = true;
    } catch (err) {
      console.error("AudioContext error:", err);
      setHint("音频初始化失败，请刷新页面重试");
    }
  }

  function setupVad() {
    if (!analyserNode) return;
    var arr = new Uint8Array(analyserNode.fftSize);
    // 优化：VAD轮询从250ms降到400ms，降低CPU占用
    vadTimer = window.setInterval(function () {
      if (!running || !selectedPlayerId) return;
      analyserNode.getByteTimeDomainData(arr);
      var sum = 0;
      for (var i = 0; i < arr.length; i++) {
        var v = (arr[i] - 128) / 128;
        sum += v * v;
      }
      var rms = Math.sqrt(sum / arr.length);
      var now = Date.now();
      if (rms > voiceThreshold) lastVoiceAt = now;
      if (mediaRecorder && mediaRecorder.state === "recording" && now - lastVoiceAt >= silenceMillis && !isTranscribing) {
        stopMediaRecorder();
      }
    }, 400);
  }

  async function createSessionIfNeeded() {
    if (sessionId) return;
    var body = {
      sessionUuid: sessionUuid,
      totalPlayers: playerCount,
      gameMode: gameMode,
      myPlayerId: myPlayerId,
      myRoleHint: myRoleHint,
      roleComposition: roleComposition
    };
    var res = await fetch(API_ROOT + "/werewolf/live/sessions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    if (!res.ok) throw new Error("创建会话失败：" + res.status);
    var data = await res.json();
    sessionId = data.sessionId;
  }

  async function startRecordingForPlayer(playerId) {
    selectedPlayerId = playerId;
    setStatus("实时分析中");
    await initAudioResources();
    if (!resourcesReady) {
      setHint("音频资源未就绪，请允许麦克风权限后重试");
      return;
    }
    if (audioCtx && audioCtx.state === "suspended") {
      try { await audioCtx.resume(); } catch (e) { console.warn("resume AudioContext failed", e); }
    }
    await createSessionIfNeeded();
    running = true;
    lastVoiceAt = Date.now();
    startMediaRecorder();
    setHint("正在为 " + playerId + " 号录音。安静后自动识别。");
    scheduleRender();
  }

  function pauseListening() {
    running = false;
    stopMediaRecorder();
    setStatus("已暂停");
    if (recBtnLabel) recBtnLabel.textContent = "REC";
    if (statusDot) statusDot.style.background = "rgba(100,120,160,0.4)";
    scheduleRender();
  }

  async function consumeQueue() {
    if (sending || queue.length === 0 || !sessionId) return;
    sending = true;
    abortController = new AbortController();
    setQueueHint();
    var payload = queue.shift();
    var analysisType = payload.analysisType || "roundSummary";
    var targetElement;
    if (analysisType === "speechAdvice") {
      targetElement = speechContent;
    } else if (analysisType === "voteAdvice") {
      targetElement = voteAdviceText;
    } else if (analysisType === "dataPanel") {
      targetElement = null;
    } else {
      targetElement = aiSummaryText;
    }
    try {
      var res = await fetch(API_ROOT + "/werewolf/live/sessions/" + sessionId + "/stream-chunks", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
        signal: abortController.signal
      });
      if (!res.ok) throw new Error("后端异常：" + res.status);

      var reader = res.body.getReader();
      var decoder = new TextDecoder("utf-8");
      var buffer = "";
      var fullAnalysisText = "";

      while (true) {
        var result = await reader.read();
        if (result.done) break;
        buffer += decoder.decode(result.value, { stream: true });

        var parts = buffer.split("\n\n");
        buffer = parts.pop();

        for (var i = 0; i < parts.length; i++) {
          var eventType = "";
          var eventData = "";
          var lines = parts[i].split("\n");
          for (var j = 0; j < lines.length; j++) {
            if (lines[j].indexOf("event:") === 0) {
              eventType = lines[j].slice(6).trim();
            } else if (lines[j].indexOf("data:") === 0) {
              eventData = lines[j].slice(5).trim();
            }
          }

          if (eventType === "text" && eventData) {
            try {
              var parsed = JSON.parse(eventData);
              if (parsed.content) fullAnalysisText += parsed.content;
            } catch (e) {
              fullAnalysisText += eventData;
            }
            // 节流：限制DOM更新频率
            var now = Date.now();
            if (targetElement && (now - lastStreamUpdate >= STREAM_THROTTLE_MS)) {
              lastStreamUpdate = now;
              targetElement.innerHTML = formatThreeBlocks(fullAnalysisText);
            }
          } else if (eventType === "complete" && eventData) {
            try {
              var data = JSON.parse(eventData);
              if (typeof data.elapsedSeconds === "number") elapsedSeconds = data.elapsedSeconds;
              renderStrategyPanels(data);
              updateSpeechTemplatesFromResponse(data);
            } catch (e) {
              console.error("Parse complete event error:", e);
            }
          }
        }
      }

      // 流结束后最终更新一次DOM
      if (targetElement) {
        targetElement.innerHTML = formatThreeBlocks(fullAnalysisText);
      }

      setHint("AI分析已完成。");
      if (analysisType === "speechAdvice" && fullAnalysisText) {
        speechTemplates["defense"] = fullAnalysisText;
        renderSpeechContent();
      }
    } catch (err) {
      if (err.name === "AbortError") {
        setHint("已取消之前的AI分析。");
        return;
      }
      console.error(err);
      setHint("分析请求失败：" + err.message);
    } finally {
      sending = false;
      abortController = null;
      setQueueHint();
      if (queue.length > 0) consumeQueue();
    }
  }

  function updateSpeechTemplatesFromResponse(data) {
    if (!data) return;
    if (data.suggestedSpeech) {
      speechTemplates["defense"] = data.suggestedSpeech;
    }
    if (data.werewolfTalks && data.werewolfTalks.length > 0) {
      var attackLines = data.werewolfTalks.filter(function (t) {
        return t && (t.includes("归票") || t.includes("逻辑") || t.includes("号玩家"));
      });
      if (attackLines.length > 0) speechTemplates["attack"] = attackLines.join("\n\n");
      else speechTemplates["attack"] = data.werewolfTalks.slice(0, 2).join("\n\n");
    }
    if (data.votePoints && data.votePoints.length > 0) {
      speechTemplates["tablewater"] = data.votePoints.join("\n· ");
    }
    if (myRoleHint !== "未知") {
      speechTemplates["identity"] = buildIdentitySpeech(myRoleHint, data);
    }
    renderSpeechContent();
  }

  function buildIdentitySpeech(role, data) {
    var voteTarget = "";
    if (data && data.voteAdvice) {
      var m = data.voteAdvice.match(/(\d+)\s*号/);
      if (m) voteTarget = m[1];
    }
    if (role === "预言家") {
      return "我是预言家，昨晚验了X号，金水/查杀。我的警徽流先5后7，" +
        "如果我死了，警徽给金水，没验出金水就撕警徽。" +
        (voteTarget ? "\n目前建议归票" + voteTarget + "号。" : "");
    }
    if (role === "女巫") {
      return "我是女巫，昨晚的情况我已经知道了。今天我会配合预言家的指引行动。" +
        (voteTarget ? "\n目前建议配合归票" + voteTarget + "号。" : "");
    }
    if (role === "猎人") {
      return "我是猎人，我有开枪权。如果我被放逐，我会开枪带走我最怀疑的狼人。" +
        "大家在决定放逐我之前请三思。";
    }
    if (role === "守卫") {
      return "我是守卫，昨晚守护了自己/预言家。今天我会根据场上形势继续守护关键角色。";
    }
    if (role === "平民") {
      return "我是平民，没有任何技能，我只能通过发言逻辑来判断谁是狼人。" +
        "我上轮投票给X是因为他的发言前后矛盾，这轮我认为" +
        (voteTarget ? voteTarget + "号" : "X号") + "更可疑，建议大家关注。";
    }
    if (role === "狼人" || role === "狼王") {
      return "（狼人话术）混淆视线，假装分析，引导好人内斗。" +
        "归票方向可选择威胁最大的神职，注意不要表现得太积极。" +
        (voteTarget ? "\n当前建议推动归票" + voteTarget + "号（利用好人的怀疑）。" : "");
    }
    return "请根据当前游戏状况随机应变。AI建议：" + (data && data.suggestedSpeech ? data.suggestedSpeech : "继续收集信息");
  }

  /* =====================================================
     事件绑定
  ===================================================== */
  function onPlayerMicClick(playerId) {
    if (!playerAlive[playerId]) {
      setHint(playerId + "号已出局，无法录音。");
      return;
    }
    if (running && selectedPlayerId === playerId) {
      pauseListening();
      return;
    }
    if (running && selectedPlayerId && selectedPlayerId !== playerId) {
      running = false;
      stopMediaRecorder();
      isTranscribing = false;
    }
    try {
      startRecordingForPlayer(playerId);
    } catch (err) {
      console.error(err);
      setHint("启动录音失败：" + err.message);
    }
  }

  if (recBtn) {
    recBtn.addEventListener("click", function () {
      if (selectedPlayerId) {
        onPlayerMicClick(selectedPlayerId);
      } else {
        onPlayerMicClick(1);
      }
    });
  }

  if (myRoleSelect) {
    myRoleSelect.addEventListener("change", function (e) {
      myRoleHint = (e.target && e.target.value) || "未知";
      updateWolfProbVisibility();
      setHint("我的身份已设置为" + myRoleHint + "");
    });
  }

  if (endGameBtn) {
    endGameBtn.addEventListener("click", function () {
      cleanup();
      sessionStorage.setItem("werewolf_sessionId", sessionId || "");
      sessionStorage.setItem("werewolf_playerCount", playerCount);
      sessionStorage.setItem("werewolf_myRole", myRoleHint);
      sessionStorage.setItem("werewolf_elapsed", elapsedSeconds);
      window.location.href = "/game-over.html";
    });
  }

  var speechAdviceBtn = document.getElementById("speechAdviceBtn");
  if (speechAdviceBtn) {
    speechAdviceBtn.addEventListener("click", function () { triggerSpeechAdvice(); });
  }

  var roleAnalysisBtn = document.getElementById("roleAnalysisBtn");
  if (roleAnalysisBtn) {
    roleAnalysisBtn.addEventListener("click", function () { triggerRoleAnalysis(); });
  }

  var roundSummaryBtn = document.getElementById("roundSummaryBtn");
  if (roundSummaryBtn) {
    roundSummaryBtn.addEventListener("click", function () { triggerRoundSummary(); });
  }

  var voteAdviceBtn = document.getElementById("voteAdviceBtn");
  if (voteAdviceBtn) {
    voteAdviceBtn.addEventListener("click", function () { triggerVoteAdvice(); });
  }

  var dataPanelAnalysisBtn = document.getElementById("dataPanelAnalysisBtn");
  if (dataPanelAnalysisBtn) {
    dataPanelAnalysisBtn.addEventListener("click", function () { triggerDataPanelAnalysis(); });
  }

  /* =====================================================
     计时器
  ===================================================== */
  function startTimer() {
    if (timerJob) return;
    timerJob = window.setInterval(function () {
      elapsedSeconds += 1;
      if (running && selectedPlayerId) {
        playerSeconds[selectedPlayerId] = (playerSeconds[selectedPlayerId] || 0) + 1;
      }
      if (timerText) timerText.textContent = fmtTimer(elapsedSeconds);
    }, 1000);
  }

  /* =====================================================
     清理
  ===================================================== */
  function cleanup() {
    running = false;
    stopMediaRecorder();
    if (vadTimer) { window.clearInterval(vadTimer); vadTimer = null; }
    if (timerJob) { window.clearInterval(timerJob); timerJob = null; }
    if (audioStream) { audioStream.getTracks().forEach(function (t) { t.stop(); }); audioStream = null; }
    if (audioCtx) { audioCtx.close(); audioCtx = null; }
    resourcesReady = false;
  }

  window.addEventListener("beforeunload", cleanup);

  // 可见性变化时优化性能：页面不可见时暂停VAD
  document.addEventListener("visibilitychange", function() {
    if (document.hidden && running) {
      // 页面不可见时暂停录音（节省资源）
      pauseListening();
      setHint("页面切换到后台，已暂停录音");
    }
  });

  function renderCountOptions() {
    // analysis页不提供人数切换
  }

  /* =====================================================
     启动
  ===================================================== */
  init();
})();
