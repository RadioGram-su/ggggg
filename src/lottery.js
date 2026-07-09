// Уведомления о розыгрышах «Призового пула» — в ЛИЧКУ подписчикам бота
// (всем, кто нажал /start). Постит: старт раунда, закрытие ставок, итог.
// У постов — кнопка «Участвовать» в мини-апп (вкладка пула).

const fs = require("fs");
const path = require("path");
const tg = require("./telegram");
const subs = require("./subscribers");

const SITE = (process.env.GRAMRADAR_API || "https://gramradar.org").replace(/\/+$/, "");
const TICK_MS = Number(process.env.LOTTERY_WATCH_MS || 30000);
const STATE_FILE = path.join(__dirname, "..", "data", "lottery-state.json");

const MINIAPP_URL = process.env.LOTTERY_MINIAPP_URL || "https://t.me/gramradardns_bot?startapp=lottery";
const partBtn = { inline_keyboard: [[{ text: "🎟 Участвовать", url: MINIAPP_URL }]] };

// Канал, куда дублируются уведомления + фид покупок (бот должен быть админом).
function deriveChannel(url) {
  const m = String(url || "").match(/t\.me\/([A-Za-z0-9_]+)/);
  return m ? "@" + m[1] : "";
}
const BURN_CHANNEL = process.env.LOTTERY_BURN_CHANNEL || deriveChannel(process.env.LOTTERY_BURN_CHANNEL_URL) || "@grmholdersburn";

let timer = null;

function ticketWord(n) {
  const m = n % 10, h = n % 100;
  if (m === 1 && h !== 11) return "билет";
  if (m >= 2 && m <= 4 && (h < 10 || h >= 20)) return "билета";
  return "билетов";
}

async function postChannel(text, extra = {}) {
  if (!BURN_CHANNEL) return;
  try {
    await tg.tg("sendMessage", { chat_id: BURN_CHANNEL, text, parse_mode: "HTML", disable_web_page_preview: true, ...extra });
  } catch (e) {
    console.warn("[lottery] channel send:", e.message);
  }
}

// Уведомление о событии раунда: и подписчикам в личку, и в канал.
async function notifyAll(text, extra = {}) {
  await broadcast(text, extra);
  await postChannel(text, extra);
}

function loadState() {
  try { return JSON.parse(fs.readFileSync(STATE_FILE, "utf8")); } catch (_) { return {}; }
}
function saveState(s) {
  try {
    fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
    fs.writeFileSync(STATE_FILE, JSON.stringify(s));
  } catch (e) { console.warn("[lottery] save:", e.message); }
}

async function getJSON(p) {
  const res = await fetch(`${SITE}${p}`, { signal: AbortSignal.timeout(15000) });
  return res.json();
}

function fmtLeft(sec) {
  if (sec <= 0) return "завершён";
  if (sec < 3600) return `${Math.floor(sec / 60)}м ${sec % 60}с`;
  const h = Math.floor(sec / 3600), m = Math.floor((sec % 3600) / 60);
  return `${h}ч ${m}м`;
}
const fmtN = (n) => Number(n).toLocaleString("ru-RU");
// USD-метка для суммы GRM по текущей цене: " (≈ $0.13)" или "" если цены нет.
function usdTag(grm, price) {
  if (!price || price <= 0) return "";
  const v = grm * price;
  const s = v >= 0.1 ? v.toFixed(2) : v.toFixed(3);
  return ` (≈ $${s})`;
}

// Рассылка всем подписчикам в личку. Заблокировавших — удаляем из списка.
async function broadcast(text, extra = {}) {
  const ids = subs.all();
  let ok = 0;
  for (const id of ids) {
    try {
      await tg.tg("sendMessage", {
        chat_id: id,
        text,
        parse_mode: "HTML",
        disable_web_page_preview: true,
        ...extra,
      });
      ok++;
    } catch (e) {
      const m = e.message || String(e);
      if (/blocked|chat not found|deactivated|kicked|user is deactivated/i.test(m)) {
        subs.remove(id);
      } else {
        console.warn("[lottery] send", id, m);
      }
    }
    await new Promise((r) => setTimeout(r, 40));
  }
  console.log(`[lottery] broadcast → ${ok}/${ids.length}`);
  return ids.length; // считаем «выполнено», даже если 0 подписчиков
}

