try {
  require("dotenv").config();
} catch (_) {}

const poll = require("./src/poll");
const watcher = require("./src/watcher");
const api = require("./src/api");

function checkEnv() {
  if (!api.SECRET) {
    console.error("Set BOT_API_SECRET in env (same as gramradar.org .env)");
    process.exit(1);
  }
  if (!process.env.TELEGRAM_BOT_TOKEN && !process.env.GRAMRADAR_BOT_TOKEN) {
    console.error("Set TELEGRAM_BOT_TOKEN in env");
    process.exit(1);
  }
}

checkEnv();
console.log("Gram Radar bot (bothost) — commands + auction alerts");
console.log(`API: ${api.SITE}`);
poll.startPolling();
watcher.startWatcher();

process.on("SIGINT", () => {
  console.log("bye");
  process.exit(0);
});
