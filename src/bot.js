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
    `Команды: /status /sync /unlink /watchlist /help /channel`
  );
}

function fmtTrackedList(items, limit = 8) {
  if (!items?.length) return "нет активных ставок — отправьте <code>/sync</code>";
  return items
    .slice(0, limit)
    .map((row) => {
      const name = tg.esc(String(row.name || `${row.slug}.gram`).replace(/\.gram$/i, "") + ".gram");
      if (row.outbid) {
        return `• <b>${name}</b> — ваша ${row.myBidGrm.toFixed(2)} GRM, сейчас ${row.currentBidGrm.toFixed(2)} GRM ⚠️`;
      }
      if (row.isLeader) {
        return `• <b>${name}</b> — лидер, ${row.myBidGrm.toFixed(2)} GRM ✅`;
      }
      return `• <b>${name}</b> — ваша ${row.myBidGrm.toFixed(2)} GRM`;
    })
    .join("\n");
}

async function sendOutbidAlerts(chatId, wallet, items) {
  for (const row of items || []) {
    await tg.send(
      chatId,
      `⚠️ <b>Ставку перебили</b>\n\n` +
        `<b>${tg.esc(row.name || `${row.slug}.gram`)}</b>\n` +
        `Ваш кошелёк: <code>${tg.fmtWalletFull(wallet)}</code>\n` +
        `Ваша ставка: <b>${row.myBidGrm.toFixed(2)} GRM</b>\n` +
        `Новая ставка: <b>${row.currentBidGrm.toFixed(2)} GRM</b>\n` +
        `Лидер: <code>${tg.fmtWallet(row.leader)}</code>\n` +
        `<a href="${row.url}">Открыть аукцион</a>`
    );
  }
}

async function linkByCode(chatId, code, username) {
  const out = await api.linkWithCode(code, chatId, username);
  const syncNote = out.syncPending
    ? "\n\nСтавки подтягиваются в фоне — через минуту отправьте <code>/sync</code>."
    : out.trackedAuctions
      ? `\nОтслеживается: <b>${out.trackedAuctions}</b> аукцион(ов)`
      : "\n\nСтавки не найдены — отправьте <code>/sync</code> после ставки.";
  await tg.send(
    chatId,
    `✅ Telegram привязан к кошельку\n\n` +
      `<code>${tg.fmtWalletFull(out.wallet)}</code>\n\n` +
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
          `Кошелёк:\n<code>${tg.fmtWalletFull(st.wallet)}</code>\n\n` +
          `Алерты: <b>бесплатно</b>, активны\n` +
          `Watchlist (вручную): ${st.settings?.watchlist?.length || 0} домен(ов)\n\n` +
          `<b>Ваши ставки на аукционах:</b>\n${fmtTrackedList(st.trackedDomains)}\n\n` +
          `Обновить: <code>/sync</code>\n` +
          `Не тот кошелёк? <code>/unlink</code>`
      );
    } catch (e) {
      await tg.send(chatId, `❌ ${tg.esc(e.message)}`);
    }
    return;
  }

  if (text === "/unlink") {
    try {
      await api.unlinkByChat(chatId);
      await tg.send(
        chatId,
        `✅ Привязка удалена.\n\n` +
          `1. Откройте <a href="${SITE}/#sniper">gramradar.org</a>\n` +
          `2. Подключите <b>нужный</b> TON-кошелёк\n` +
          `3. Нажмите «Привязать @gramradardns_bot»\n\n` +
          `Или: <code>/link КОД</code> с сайта`
      );
    } catch (e) {
      await tg.send(chatId, `❌ ${tg.esc(e.message)}`);
    }
    return;
  }

  if (text === "/sync") {
    try {
      const st = await api.statusByChat(chatId);
      if (!st.linked) {
        await tg.send(chatId, "Кошелёк не привязан. Сначала привяжите кошелёк на сайте.");
        return;
      }
      await tg.send(chatId, "⏳ Синхронизирую ваши ставки с блокчейна…");
      const out = await api.resyncBids(chatId);
      await tg.send(
        chatId,
        `✅ Синхронизация завершена\n\n` +
          `Кошелёк: <code>${tg.fmtWalletFull(out.wallet)}</code>\n` +
          `Обновлено аукционов: ${out.bidsSynced || 0}\n` +
          `Отслеживается: <b>${out.trackedAuctions || 0}</b>\n\n` +
          `<b>Ставки:</b>\n${fmtTrackedList(out.trackedDomains)}`
      );
      if (out.trackedAuctions) {
        await tg.send(chatId, "Уведомления о перебивке активны — новые перебивки придут автоматически.");
      } else {
        await tg.send(chatId, "На активных аукционах ваших ставок не найдено.");
      }
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
      const manual = st.settings?.watchlist?.length
        ? st.settings.watchlist
            .slice(0, 30)
            .map((s) => `• ${tg.esc(String(s).replace(/\.gram$/, ""))}.gram`)
            .join("\n")
        : "— пуст";
      await tg.send(
        chatId,
        `<b>Watchlist</b> (добавляется на сайте вручную)\n\n${manual}\n\n` +
          `<b>Авто-отслеживание ваших ставок:</b>\n${fmtTrackedList(st.trackedDomains, 15)}`
      );
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
