// ============================================================================
//  Gram DNS events — глобальная лента всех операций с .gram доменами в канал.
//  Постит: минт, перебитую ставку, конец аукциона (выигрыш минта), листинг на
//  GetGems, продажу на GetGems, старт аукциона на GetGems (ресейл), и дублирует
//  новые тайм-локи GRM. Красиво, с кликабельными кнопками-ссылками.
//
//  Бот должен быть АДМИНОМ канала DNS_CHANNEL.
//
//  ── КОНТРАКТ API (его ты делаешь отдельно) ──────────────────────────────────
//  GET DNS_EVENTS_URL → { "events": [ {                                       │
//    id:        "уникальная строка (txhash/nft+action+ts) — для дедупа",      │
//    type:      "mint|outbid|auction_end|list|sale|auction_start",            │
//    domain:    "passio.gram",            slug: "passio",                     │
//    address:   "EQ... (адрес NFT-домена) — для ссылок getgems/radar",        │
//    priceTon:  1.5,   priceUsd: 2.36,    // цена (продажа/листинг)           │
//    bidTon:    315,   minBidTon: 50,     // аукцион: текущая/стартовая ставка │
//    seller:    "easynftcase.ton",  buyer: "passio-gram.ton",                 │
//    owner:     "UQ...",                  // владелец (mint/auction)          │
//    endsAt:    1719500000,               // unix конца аукциона (опц)        │
//    feePct:    5,     method: "Fixed Price",                                 │
//    utime:     1719400000,               // время события (сортировка)       │
//    getgems:   "https://getgems.io/nft/EQ...",  // опц, иначе строим сами    │
//    radar:     "https://gramradar.org/?q=passio" // опц                      │
//  } ] }                                                                       │
//  Достаточно вернуть последние ~50 событий; бот сам дедупит по id.           │
// ============================================================================

const fs = require("fs");
const path = require("path");
const tg = require("./telegram");

const SITE = (process.env.GRAMRADAR_API || "https://gramradar.org").replace(/\/+$/, "");
const DNS_EVENTS_URL = process.env.DNS_EVENTS_URL || `${SITE}/api/dns/events`;
const DNS_CHANNEL = process.env.DNS_CHANNEL || "@gramdnsevents";
const TICK_MS = Number(process.env.DNS_WATCH_MS || 45000);
const STATE_FILE = path.join(__dirname, "..", "data", "dns-events-state.json");

// ссылки для кнопок
const GETGEMS = "https://getgems.io";
const MINIAPP = process.env.DNS_MINIAPP_URL || "https://t.me/gramradardns_bot";
const LOCK_URL = process.env.LOCK_MINIAPP_URL || "https://t.me/gramradardns_bot?startapp=lock";
const LOTTERY_URL = process.env.LOTTERY_URL || "https://t.me/gramradardns_bot?startapp=pool";
const CHAT_URL = process.env.DNS_CHAT_URL || "https://t.me/gramradar";
const INCLUDE_LOCKS = process.env.DNS_INCLUDE_LOCKS !== "0";

let timer = null;
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const fmtN = (n) => Number(n || 0).toLocaleString("ru-RU");
const fmtTon = (n) => (n == null ? null : `${fmtN(n)} TON`);
const fmtUsd = (n) => (n == null ? "" : ` (~$${n < 100 ? Number(n).toFixed(2) : fmtN(Math.round(n))})`);

function fmtLeft(sec) {
  if (!sec || sec <= 0) return "завершён";
  const d = Math.floor(sec / 86400), h = Math.floor((sec % 86400) / 3600), m = Math.floor((sec % 3600) / 60);
  if (d > 0) return `${d}д ${h}ч`;
  if (h > 0) return `${h}ч ${m}м`;
  return `${m}м`;
}

function links(ev) {
  const getgems = ev.getgems || (ev.address ? `${GETGEMS}/nft/${ev.address}` : `${GETGEMS}`);
  const radar = ev.radar || `${SITE}/?q=${encodeURIComponent(ev.slug || ev.domain || "")}`;
  return { getgems, radar };
}

// единый «подвал» с кнопками: GetGems · Gram Radar · Лок · Розыгрыш · Чат
function buttons(ev) {
  const { getgems, radar } = links(ev);
  return {
    inline_keyboard: [
      [{ text: "💎 GetGems", url: getgems }, { text: "📡 Gram Radar", url: radar }],
      [{ text: "🔒 Лок", url: LOCK_URL }, { text: "🎲 Розыгрыш", url: LOTTERY_URL }, { text: "💬 Чат", url: CHAT_URL }],
    ],
  };
}

function priceLine(ev) {
  if (ev.priceTon == null) return "";
  return `💎 Цена: <b>${fmtTon(ev.priceTon)}</b>${fmtUsd(ev.priceUsd)}\n`;
}

