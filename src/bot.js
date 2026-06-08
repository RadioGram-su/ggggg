const api = require("./api");
const tg = require("./telegram");
const kb = require("./keyboard");

const CHANNEL = kb.CHANNEL;
const SITE = kb.SITE;

function welcomeText(linked = false) {
  if (linked) {
    return (
      `👋 <b>Gram Radar DNS</b>\n\n` +
      `Кошелёк привязан — алерты активны.\n\n` +
      `<b>Кнопки внизу экрана:</b>\n` +
      `📊 Статус · 🔄 Обновить ставки · 📋 Мои ставки\n\n` +
      `Уведомления: перебили ставку, выигрыш, 10/5 мин до конца, продление домена.`
    );
  }
  return (
    `👋 <b>Gram Radar DNS Bot</b>\n\n` +
    `<b>Бесплатные алерты для .gram</b>\n` +
    `• перебили вашу ставку\n` +
    `• вы выиграли аукцион\n` +
    `• 10 / 5 минут до конца\n` +
    `• напоминания о продлении\n\n` +
    `<b>Как начать:</b>\n` +
    `1. Нажмите «🌐 Открыть сайт» или кнопку ниже\n` +
    `2. Подключите TON-кошелёк\n` +
    `3. «Привязать @gramradardns_bot» → вставьте код сюда\n\n` +
    `Дальше — только <b>кнопки внизу</b>, команды не нужны.`
  );
}

