const SITE = (process.env.SITE_URL || "https://gramradar.org").replace(/\/+$/, "");
const CHANNEL = process.env.CHANNEL_URL || "https://t.me/gramradardns";

const BTN = {
  status: "📊 Статус",
  sync: "🔄 Обновить ставки",
  bids: "📋 Мои ставки",
  site: "🌐 Открыть сайт",
  link: "🔗 Как привязать",
  unlink: "🔓 Отвязать кошелёк",
  channel: "📢 Канал",
  help: "❓ Помощь"
};

function mainReplyKeyboard(linked = false) {
  const rows = [
    [{ text: BTN.status }, { text: BTN.sync }],
    [{ text: BTN.bids }, { text: BTN.site }]
  ];
  rows.push([{ text: linked ? BTN.unlink : BTN.link }]);
  rows.push([{ text: BTN.channel }, { text: BTN.help }]);
  return {
    keyboard: rows,
    resize_keyboard: true,
    is_persistent: true,
    input_field_placeholder: linked ? "Выберите действие в меню" : "Сначала привязите кошелёк на сайте"
  };
}

function siteInlineKeyboard() {
  return {
    inline_keyboard: [
      [{ text: "🔗 Привязать кошелёк на сайте", url: `${SITE}/#sniper` }],
      [{ text: "📖 Gram Radar DNS", url: SITE }]
    ]
  };
}

function channelInlineKeyboard() {
  return {
    inline_keyboard: [[{ text: "📢 Подписаться @gramradardns", url: CHANNEL }]]
  };
}

function welcomeInlineKeyboard() {
  return {
    inline_keyboard: [
      [{ text: "🔗 Привязать на gramradar.org", url: `${SITE}/#sniper` }],
      [
        { text: "📊 Статус", callback_data: "act:status" },
        { text: "🔄 Обновить", callback_data: "act:sync" }
      ],
      [{ text: "📢 Канал", url: CHANNEL }]
    ]
  };
}

function actionInlineKeyboard(linked = false) {
  const row = [
    { text: "📊 Статус", callback_data: "act:status" },
    { text: "🔄 Обновить", callback_data: "act:sync" }
  ];
  const rows = [row, [{ text: "📋 Мои ставки", callback_data: "act:bids" }]];
  if (linked) {
    rows.push([{ text: "🔓 Отвязать", callback_data: "act:unlink" }]);
  } else {
    rows.push([{ text: "🔗 Привязать", url: `${SITE}/#sniper` }]);
  }
  return { inline_keyboard: rows };
}

module.exports = {
  BTN,
  mainReplyKeyboard,
  siteInlineKeyboard,
  channelInlineKeyboard,
  welcomeInlineKeyboard,
  actionInlineKeyboard,
  SITE,
  CHANNEL
};