// Пулы розыгрышей. Каждый — со своим состоянием и меткой.
const POOLS = [
  { key: "hour", label: "📅 Ежедневный" },
  { key: "week", label: "📅 7-дневный" },
  { key: "month", label: "🗓 30-дневный" },
];
function poolState(st, key) {
  if (!st.pools) st.pools = {};
  if (!st.pools[key]) st.pools[key] = {};
  return st.pools[key];
}

async function tickPool(pool) {
  let round;
  try { round = await getJSON("/api/lottery/round?pool=" + pool.key); } catch (e) { return; }
  if (!round || round.error || !round.roundId) return;

  const st = loadState();
  const ps = poolState(st, pool.key);
  const now = Math.floor(Date.now() / 1000);
  const tag = ` <i>${pool.label}</i>`;

  // 1) Новый раунд стартовал
  if (round.open && ps.announcedRound !== round.roundId) {
    await notifyAll(
      `🎲 <b>Новый розыгрыш стартовал!</b>${tag}\n\n` +
        `Цена билета: <b>${fmtN(round.ticketPrice)} GRM</b>${usdTag(round.ticketPrice, round.grmUsd)}\n` +
        `Победителю — <b>90%</b> банка · сжигается <b>10%</b> 🔥\n` +
        `До розыгрыша: <b>${fmtLeft(round.endTime - now)}</b>\n` +
        `Ставки закрываются за 5 мин до конца.\n\n` +
        `⚖️ Победитель — случайный билет on-chain. Каждый билет = +1 шанс.`,
      { reply_markup: partBtn }
    );
    ps.announcedRound = round.roundId;
    ps.betsClosedAnnounced = false;
    ps.roundTickets = 0;
    ps.lastNotifiedTickets = 0;
    saveState(st);
  }

  if (round.open && ps.announcedRound === round.roundId && (round.totalTickets || 0) !== ps.roundTickets) {
    ps.roundTickets = round.totalTickets || 0;
    saveState(st);
  }

  // 1.5) Фид покупок В КАНАЛ
  if (round.open && (round.totalTickets || 0) > (ps.lastNotifiedTickets || 0)) {
    let px = [];
    try { px = await getJSON("/api/lottery/purchases?pool=" + pool.key); } catch (_) {}
    if (Array.isArray(px)) {
      const seen = new Set(ps.seenPurchases || []);
      const fresh = px.filter((p) => p && p.txHash && !seen.has(p.txHash)).reverse();
      for (const p of fresh) {
        const tv = `https://tonviewer.com/transaction/${p.txHash}`;
        await postChannel(
          `🎟 <b>${tg.fmtWallet(p.buyer)}</b> купил <b>${p.tickets}</b> ${ticketWord(p.tickets)} ` +
            `· ${fmtN(p.amount)} GRM${usdTag(p.amount, round.grmUsd)}${tag}\n` +
            `Раунд #${round.roundId}: <b>${round.totalTickets}</b> билетов · <b>${round.players || 0}</b> кошельков · банк <b>${fmtN(round.pot)} GRM</b>${usdTag(round.pot, round.grmUsd)}\n` +
            `<a href="${tv}">Транзакция</a>`,
          { reply_markup: partBtn }
        );
        seen.add(p.txHash);
        await new Promise((r) => setTimeout(r, 60));
      }
      ps.seenPurchases = [...seen].slice(-300);
      ps.lastNotifiedTickets = round.totalTickets || 0;
      saveState(st);
    }
  }

  // 2) Ставки закрыты
  if (round.open && now >= round.betClose && ps.announcedRound === round.roundId && !ps.betsClosedAnnounced) {
    await notifyAll(
      `🔒 <b>Ставки закрыты</b>${tag}\n\n` +
        `Раунд #${round.roundId}: банк <b>${fmtN(round.pot)} GRM</b>${usdTag(round.pot, round.grmUsd)} · ${round.totalTickets} билетов · ${round.players || 0} кошельков\n` +
        `До розыгрыша осталось ~${fmtLeft(round.endTime - now)}.`
    );
    ps.betsClosedAnnounced = true;
    saveState(st);
  }

  // 3) Розыгрыш завершён
  if (!round.open && ps.announcedRound && ps.settledAnnounced !== ps.announcedRound) {
    let last = null;
    try { last = await getJSON("/api/lottery/last?pool=" + pool.key); } catch (_) {}
    if (ps.roundTickets > 0 && (!last || !last.winner)) return;

    if (last && last.refunded) {
      // в раунде был ровно 1 кошелёк — ставка возвращена целиком, без сжигания
      const tvTx = `https://tonviewer.com/transaction/${last.txHash}`;
      await notifyAll(
        `↩️ <b>Раунд #${ps.announcedRound} завершён</b>${tag}\n\n` +
          `В раунде участвовал только <b>один кошелёк</b> — играть не с кем, поэтому ставка ` +
          `<b>${fmtN(last.won)} GRM</b> возвращена полностью, без сжигания.\n\n` +
          `<a href="${tvTx}">Транзакция возврата</a>`,
        { reply_markup: partBtn }
      );
    } else if (last && last.winner) {
      const tvTx = `https://tonviewer.com/transaction/${last.txHash}`;
      const tvWin = `https://tonviewer.com/${last.winner}`;
      const burnUsd = last.burnedUsd != null ? ` (≈ $${last.burnedUsd})` : "";
      const wins = Array.isArray(last.winners) && last.winners.length
        ? last.winners
        : [{ addr: last.winner, amt: last.won, usd: last.wonUsd }];
      const medals = ["🥇", "🥈", "🥉"];
      let body;
      if (wins.length > 1) {
        body = wins
          .map((w, i) => `${medals[i] || "🏅"} <code>${tg.fmtWallet(w.addr)}</code> — <b>${fmtN(w.amt)} GRM</b>${w.usd != null ? ` (≈ $${w.usd})` : ""}`)
          .join("\n");
      } else {
        const w = wins[0];
        body = `Победитель: <code>${tg.fmtWallet(w.addr)}</code>\n` +
          `Выигрыш: <b>${fmtN(w.amt)} GRM</b>${w.usd != null ? ` (≈ $${w.usd})` : ""}`;
      }
      await notifyAll(
        `🏆 <b>Розыгрыш #${ps.announcedRound} завершён!</b>${tag}\n\n` +
          body + `\n` +
          `Сожжено: <b>${fmtN(last.burned)} GRM</b> 🔥${burnUsd}\n\n` +
          `<a href="${tvTx}">Транзакция розыгрыша</a> · <a href="${tvWin}">Кошелёк победителя</a>`,
        { reply_markup: partBtn }
      );
    } else {
      await notifyAll(
        `🔔 Раунд #${ps.announcedRound}${tag} завершён без участников. Следующий — скоро!`,
        { reply_markup: partBtn }
      );
    }
    ps.settledAnnounced = ps.announcedRound;
    saveState(st);
  }
}

async function tick() {
  for (const p of POOLS) {
    try { await tickPool(p); } catch (e) { console.warn("[lottery] tick", p.key, e.message); }
  }
}

function startLottery() {
  if (timer) return;
  console.log("[lottery] notifications started (DM to subscribers)");
  tick();
  timer = setInterval(tick, TICK_MS);
}

module.exports = { startLottery, tick };
