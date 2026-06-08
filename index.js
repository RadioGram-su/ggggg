try {
  require("dotenv").config();
} catch (_) {
  // env from host panel (bothost) — dotenv optional
}

const fs = require("fs");
const path = require("path");
const bot = require("./src/bot");
const tg = require("./src/telegram");
const watcher = require("./src/watcher");
const api = require("./src/api");

const POLL_MS = Number(process.env.POLL_MS || 1500);
const LOCK_FILE = process.env.BOT_LOCK_FILE || path.join(__dirname, "data", "bot.lock");
let offset = 0;
let pollTimer = null;

function acquireSingleInstanceLock() {
  try {
    fs.mkdirSync(path.dirname(LOCK_FILE), { recursive: true });
    if (fs.existsSync(LOCK_FILE)) {
      const oldPid = Number(fs.readFileSync(LOCK_FILE, "utf8").trim());
      if (oldPid > 0 && oldPid !== process.pid) {
        try {
          process.kill(oldPid, 0);
          console.error(`[boot] another bot instance running (pid ${oldPid}). Exit.`);
          process.exit(0);
        } catch (_) {
          /* stale lock */
        }
      }
    }
    fs.writeFileSync(LOCK_FILE, String(process.pid));
  } catch (e) {
    console.warn("[boot] lock file:", e.message);
  }
}

function releaseLock() {
  try {
    if (fs.existsSync(LOCK_FILE) && fs.readFileSync(LOCK_FILE, "utf8").trim() === String(process.pid)) {
      fs.unlinkSync(LOCK_FILE);
    }
  } catch (_) {}
}

acquireSingleInstanceLock();
process.on("exit", releaseLock);
process.on("SIGINT", () => {
  releaseLock();
  console.log("bye");
  process.exit(0);
});
process.on("SIGTERM", () => {
  releaseLock();
  process.exit(0);
});

function checkEnv() {
  if (!tg.isEnabled()) {
    console.error("Set TELEGRAM_BOT_TOKEN in env");
    process.exit(1);
  }
  if (!api.SECRET) {
    console.error("Set BOT_API_SECRET in env (same as on gramradar.org server)");
    process.exit(1);
  }
}

async function pollOnce() {
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
    if (/conflict/i.test(msg)) {
      console.error("[poll] Conflict — two programs use this bot token. See fix steps in DEPLOY.txt");
      return;
    }
    console.warn("[poll]", msg);
  }
}

function startPolling() {
  if (pollTimer) return;
  console.log("Gram Radar Bot @gramradardns_bot");
  console.log(`API: ${api.SITE}`);
  pollOnce();
  pollTimer = setInterval(pollOnce, POLL_MS);
}

async function bootstrap() {
  checkEnv();
  try {
    await tg.tg("deleteWebhook", { drop_pending_updates: true });
    console.log("[telegram] webhook cleared — polling mode");
  } catch (e) {
    console.warn("[telegram] deleteWebhook:", e.message);
  }
  try {
    const me = await tg.tg("getMe");
    console.log(`[telegram] bot @${me.username} (${me.first_name})`);
  } catch (e) {
    console.error("[telegram] invalid TELEGRAM_BOT_TOKEN:", e.message);
    process.exit(1);
  }
  await tg.setCommands();
  startPolling();
  watcher.startWatcher();
}

bootstrap().catch((e) => {
  console.error("[boot]", e.message || e);
  releaseLock();
  process.exit(1);
});
