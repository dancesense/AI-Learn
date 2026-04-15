(function () {
  var dateText = document.getElementById("dateText");
  var startBtn = document.getElementById("startBtn");

  var now = new Date();
  var dateStr = now.getFullYear() + "年" + (now.getMonth() + 1) + "月" + now.getDate() + "日";
  dateText.textContent = dateStr;

  startBtn.addEventListener("click", function () {
    window.location.href = "/analysis.html";
  });
})();
