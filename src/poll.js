const bot = require("./bot");
const tg = require("./telegram");

let offset = 0;
let running = false;

async function pollOnce() {
  if (!tg.isEnabled()) return;
  try {
    const updates = await tg.tg("getUpdates", {
      offset,
      timeout: 25,
      allowed_updates: ["message"]
    });
    for (const u of updates || []) {
      offset = u.update_id + 1;
      if (u.message) await bot.handleMessage(u.message);
    }
  } catch (e) {
    console.warn("[poll]", e.message);
  }
}

async function startPolling() {
  if (!tg.isEnabled()) {
    console.error("[poll] TELEGRAM_BOT_TOKEN not set");
    return;
  }
  if (running) return;
  running = true;

  try {
    await tg.tg("deleteWebhook", { drop_pending_updates: true });
    console.log("[poll] webhook cleared");
  } catch (e) {
    console.warn("[poll] deleteWebhook:", e.message);
  }

  await tg.setCommands();
  console.log("[poll] commands via getUpdates (bothost)");
  pollOnce();
  setInterval(pollOnce, 500);
}

module.exports = { startPolling };
