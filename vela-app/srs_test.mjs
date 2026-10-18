import srs from "./src/common/srs.js";
let pass = 0, fail = 0;
function ok(cond, name) { if (cond) { pass++; } else { fail++; console.log("FAIL:", name); } }

// 1. 基础更新逻辑（默认 gap 24h → base 8h=0.33天）
let w = { w: "apple", m: "苹果" };
srs.norm(w);
let r1 = srs.updateWord(w, "know", { rt: 500, gap: 24 });
ok(r1.action === "know" && r1.rt === 500, "know 带 rt");
ok(Math.abs(w.i - 8 / 24) < 0.001 && w.c === 1, "know 首次间隔=日频基础(8h)");
// 2. 反应时间影响间隔
let fast = { w: "a", i: 3, e: 2.5 };
srs.norm(fast);
srs.updateWord(fast, "know", { rt: 500, gap: 24 });   // 快→加成
let slow = { w: "b", i: 3, e: 2.5 };
srs.norm(slow);
srs.updateWord(slow, "know", { rt: 6000, gap: 24 });  // 慢→打折
ok(fast.i > slow.i, "反应快间隔>反应慢间隔 (fast=%s slow=%s)".replace("%s", fast.i).replace("%s", slow.i));
// 3. dwell 影响
let dwellLong = { w: "c", i: 3, e: 2.5 };
srs.norm(dwellLong);
srs.updateWord(dwellLong, "know", { rt: 1500, dwell: 5000, gap: 24 }); // 认真学→推后
let dwellShort = { w: "d", i: 3, e: 2.5 };
srs.norm(dwellShort);
srs.updateWord(dwellShort, "know", { rt: 1500, dwell: 200, gap: 24 }); // 扫一眼→提前
ok(dwellLong.i > dwellShort.i, "释义停留久间隔>扫一眼 (long=%s short=%s)".replace("%s", dwellLong.i).replace("%s", dwellShort.i));
// 4. forget 按使用频率重置（不再固定1天）：高频 3h → 0.125 天
let fw = { w: "e", i: 5, e: 2.0, lvl: 5 };
srs.norm(fw);
srs.updateWord(fw, "forget", { rt: 9000, gap: 3 });
ok(Math.abs(fw.i - 3 / 24) < 0.001 && fw.wc === 1, "高频用户 forget 重置3小时(非1天)");
// 4b. 低频用户 forget → 2 天
let fw2 = { w: "e2", i: 5, e: 2.0, lvl: 5 };
srs.norm(fw2);
srs.updateWord(fw2, "forget", { rt: 9000, gap: 100 });
ok(Math.abs(fw2.i - 2) < 0.001, "低频用户 forget 重置2天");
// 5. record 带 dwell
let w2 = { w: "f", m: "x" };
srs.norm(w2);
let r2 = srs.updateWord(w2, "fuzzy", { rt: 1200, dwell: 4000, gap: 24 });
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
// 8. estimateGapHours：最近记录间隔中位数（上午~中午=3h 高频）
let recs = [
  { t: "2026-10-18 08:00:00" }, { t: "2026-10-18 11:00:00" },
  { t: "2026-10-18 14:00:00" }, { t: "2026-10-18 17:00:00" }
];
let gap = srs.estimateGapHours(recs);
ok(Math.abs(gap - 3) < 0.01, "高频使用 gap≈3h (got %s)".replace("%s", gap));
// 8b. baseIntervalHours 映射
ok(srs.baseIntervalHours(3) === 3, "gap3h→base3h(上午看中午巩固)");
ok(srs.baseIntervalHours(24) === 8, "gap24h→base8h");
ok(srs.baseIntervalHours(100) === 48, "gap100h→base48h");
// 9. 高频整体效应：上午看不会的词，中午前到期
let hw = { w: "h", m: "x" };
srs.norm(hw);
srs.updateWord(hw, "forget", { rt: 2000, gap: 3 });   // 上午选了不会
let r3 = srs.retention(hw);                            // 立刻算：刚复习 retention≈1
hw.next = hw.next - 3 * 3600000;                       // 模拟3小时后
let r4 = srs.retention(hw);
ok(r4 < 0.9, "3小时后该词到期(r=%s<0.9)".replace("%s", r4.toFixed(2)));
// 10. 动态选词基础：候选不重复
let uniq = new Set(batch.map(x => x.w));
ok(uniq.size === batch.length, "pickBatch 候选不重复");
// 11. 选"不会"后 urgency 实时上升 → 下轮更优先（动态选词的依据）
let uw = { w: "u1", m: "x", lvl: 3, i: 5, e: 2.5, c: 2, wc: 0 };
srs.norm(uw);
let uBefore = srs.urgency(uw);
srs.updateWord(uw, "forget", { gap: 3 });
let uAfter = srs.urgency(uw);
ok(uAfter > uBefore, "选不会后 urgency 上升(%s→%s)".replace("%s", uBefore.toFixed(2)).replace("%s", uAfter.toFixed(2)));
// 12. 快忘的词(长时间未复习)urgency 最高 → 优先被动态选中
let aging = { w: "old", m: "x", lvl: 1, i: 1, e: 1.5, c: 0, wc: 0, next: Date.now() - 30 * 86400000 };
srs.norm(aging);
let fresh = { w: "new", m: "x", lvl: 1, i: 3, e: 2.5, c: 1, wc: 0, next: Date.now() };
srs.norm(fresh);
ok(srs.urgency(aging) > srs.urgency(fresh), "遗忘线附近的词比刚复习的词紧急(动态选词优先)");
// 13. estimateGapHours：记录太少返回 null（用默认）
ok(srs.estimateGapHours([{ t: "2026-10-18 08:00:00" }]) === null, "单条记录 gap=null(用默认)");
// 14. 全库动态选词 pickNextAll：最紧急的词优先
let lib = [
  { w: "aging", m: "x", lvl: 1, i: 1, e: 1.5, c: 0, wc: 2, next: Date.now() - 20 * 86400000 },
  { w: "fresh", m: "x", lvl: 4, i: 10, e: 2.8, c: 5, wc: 0, next: Date.now() }
];
let n1 = srs.pickNextAll(lib, {});
ok(n1 && n1.w === "aging", "pickNextAll 选最紧急的词");
// 15. 排除已答：不会重复选
let n2 = srs.pickNextAll(lib, { aging: true });
ok(n2 && n2.w === "fresh", "pickNextAll 排除已答");
// 16. 词库学完一遍 → null
let n3 = srs.pickNextAll(lib, { aging: true, fresh: true });
ok(n3 === null, "词库全部答完 → null(done)");
// 17. 每个词独立调度：答完"不会"的词下次更优先（不依赖批次）
let solo = [
  { w: "w1", m: "x", lvl: 2, i: 3, e: 2.5, c: 1, wc: 0, next: Date.now() },
  { w: "w2", m: "x", lvl: 2, i: 3, e: 2.5, c: 1, wc: 0, next: Date.now() }
];
let picked = srs.pickNextAll(solo, {});
let pickedWord = picked.w;
srs.updateWord(picked, "forget", { gap: 3 });  // 这个词答错，状态更新
let nextPick = srs.pickNextAll(solo, { [pickedWord]: true });  // 排除刚答的
let other = solo.find(x => x.w !== pickedWord);
ok(nextPick.w === other.w, "答完排除后选另一个");
// 重新模拟第二轮：答错的词间隔重置 → urgency 上升 → 再次全库选词时优先
let round2 = srs.pickNextAll(solo, { [other.w]: true });
ok(round2.w === pickedWord, "第二轮该词独立地最紧急(单独计算)");
console.log("结果: %d 通过 / %d 失败", pass, fail);
if (fail > 0) process.exit(1);
