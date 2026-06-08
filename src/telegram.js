const TOKEN = process.env.TELEGRAM_BOT_TOKEN || process.env.GRAMRADAR_BOT_TOKEN || "";
const API = TOKEN ? `https://api.telegram.org/bot${TOKEN}` : "";

function esc(s) {
  return String(s || "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
}

function fmtWallet(addr) {
  if (!addr) return "—";
  const a = String(addr);
  if (a.length <= 16) return esc(a);
  return esc(`${a.slice(0, 6)}…${a.slice(-6)}`);
}

async function tg(method, body = {}) {
  if (!API) throw new Error("TELEGRAM_BOT_TOKEN not set");
  const res = await fetch(`${API}/${method}`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body)
  });
  const data = await res.json();
  if (!data.ok) throw new Error(data.description || `Telegram ${method} failed`);
  return data.result;
}

async function send(chatId, text, extra = {}) {
  if (!chatId) return null;
  try {
    return await tg("sendMessage", {
      chat_id: chatId,
      text,
      parse_mode: "HTML",
      disable_web_page_preview: true,
      ...extra
    });
  } catch (e) {
    console.warn("[tg] send failed:", e.message);
    return null;
  }
}

async function setCommands() {
  if (!API) return null;
  try {
    return await tg("setMyCommands", {
      commands: [
        { command: "status", description: "Premium, пробный период, кошелёк" },
        { command: "watchlist", description: "Домены в мониторинге" },
        { command: "channel", description: "Канал Gram Radar DNS" },
        { command: "help", description: "Как подключить алерты" }
      ]
    });
  } catch (e) {
    console.warn("[tg] setMyCommands failed:", e.message);
    return null;
  }
}

module.exports = { send, esc, fmtWallet, tg, setCommands, isEnabled: () => !!API };
