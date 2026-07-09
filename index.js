// Gram Radar — бот «Призовой пул»: уведомления о розыгрышах в канал + запуск мини-аппа.
// Старый функционал (алерты аукционов, привязка кошелька) отключён — только лотерея.

try {
  require("dotenv").config();
} catch (_) {}

const lottery = require("./src/lottery");
const lockWatch = require("./src/lock");
const dnsEvents = require("./src/dns-events");
const start = require("./src/start");
const shareRelay = require("./src/share-relay");
const tg = require("./src/telegram");

if (!tg.isEnabled()) {
  console.error("Set TELEGRAM_BOT_TOKEN in env");
  process.exit(1);
}

console.log("Gram Radar — Призовой пул (lottery notifications + miniapp launch)");
start.startCommands();   // /start → кнопка запуска пула
lottery.startLottery();  // уведомления о розыгрышах в канал
lockWatch.startLockWatch();  // уведомления о новых тайм-локах в @grmlocked
dnsEvents.startDnsEvents();  // лента всех DNS-операций в канал (минт/ставки/листинги/продажи + локи)
shareRelay.startShareRelay();  // Gram Play: готовит фото-карточки приглашений (prepared inline message)

process.on("SIGINT", () => {
  console.log("bye");
  process.exit(0);
});