// карточка по типу события
function render(ev) {
  const name = `🌐 <b>${tg.esc(ev.domain || ev.slug + ".gram")}</b>\n`;
  const fee = ev.feePct ? `\n🧾 Комиссия: ${ev.feePct}%` : "";
  switch (ev.type) {
    case "mint":
      return name + `🆕 <b>Домен заминчен</b>\n` +
        (ev.owner ? `👤 Владелец: <code>${tg.fmtWallet(ev.owner)}</code>\n` : "") + priceLine(ev);
    case "outbid":
      return name + `⚡ <b>Ставку перебили</b>\n` +
        `💰 Новая ставка: <b>${fmtTon(ev.bidTon)}</b>\n` +
        (ev.endsAt ? `⏳ До конца: ${fmtLeft(ev.endsAt - Math.floor(Date.now() / 1000))}\n` : "");
    case "auction_end":
      return name + `🏆 <b>Аукцион завершён</b>\n` +
        (ev.owner ? `👑 Победитель: <code>${tg.fmtWallet(ev.owner)}</code>\n` : "") +
        (ev.bidTon != null ? `💰 Цена: <b>${fmtTon(ev.bidTon)}</b>${fmtUsd(ev.priceUsd)}\n` : "");
    case "list":
      return name + `🏷 <b>Выставлен на продажу</b>\n` + priceLine(ev) + (ev.method ? `📦 ${ev.method}` : "") + fee;
    case "auction_start":
      return name + `🔨 <b>Аукцион начат</b>\n` +
        `🚀 Старт: <b>${fmtTon(ev.minBidTon ?? ev.bidTon)}</b>${fmtUsd(ev.priceUsd)}\n` +
        (ev.endsAt ? `⏳ До конца: ${fmtLeft(ev.endsAt - Math.floor(Date.now() / 1000))}` : "") + fee;
    case "sale":
      return name + `💚 <b>Домен продан</b>\n` + priceLine(ev) +
        (ev.seller || ev.buyer ? `🔁 <code>${tg.esc(ev.seller || "?")}</code> ➜ <code>${tg.esc(ev.buyer || "?")}</code>\n` : "") +
        (ev.method ? `📦 ${ev.method}` : "") + fee;
    default:
      return name + `ℹ️ Событие: ${tg.esc(ev.type)}`;
  }
}

function loadState() { try { return JSON.parse(fs.readFileSync(STATE_FILE, "utf8")); } catch (_) { return {}; } }
function saveState(s) {
  try { fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true }); fs.writeFileSync(STATE_FILE, JSON.stringify(s)); }
  catch (e) { console.warn("[dns] save:", e.message); }
}
async function getJSON(url) {
  const res = await fetch(url, { signal: AbortSignal.timeout(15000) });
  return res.json();
}
async function post(text, reply_markup) {
  if (!DNS_CHANNEL) return;
  try { await tg.tg("sendMessage", { chat_id: DNS_CHANNEL, text, parse_mode: "HTML", disable_web_page_preview: true, reply_markup }); }
  catch (e) { console.warn("[dns] send:", e.message); }
}

// дата лока в МСК
function fmtMsk(ts) {
  const d = new Date((ts + 3 * 3600) * 1000), p = (n) => String(n).padStart(2, "0");
  return `${p(d.getUTCDate())}.${p(d.getUTCMonth() + 1)}.${d.getUTCFullYear()} ${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}

async function tick() {
  const st = loadState();
  const seen = new Set(st.seen || []);
  const firstRun = !st.seen;

  // 1) DNS-события из API
  let events = [];
  try {
    const data = await getJSON(DNS_EVENTS_URL);
    events = Array.isArray(data && data.events) ? data.events : [];
  } catch (e) { console.warn("[dns] fetch:", e.message); }

  // 2) локи (дублируем в этот же канал)
  if (INCLUDE_LOCKS) {
    try {
      const lf = await getJSON(`${SITE}/api/lock/feed`);
      for (const f of (lf && lf.feed) || []) {
        if (!f.txHash) continue;
        events.push({
          id: "lock:" + f.txHash, type: "lock", utime: f.utime, amount: f.amount,
          buyer: f.buyer, unlockAt: f.unlockAt, tranches: f.tranches,
          getgems: GETGEMS, radar: `${SITE}/#lock`,
        });
      }
    } catch (_) {}
  }

  // сортируем старые→новые, дедупим по id
  events.sort((a, b) => (a.utime || 0) - (b.utime || 0));

  // первый запуск: гасим всю историю кроме последних N (без спама)
  if (firstRun) {
    const N = Number(process.env.DNS_BACKFILL || 0);
    const ids = events.map((e) => e.id).filter(Boolean);
    for (let i = 0; i < Math.max(0, ids.length - N); i++) seen.add(ids[i]);
  }

  for (const ev of events) {
    if (!ev.id || seen.has(ev.id)) continue;
    if (ev.type === "lock") {
      const vest = ev.tranches > 1 ? `\n⛓ Вестинг: ${ev.tranches} частей` : "";
      await post(
        `🔒 <b>Новый лок GRM</b>\n\n💎 <b>${fmtN(ev.amount)} GRM</b>\n` +
          `👤 <code>${tg.fmtWallet(ev.buyer)}</code>\n🔓 Разлок: <b>${fmtMsk(ev.unlockAt)}</b> (МСК)${vest}`,
        buttons(ev)
      );
    } else {
      await post(render(ev), buttons(ev));
    }
    seen.add(ev.id);
    await sleep(150);
  }

  st.seen = [...seen].slice(-1000);
  saveState(st);
}

function startDnsEvents() {
  if (timer) return;
  if (!process.env.DNS_EVENTS_URL && !process.env.DNS_FORCE) {
    console.log("[dns] DNS_EVENTS_URL не задан — лента DNS-событий выключена (задай URL своего API)");
    return;
  }
  console.log("[dns] events watcher started → " + DNS_CHANNEL);
  tick();
  timer = setInterval(tick, TICK_MS);
}

module.exports = { startDnsEvents, tick, render };
