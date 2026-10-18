import srs from "./src/common/srs.js";
let pass = 0, fail = 0;
function ok(cond, name) { if (cond) { pass++; } else { fail++; console.log("FAIL:", name); } }

// 1. 基础更新逻辑
let w = { w: "apple", m: "苹果" };
srs.norm(w);
let r1 = srs.updateWord(w, "know", { rt: 500 });
ok(r1.action === "know" && r1.rt === 500, "know 带 rt");
ok(w.i === 1 && w.c === 1, "know 首次间隔1");
// 2. 反应时间影响间隔
let fast = { w: "a", i: 3, e: 2.5 };
srs.norm(fast);
srs.updateWord(fast, "know", { rt: 500 });   // 快→加成
let slow = { w: "b", i: 3, e: 2.5 };
srs.norm(slow);
srs.updateWord(slow, "know", { rt: 6000 });  // 慢→打折
ok(fast.i > slow.i, "反应快间隔>反应慢间隔 (fast=%s slow=%s)".replace("%s", fast.i).replace("%s", slow.i));
// 3. dwell 影响
let dwellLong = { w: "c", i: 3, e: 2.5 };
srs.norm(dwellLong);
srs.updateWord(dwellLong, "know", { rt: 1500, dwell: 5000 }); // 认真学→推后
let dwellShort = { w: "d", i: 3, e: 2.5 };
srs.norm(dwellShort);
srs.updateWord(dwellShort, "know", { rt: 1500, dwell: 200 }); // 扫一眼→提前
ok(dwellLong.i > dwellShort.i, "释义停留久间隔>扫一眼 (long=%s short=%s)".replace("%s", dwellLong.i).replace("%s", dwellShort.i));
// 4. forget 重置
let fw = { w: "e", i: 5, e: 2.0, lvl: 5 };
srs.norm(fw);
srs.updateWord(fw, "forget", { rt: 9000 });
ok(fw.i === 1 && fw.wc === 1, "forget 重置1天+错次+1");
// 5. record 带 dwell
let w2 = { w: "f", m: "x" };
srs.norm(w2);
let r2 = srs.updateWord(w2, "fuzzy", { rt: 1200, dwell: 4000 });
ok(r2.dwell === 4000 && r2.rt === 1200, "record 带 rt+dwell");
// 6. 不会的反复问：长时间未复习+错过多的词紧急度高（会被优先选）
let g = { w: "g", i: 1, e: 1.5, lvl: 0, wc: 3, next: Date.now() - 10 * 86400000 };
srs.norm(g);
let due = srs.urgency(g) > 2;
ok(due, "遗忘线+熟练度低+错过多的词紧急度高");
// 7. pickBatch 正常
let words = [];
for (let i = 0; i < 20; i++) words.push({ w: "w" + i, m: "m" + i, lvl: i % 3, i: i % 5, e: 2.5, c: 0, wc: i % 2 });
let batch = srs.pickBatch(words, 10);
ok(batch.length === 10, "pickBatch 10 词");
console.log("结果: %d 通过 / %d 失败", pass, fail);
if (fail > 0) process.exit(1);
