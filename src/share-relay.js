// Релей фото-карточек Gram Play для @gramradardns_bot.
// Мини-апп «Gram Radar Play» открывает ИМЕННО этот бот, а Telegram привязывает
// prepared inline message (savePreparedInlineMessage → tg.shareMessage) к боту
// мини-аппа. Поэтому карточку-приглашение должен готовить ЭТОТ бот, а не lustword.
//
// Поток: аппка → backend (очередь Redis) → этот бот забирает /gamebot/share/pending →
//   savePreparedInlineMessage (свой токен) → /gamebot/share/result → аппка зовёт shareMessage(id).
//
// ENV (панель bothost этого бота):
//   LW_API=https://gramradar.org/lw
//   GAME_API_SECRET=<тот же секрет, что в backend/.env и у lustword>
//   BOT_USERNAME=gramradardns_bot   (для кнопки «Войти» в карточке)

const telegram = require("./telegram");

const clean = (s) => String(s || "").replace(/[^\x21-\x7E]/g, "");
let LW_API = clean(process.env.LW_API || "https://gramradar.org/lw").replace(/\/+$/, "");
LW_API = LW_API.replace(/^http(s?)[=:]\/\//, "http$1://");
if (LW_API && !/^https?:\/\//.test(LW_API)) LW_API = "https://" + LW_API.replace(/^\/+/, "");
const SECRET = clean(process.env.GAME_API_SECRET || process.env.GRAMRADAR_BOT_SECRET || process.env.BOT_API_SECRET);
const BOT_USERNAME = clean(process.env.BOT_USERNAME) || "gramradardns_bot";

async function api(path, body) {
  const url = clean(LW_API + "/gamebot" + path);
  const r = await fetch(url, {
    method: body ? "POST" : "GET",
    headers: { "X-Game-Secret": SECRET, "content-type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!r.ok) {
    let d = "";
    try { const j = await r.json(); d = (j && (j.error || j.message)) || ""; } catch (_) {}
    throw new Error(r.status + " " + path + (d ? " " + d : ""));
  }
  return r.json();
}

function shareResultFor(job) {
  const roomId = clean(String(job.roomId || ""));
  // ВНИМАНИЕ: в startapp Telegram допускает ТОЛЬКО [A-Za-z0-9_-]. Символ «~» ломал ссылку
  // (START_PARAM_INVALID). Реф-код приглашающего кладём через «_»: roomId — это UUID без
  // подчёркиваний, поэтому клиент однозначно делит `searoom_<uuid>_<ref>` → комната + реферал.
  const refCode = clean(String(job.ref || "")).replace(/[^A-Za-z0-9]/g, "");
  const refSuffix = refCode ? "_" + refCode : "";
  if (job.game === "check") {
    // Подарочный чек: картинка = /card/<imgId> (клиентский рендер), кнопка → активация чека.
    const imgId = clean(String(job.imgId || ""));
    const img = LW_API + "/card/" + imgId;
    return {
      type: "photo", id: "chk" + Date.now(),
      photo_url: img, thumb_url: img,
      caption: "🎁 <b>Тебе подарочный чек в Gram Play!</b>\nОткрой и забери приз 👇",
      parse_mode: "HTML",
      reply_markup: { inline_keyboard: [[{ text: "🎁 Открыть чек", url: "https://t.me/" + BOT_USERNAME + "?startapp=check_" + roomId }]] },
    };
  }
  if (job.game === "card") {
    // Карточка профиля: картинка = /card/<imgId>, кнопка ведёт в игру (реф-код если есть).
    const imgId = clean(String(job.imgId || ""));
    const img = LW_API + "/card/" + imgId;
    const startapp = job.ref ? "ref_" + clean(String(job.ref)) : "play";
    return {
      type: "photo", id: "card" + Date.now(),
      photo_url: img, thumb_url: img,
      caption: "🎙️ <b>Я в LAST WORD — Gram Play!</b>\nЗдесь за последнее слово платят реальными GRM. Заходи, забери банк 👇",
      parse_mode: "HTML",
      reply_markup: { inline_keyboard: [[{ text: "🎮 Играть в Gram Play", url: "https://t.me/" + BOT_USERNAME + "?startapp=" + startapp }]] },
    };
  }
  if (job.game === "poker") {
    // Приглашение за покерный стол: картинка = нарисованная карточка (/card/<imgId>),
    // кнопка ведёт за стол с реф-суффиксом: poker_<tableId>_<ref>.
    const imgId = clean(String(job.imgId || ""));
    const img = LW_API + "/card/" + imgId;
    return {
      type: "photo", id: "pkr" + Date.now(),
      photo_url: img, thumb_url: img,
      caption: "♠️ <b>Тебя ждут за покерным столом!</b>\n\n"
        + "🔥 Техасский Холдем на реальных GRM — прямо в Telegram\n"
        + "💰 Дожми соперника и забери весь банк\n"
        + "🆓 Новичкам — 2 000 фишек на тренировку, играй бесплатно\n"
        + "⚡ Провабли-фейр: честность каждой раздачи проверяема\n\n"
        + "👇 Место за столом свободно — успей сесть",
      parse_mode: "HTML",
      reply_markup: { inline_keyboard: [[{ text: "♠️ Сесть за стол", url: "https://t.me/" + BOT_USERNAME + "?startapp=poker_" + roomId + refSuffix }]] },
    };
  }
  if (job.game === "seabattle") {
    const img = LW_API + "/card/room/seabattle/" + roomId;
    return {
      type: "photo", id: "sea" + Date.now(),
      photo_url: img, thumb_url: img,
      caption: "⚓ <b>Тебя вызвали на Морской бой в Gram Play!</b>\nРасставь флот и потопи эскадру соперника 👇",
      parse_mode: "HTML",
      reply_markup: { inline_keyboard: [[{ text: "⚓ Войти в бой", url: "https://t.me/" + BOT_USERNAME + "?startapp=searoom_" + roomId + refSuffix }]] },
    };
  }
  const img = LW_API + "/card/room/minefield/" + roomId;
  return {
    type: "photo", id: "mine" + Date.now(),
    photo_url: img, thumb_url: img,
    caption: "💣 <b>Тебя вызвали на Минное поле в Gram Play!</b>\nЗаходи в комнату — заминируй соперника или разминируйся сам 👇",
    parse_mode: "HTML",
    reply_markup: { inline_keyboard: [[{ text: "💣 Войти в комнату", url: "https://t.me/" + BOT_USERNAME + "?startapp=mineroom_" + roomId + refSuffix }]] },
  };
}

async function pollShares() {
  try {
    const { jobs } = await api("/share/pending");
    for (const job of jobs || []) {
      try {
        const prepared = await telegram.tg("savePreparedInlineMessage", {
          user_id: Number(job.userId),
          result: shareResultFor(job),
          allow_user_chats: true,
          allow_group_chats: true,
          allow_channel_chats: false,
          allow_bot_chats: false,
        });
        await api("/share/result", { token: job.token, id: prepared.id });
      } catch (e) {
        console.warn("[share] prepare err:", e.message);
        try { await api("/share/result", { token: job.token, error: String(e.message || "failed") }); } catch (_) {}
      }
    }
  } catch (_) { /* backend недоступен — молча ждём */ }
}

// Пуш-уведомления покера: забираем из очереди и шлём DM игрокам.
async function pollNotify() {
  try {
    const { notifications } = await api("/notify/pending");
    for (const n of notifications || []) {
      try {
        await telegram.tg("sendMessage", {
          chat_id: Number(n.userId),
          text: String(n.text || ""),
          reply_markup: { inline_keyboard: [[{ text: "♠️ За стол", url: "https://t.me/" + BOT_USERNAME + "?startapp=" + (n.startapp || "play") }]] },
        });
      } catch (_) { /* юзер не открывал бота (403) и т.п. — пропускаем */ }
    }
  } catch (_) { /* backend недоступен */ }
}

function startShareRelay() {
  if (!SECRET) { console.warn("[share] GAME_API_SECRET не задан — релей карточек ВЫКЛЮЧЕН"); return; }
  console.log("[share] relay ON | LW_API =", LW_API, "| bot =", BOT_USERNAME);
  setInterval(pollShares, 1200);
  setInterval(pollNotify, 5000);
}

module.exports = { startShareRelay };
