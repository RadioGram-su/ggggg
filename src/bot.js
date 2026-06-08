const api = require("./api");
const tg = require("./telegram");

const CHANNEL = process.env.CHANNEL_URL || "https://t.me/gramradardns";
const SITE = process.env.SITE_URL || "https://gramradar.org";

function welcomeText() {
  return (
    `👋 <b>Gram Radar DNS Bot</b>\n\n` +
    `<b>Бесплатные алерты для .gram</b>\n` +
    `• перебили вашу ставку (кошелёк, суммы, лидер)\n` +
    `• вы выиграли аукцион\n` +
    `• 10 / 5 минут до конца аукциона\n` +
    `• продление доменов за 30 / 7 / 1 день и за 1 час\n\n` +
    `<b>Привязка кошелька:</b>\n` +
    `1. Откройте <a href="${SITE}/#sniper">gramradar.org</a>\n` +
    `2. Подключите TON-кошелёк\n` +
    `3. Нажмите «Привязать @gramradardns_bot»\n\n` +
    `Или: <code>/link КОД</code>\n\n` +
    `📢 Канал: <a href="${CHANNEL}">@gramradardns</a>\n` +
    `Команды: /status /watchlist /help /channel`
  );
}

async function linkByCode(chatId, code, username) {
  const out = await api.linkWithCode(code, chatId, username);
  const syncNote = out.bidsSynced ? `\nСинхронизировано активных ставок: ${out.bidsSynced}` : "";
  await tg.send(
    chatId,
    `✅ Telegram привязан к кошельку\n\n` +
      `<code>${tg.esc(out.wallet)}</code>\n\n` +
      `🔔 <b>Алерты бесплатные</b> — уже включены.${syncNote}\n\n` +
      `Уведомления:\n` +
      `• перебили ставку\n` +
      `• вы выиграли аукцион\n` +
      `• 10 / 5 мин до конца\n` +
      `• продление 30 / 7 / 1 день и за 1 час\n\n` +
      `📢 <a href="${CHANNEL}">Подписаться на канал</a>`
  );
}

async function handleMessage(msg) {
  const chatId = msg.chat?.id;
  const text = (msg.text || "").trim();
  const username = msg.from?.username;
  if (!chatId || !text) return;

  if (text === "/start" || text.startsWith("/start ")) {
    const payload = text.split(/\s+/)[1] || "";
    if (payload.startsWith("link_")) {
      try {
        await linkByCode(chatId, payload.slice(5).toUpperCase(), username);
      } catch (e) {
        await tg.send(chatId, `❌ ${tg.esc(e.message)}\n\nПолучите новый код на сайте.`);
      }
      return;
    }
    await tg.send(chatId, welcomeText());
    return;
  }

  if (text.startsWith("/link")) {
    const code = text.split(/\s+/)[1]?.toUpperCase();
    if (!code) {
      await tg.send(chatId, "Использование: <code>/link AB12CD</code>");
      return;
    }
    try {
      await linkByCode(chatId, code, username);
    } catch (e) {
      await tg.send(chatId, `❌ ${tg.esc(e.message)}`);
    }
    return;
  }

  if (text === "/status") {
    try {
      const st = await api.statusByChat(chatId);
      if (!st.linked) {
        await tg.send(chatId, "Кошелёк не привязан. Откройте сайт и нажмите «Привязать Telegram».");
        return;
      }
      await tg.send(
        chatId,
        `✅ <b>Статус</b>\n\n` +
          `Кошелёк:\n<code>${tg.esc(st.wallet)}</code>\n\n` +
          `Алерты: <b>бесплатно</b>, активны\n` +
          `Watchlist: ${st.settings?.watchlist?.length || 0} домен(ов)`
      );
    } catch (e) {
      await tg.send(chatId, `❌ ${tg.esc(e.message)}`);
    }
    return;
  }

  if (text === "/watchlist") {
    try {
      const st = await api.statusByChat(chatId);
      if (!st.linked) {
        await tg.send(chatId, "Кошелёк не привязан. Откройте сайт и нажмите «Привязать Telegram».");
        return;
      }
      const list = st.settings?.watchlist?.length
        ? st.settings.watchlist
            .slice(0, 30)
            .map((s) => `• ${tg.esc(String(s).replace(/\.gram$/, ""))}.gram`)
            .join("\n")
        : "Watchlist пуст — отслеживаются ваши ставки на аукционах автоматически.";
      await tg.send(chatId, `<b>Watchlist</b>\n\n${list}`);
    } catch (e) {
      await tg.send(chatId, `❌ ${tg.esc(e.message)}`);
    }
    return;
  }

  if (text === "/channel") {
    await tg.send(chatId, `📢 Канал Gram Radar DNS:\n<a href="${CHANNEL}">@gramradardns</a>`);
    return;
  }

  if (text === "/help") {
    await tg.send(chatId, welcomeText());
  }
}

module.exports = { handleMessage, welcomeText };
