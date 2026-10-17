// 池鱼单词 - 手环端遗忘曲线算法（简化 SM-2 + 指数遗忘预测）
// 设计目标：熟练度低的词多问、预测快忘的词优先问、结合使用时间综合评判
const DAY = 86400000

function nowTs() { return Date.now() }
function dayStr(ts) {
  var d = new Date(ts)
  var p = function (n) { return (n < 10 ? '0' : '') + n }
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) +
    ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds())
}

// 归一化一个词的字段
function norm(w) {
  if (!w) return null
  w.w = w.w || ''
  w.m = w.m || ''
  w.e = (typeof w.e === 'number' && w.e > 0) ? w.e : 2.5   // 难度系数 ease
  w.i = (typeof w.i === 'number' && w.i >= 0) ? w.i : 0    // 当前间隔（天）
  w.lvl = (typeof w.lvl === 'number') ? Math.max(0, Math.min(8, w.lvl)) : 0 // 熟练度 0-8
  w.c = (typeof w.c === 'number') ? w.c : 0                // 答对次数
  w.wc = (typeof w.wc === 'number') ? w.wc : 0             // 答错次数
  w.next = (typeof w.next === 'number') ? w.next : nowTs() // 上次复习时间戳
  return w
}

// 记忆保持率 R(t) = exp(-t/S)：S 为稳定性（由间隔与难度估计），t 为距上次复习的天数
function retention(w) {
  var days = (nowTs() - w.next) / DAY
  if (days <= 0) return 1
  var stability = Math.max(0.3, w.i * 0.5 + w.e * 0.2)
  return Math.exp(-days / stability)
}

// 一个词的综合紧急度：到期（R 低）优先、熟练度低优先、错过多的优先
function urgency(w) {
  var R = retention(w)
  var dueScore = Math.max(0, 1 - R) * 2          // 0~2，越快到遗忘线越高
  var lvlScore = 1 / (w.lvl + 1)                  // 熟练度低 → 高
  var missScore = w.wc > 0 ? Math.min(1, w.wc * 0.3) : 0
  return dueScore + lvlScore * 0.8 + missScore
}

// 一轮选词：到期优先，数量不足时按紧急度补足；带少量随机避免顺序固定
function pickBatch(words, n) {
  var now = nowTs()
  var list = []
  for (var i = 0; i < words.length; i++) {
    var w = norm(words[i])
    w._u = urgency(w)
    w._r = retention(w)
    list.push(w)
  }
  list.sort(function (a, b) {
    if (Math.abs(a._r - b._r) > 0.12) return a._r - b._r  // 先按遗忘概率升序（快忘的在前）
    return b._u - a._u                                     // 再按紧急度
  })
  var batch = list.slice(0, Math.min(n, list.length))
  // 轻微乱序，避免永远是同一顺序
  for (var j = batch.length - 1; j > 0; j--) {
    var k = Math.floor(Math.random() * (j + 1))
    var t = batch[j]; batch[j] = batch[k]; batch[k] = t
  }
  return batch
}

// 更新一个词：action = know | forget | fuzzy
function updateWord(w, action) {
  norm(w)
  w.c = w.c || 0
  w.wc = w.wc || 0
  if (action === 'know') {
    w.c++
    w.lvl = Math.min(8, w.lvl + 1)
    if (w.i <= 0) w.i = 1
    else if (w.i === 1) w.i = 3
    else w.i = Math.round(w.i * w.e)
    w.e = Math.min(3.0, w.e + 0.1)
  } else if (action === 'fuzzy') {
    w.i = Math.max(1, Math.round(w.i * 0.5))
    w.e = Math.max(1.3, w.e - 0.1)
    w.lvl = Math.max(0, w.lvl - 1)
  } else { // forget
    w.wc++
    w.i = 1
    w.e = Math.max(1.3, w.e - 0.2)
    w.lvl = Math.max(0, w.lvl - 2)
  }
  w.next = nowTs()
  return {
    word: w.w,
    action: action,
    t: dayStr(w.next)
  }
}

export default {
  norm: norm,
  retention: retention,
  urgency: urgency,
  pickBatch: pickBatch,
  updateWord: updateWord
}
