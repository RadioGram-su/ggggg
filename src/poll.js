const bot = require("./bot");
const tg = require("./telegram");

let offset = 0;
let running = false;

async function pollLoop() {
  if (!running || !tg.isEnabled()) return;
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
    const msg = e.message || String(e);
    console.warn("[poll]", msg);
    if (/conflict/i.test(msg)) {
      await new Promise((r) => setTimeout(r, 5000));
    } else {
      await new Promise((r) => setTimeout(r, 2000));
    }
  }
  setImmediate(pollLoop);
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
  pollLoop();
}

module.exports = { startPolling };