function fmtTrackedList(items, limit = 8) {
  if (!items?.length) {
    return "Пока нет активных ставок.\nНажмите <b>🔄 Обновить ставки</b> после ставки на аукционе.";
  }
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

async function sendWithMenu(chatId, text, linked, extra = {}) {
  const { reply_markup: _drop, ...rest } = extra;
  return tg.send(chatId, text, {
    reply_markup: kb.mainReplyKeyboard(linked),
    ...rest
  });
}

async function getLinked(chatId) {
  try {
    const st = await api.statusByChat(chatId);
    return st.linked ? st : null;
  } catch {
    return null;
  }
}

async function linkByCode(chatId, code, username) {
  const out = await api.linkWithCode(code, chatId, username);
  const syncNote = out.syncPending
    ? "\n\nСтавки подтягиваются автоматически — через минуту нажмите <b>🔄 Обновить ставки</b>."
    : out.trackedAuctions
      ? `\nОтслеживается: <b>${out.trackedAuctions}</b> аукцион(ов)`
      : "\n\nПосле ставки нажмите <b>🔄 Обновить ставки</b>.";
  await sendWithMenu(
    chatId,
    `✅ <b>Кошелёк привязан</b>\n\n` +
      `<code>${tg.fmtWalletFull(out.wallet)}</code>\n\n` +
      `🔔 Алерты бесплатные и уже включены.${syncNote}`,
    true
  );
}

async function showStatus(chatId) {
  const st = await api.statusByChat(chatId);
  if (!st.linked) {
    await sendWithMenu(
      chatId,
      `⚠️ <b>Кошелёк не привязан</b>\n\n` +
        `1. Нажмите <b>🌐 Открыть сайт</b>\n` +
        `2. Подключите кошелёк и «Привязать бота»\n` +
        `3. Вставьте скопированную команду сюда\n\n` +
        `<a href="${SITE}/#sniper">Открыть gramradar.org →</a>`,
      false
    );
    return;
  }
  await sendWithMenu(
    chatId,
    `✅ <b>Статус</b>\n\n` +
      `Кошелёк:\n<code>${tg.fmtWalletFull(st.wallet)}</code>\n\n` +
      `Алерты: <b>бесплатно</b>, активны\n` +
      `Отслеживание: <b>авто</b> из транзакций\n\n` +
      `<b>Ваши ставки:</b>\n${fmtTrackedList(st.trackedDomains)}`,
    true
  );
}

async function showSync(chatId) {
  const st = await api.statusByChat(chatId);
  if (!st.linked) {
    await showLinkHelp(chatId);
    return;
  }
  await sendWithMenu(chatId, "⏳ Синхронизирую ставки с блокчейна…", true);
  const out = await api.resyncBids(chatId);
  const tail = out.trackedAuctions
    ? "Новые перебивки придут автоматически."
    : "Ставок на активных аукционах не найдено.";
  await sendWithMenu(
    chatId,
    `✅ <b>Ставки обновлены</b>\n\n` +
      `Кошелёк: <code>${tg.fmtWalletFull(out.wallet)}</code>\n` +
      `Найдено аукционов: ${out.bidsSynced || 0}\n` +
      `Отслеживается: <b>${out.trackedAuctions || 0}</b>\n\n` +
      `<b>Список:</b>\n${fmtTrackedList(out.trackedDomains)}\n\n` +
      tail,
    true
  );
}

async function showWatchlist(chatId) {
  const st = await api.statusByChat(chatId);
  if (!st.linked) {
    await showLinkHelp(chatId);
    return;
  }
  await sendWithMenu(
    chatId,
    `<b>📋 Ваши ставки</b> (авто из транзакций)\n\n${fmtTrackedList(st.trackedDomains, 15)}\n\n` +
      `Обновить список: <b>🔄 Обновить ставки</b>`,
    true
  );
}

async function showUnlink(chatId) {
  await tg.send(chatId, "⚠️ <b>Отвязать кошелёк?</b>\n\nАлерты перестанут приходить.", {
    reply_markup: {
      inline_keyboard: [
        [
          { text: "✅ Да, отвязать", callback_data: "act:unlink_confirm" },
          { text: "❌ Отмена", callback_data: "act:status" }
        ]
      ]
    }
  });
}

async function confirmUnlink(chatId) {
  await api.unlinkByChat(chatId);
  await sendWithMenu(
    chatId,
    `✅ <b>Привязка удалена</b>\n\n` +
      `Чтобы привязать снова:\n` +
      `1. <b>🌐 Открыть сайт</b>\n` +
      `2. Подключите нужный кошелёк\n` +
      `3. «Привязать @gramradardns_bot»\n\n` +
      `<a href="${SITE}/#sniper">Открыть gramradar.org →</a>`,
    false
  );
}

async function showLinkHelp(chatId) {
  await sendWithMenu(
    chatId,
    `<b>🔗 Привязка кошелька</b>\n\n` +
      `1. Откройте <a href="${SITE}/#sniper">gramradar.org</a>\n` +
      `2. Подключите TON-кошелёк\n` +
      `3. «Привязать @gramradardns_bot»\n` +
      `4. Вставьте скопированный код сюда\n\n` +
      `Ставки подтянутся автоматически — любая сумма GRM.`,
    false
  );
}

async function showSite(chatId, linked) {
  await sendWithMenu(
    chatId,
    linked
      ? `🌐 <a href="${SITE}/#sniper">gramradar.org</a> — ваши домены, алерты и аукционы.`
      : `🌐 Откройте сайт, подключите кошелёк и нажмите «Привязать @gramradardns_bot».\n\n<a href="${SITE}/#sniper">gramradar.org →</a>`,
    !!linked
  );
}

async function showChannel(chatId, linked) {
  await sendWithMenu(
    chatId,
    `📢 Канал с новостями и аукционами:\n<a href="${CHANNEL}">@gramradardns</a>`,
    !!linked
  );
}

async function showHelp(chatId, linked) {
  await sendWithMenu(chatId, welcomeText(!!linked), !!linked);
}

async function showStart(chatId) {
  const linked = await getLinked(chatId);
  await sendWithMenu(chatId, welcomeText(!!linked), !!linked);
  if (!linked) {
    await tg.send(chatId, "👇 Быстрые кнопки:", { reply_markup: kb.welcomeInlineKeyboard() });
  }
}

const ACTION_MAP = {
  [kb.BTN.status]: "status",
  [kb.BTN.sync]: "sync",
  [kb.BTN.bids]: "bids",
  [kb.BTN.site]: "site",
  [kb.BTN.link]: "link",
  [kb.BTN.unlink]: "unlink",
  [kb.BTN.channel]: "channel",
  [kb.BTN.help]: "help",
  "/status": "status",
  "/sync": "sync",
  "/watchlist": "bids",
  "/unlink": "unlink",
  "/channel": "channel",
  "/help": "help"
};

async function runAction(action, chatId, username) {
  const linked = await getLinked(chatId);
  switch (action) {
    case "status":
      return showStatus(chatId);
    case "sync":
      return showSync(chatId);
    case "bids":
      return showWatchlist(chatId);
    case "unlink":
      return showUnlink(chatId);
    case "link":
      return showLinkHelp(chatId);
    case "site":
      return showSite(chatId, !!linked);
    case "channel":
      return showChannel(chatId, !!linked);
    case "help":
      return showHelp(chatId, !!linked);
    default:
      return null;
  }
}

const CALLBACK_HINT = {
  status: "Статус…",
  sync: "Обновляю ставки…",
  bids: "Ваши ставки…",
  unlink: "Подтверждение…",
  unlink_confirm: "Отвязываю…",
  link: "Как привязать…",
  site: "Сайт…",
  channel: "Канал…",
  help: "Помощь…"
};

async function handleCallback(query) {
  const chatId = query.message?.chat?.id;
  const data = query.data || "";
  if (!chatId) return;
  const action = data.startsWith("act:") ? data.slice(4) : null;
  if (!action) {
    await tg.answerCallback(query.id);
    return;
  }
  await tg.answerCallback(query.id, { text: CALLBACK_HINT[action] || "…" }).catch(() => {});
  try {
    if (action === "unlink_confirm") {
      await confirmUnlink(chatId);
      return;
    }
    await runAction(action, chatId, query.from?.username);
  } catch (e) {
    await sendWithMenu(chatId, `❌ ${tg.esc(e.message)}`, !!(await getLinked(chatId)));
  }
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
        await sendWithMenu(chatId, `❌ ${tg.esc(e.message)}\n\nПолучите новый код на сайте.`, false);
      }
      return;
    }
    await showStart(chatId);
    return;
  }

  if (text.startsWith("/link")) {
    const code = (text.match(/^\/link\s*([A-Za-z0-9]+)$/i) || [])[1]?.toUpperCase();
    if (!code) {
      await showLinkHelp(chatId);
      return;
    }
    try {
      await linkByCode(chatId, code, username);
    } catch (e) {
      await sendWithMenu(chatId, `❌ ${tg.esc(e.message)}`, false);
    }
    return;
  }

  const action = ACTION_MAP[text] || ACTION_MAP[text.split(/\s+/)[0]?.toLowerCase()];
  if (action) {
    try {
      await runAction(action, chatId, username);
    } catch (e) {
      await sendWithMenu(chatId, `❌ ${tg.esc(e.message)}`, !!(await getLinked(chatId)));
    }
    return;
  }

  const linked = await getLinked(chatId);
  await sendWithMenu(
    chatId,
    `Не понял сообщение.\n\nИспользуйте <b>кнопки внизу</b> или нажмите <b>❓ Помощь</b>.`,
    !!linked
  );
}

module.exports = { handleMessage, handleCallback, welcomeText };
