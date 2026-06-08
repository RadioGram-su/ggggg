try {
  require("dotenv").config();
} catch (_) {}

const watcher = require("./src/watcher");
const api = require("./src/api");

function checkEnv() {
  if (!api.SECRET) {
    console.error("Set BOT_API_SECRET in env (same as gramradar.org .env)");
    process.exit(1);
  }
}

checkEnv();
console.log("Gram Radar watcher (bothost) — alerts only, no Telegram polling");
console.log(`API: ${api.SITE}`);
console.log("Commands (/start, /link) handled by gramradar.org webhook");
watcher.startWatcher();

process.on("SIGINT", () => {
  console.log("bye");
  process.exit(0);
});
