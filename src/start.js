// Минимальный обработчик команд: на /start (и любое сообщение в личке) — кнопка
// запуска мини-аппа «Призовой пул». Больше бот в личке ничего не делает.

const tg = require("./telegram");
const subs = require("./subscribers");

const MINIAPP_URL = process.env.LOTTERY_MINIAPP_URL || "https://t.me/gramradardns_bot?startapp=lottery";
const openBtn = { inline_keyboard: [[{ text: "🎟 Открыть Призовой пул", url: MINIAPP_URL }]] };

let offset = 0;
let running = false;

async function handle(msg) {
  const chatId = msg.chat && msg.chat.id;
  if (!chatId) return;
  subs.add(chatId); // подписываем на уведомления о розыгрышах
  await tg.send(
    chatId,
    `🎲 <b>Призовой пул GRM</b>\n\n` +
      `Почасовые розыгрыши on-chain.\n` +
      `1 билет = 100 GRM · 90% победителю · 10% сжигается 🔥\n` +
      `Каждый билет = +1 шанс, победитель случаен on-chain.\n\n` +
      `Нажми кнопку ниже, чтобы участвовать 👇`,
    { reply_markup: openBtn }
  );
}

async function loop() {
  if (!running || !tg.isEnabled()) return;
  try {
    const updates = await tg.tg("getUpdates", { offset, timeout: 25, allowed_updates: ["message"] });
    for (const u of updates || []) {
      offset = u.update_id + 1;
      if (u.message) await handle(u.message);
    }
  } catch (e) {
    const msg = e.message || String(e);
    console.warn("[start]", msg);
    await new Promise((r) => setTimeout(r, /conflict/i.test(msg) ? 5000 : 2000));
  }
  setImmediate(loop);
}

async function startCommands() {
  if (!tg.isEnabled()) { console.error("[start] TELEGRAM_BOT_TOKEN not set"); return; }
  if (running) return;
  running = true;
  try { await tg.tg("deleteWebhook", { drop_pending_updates: true }); } catch (_) {}
  try {
    await tg.tg("setMyCommands", { commands: [{ command: "start", description: "Открыть Призовой пул" }] });
  } catch (_) {}
  console.log("[start] commands polling (lottery only)");
  loop();
}

module.exports = { startCommands };
