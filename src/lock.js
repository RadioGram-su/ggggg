// Уведомления о новых тайм-локах GRM в канал @grmlocked.
// Поллит /api/lock/feed, постит новые локи: сумма, кошелёк, дата разлока, tx, всего заперто.
// Бот должен быть АДМИНОМ канала @grmlocked.

const fs = require("fs");
const path = require("path");
const tg = require("./telegram");

const SITE = (process.env.GRAMRADAR_API || "https://gramradar.org").replace(/\/+$/, "");
const TICK_MS = Number(process.env.LOCK_WATCH_MS || 60000);
const STATE_FILE = path.join(__dirname, "..", "data", "lock-state.json");
const LOCK_CHANNEL = process.env.LOCK_CHANNEL || "@grmlocked";
const MINIAPP_URL = process.env.LOCK_MINIAPP_URL || "https://t.me/gramradardns_bot?startapp=lock";
const lockBtn = { inline_keyboard: [[{ text: "🔒 Запереть свои GRM", url: MINIAPP_URL }]] };

let timer = null;
const fmtN = (n) => Number(n).toLocaleString("ru-RU");
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// дата в МСК (UTC+3)
function fmtMsk(ts) {
  const d = new Date((ts + 3 * 3600) * 1000), p = (n) => String(n).padStart(2, "0");
  return `${p(d.getUTCDate())}.${p(d.getUTCMonth() + 1)}.${d.getUTCFullYear()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}

function loadState() { try { return JSON.parse(fs.readFileSync(STATE_FILE, "utf8")); } catch (_) { return {}; } }
function saveState(s) {
  try { fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true }); fs.writeFileSync(STATE_FILE, JSON.stringify(s)); }
  catch (e) { console.warn("[lock] save:", e.message); }
}
async function getJSON(p) {
  const res = await fetch(`${SITE}${p}`, { signal: AbortSignal.timeout(15000) });
  return res.json();
}
async function post(text) {
  if (!LOCK_CHANNEL) return;
  try { await tg.tg("sendMessage", { chat_id: LOCK_CHANNEL, text, parse_mode: "HTML", disable_web_page_preview: true, reply_markup: lockBtn }); }
  catch (e) { console.warn("[lock] channel send:", e.message); }
}

async function tick() {
  let data;
  try { data = await getJSON("/api/lock/feed"); } catch (_) { return; }
  const feed = Array.isArray(data && data.feed) ? data.feed : [];
  if (!feed.length) return;

  const st = loadState();
  const seen = new Set(st.seen || []);
  // на первом запуске объявляем ПОСЛЕДНИЕ N локов, остальную историю гасим (без спама)
  if (!st.seen) {
    const N = Number(process.env.LOCK_BACKFILL || 3);
    const sorted = [...feed].sort((a, b) => (b.utime || 0) - (a.utime || 0)); // новые первыми
    for (let i = N; i < sorted.length; i++) if (sorted[i].txHash) seen.add(sorted[i].txHash);
  }
  const fresh = feed.filter((f) => f.txHash && !seen.has(f.txHash)).reverse(); // старые→новые
  for (const f of fresh) {
    const tv = `https://tonviewer.com/transaction/${f.txHash}`;
    const vest = f.tranches > 1 ? `\n⛓ Вестинг: выдача <b>${f.tranches}</b> частями` : "";
    await post(
      `🔒 <b>НОВЫЙ ЛОК GRM</b>\n\n` +
        `💎 Сумма: <b>${fmtN(f.amount)} GRM</b>\n` +
        `👤 Кошелёк: <code>${tg.fmtWallet(f.buyer)}</code>\n` +
        `🔓 Разлок: <b>${fmtMsk(f.unlockAt)}</b> (МСК)${vest}\n\n` +
        `📊 Всего заперто комьюнити: <b>${fmtN(Math.round((data.lockedGrm || 0)))} GRM</b>\n` +
        `<a href="${tv}">Транзакция</a>`
    );
    seen.add(f.txHash);
    await sleep(150);
  }
  st.seen = [...seen].slice(-500);
  saveState(st);
}

function startLockWatch() {
  if (timer) return;
  console.log("[lock] watcher started → " + LOCK_CHANNEL);
  tick();
  timer = setInterval(tick, TICK_MS);
}

module.exports = { startLockWatch, tick };
