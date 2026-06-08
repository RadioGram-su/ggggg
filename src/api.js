const SITE = (process.env.GRAMRADAR_API || "https://gramradar.org").replace(/\/$/, "");
const SECRET = process.env.BOT_API_SECRET || process.env.GRAMRADAR_BOT_SECRET || "";

function botHeaders(extra = {}) {
  return {
    accept: "application/json",
    "content-type": "application/json",
    "user-agent": "GramRadarBot/1.0",
    "x-bot-secret": SECRET,
    ...extra
  };
}

async function api(path, opts = {}) {
  const url = `${SITE}${path}`;
  const res = await fetch(url, {
    ...opts,
    headers: botHeaders(opts.headers)
  });
  const data = await res.json().catch(() => ({}));
  if (!res.ok) throw new Error(data.error || `${res.status} ${path}`);
  return data;
}

async function linkWithCode(code, chatId, username) {
  return api("/api/bot/link-with-code", {
    method: "POST",
    body: JSON.stringify({ code, chatId, username: username || null })
  });
}

async function statusByChat(chatId) {
  return api(`/api/bot/status-by-chat?chatId=${encodeURIComponent(chatId)}`);
}

async function listJobs() {
  return api("/api/bot/jobs");
}

async function saveWatch(wallet, watch) {
  return api("/api/bot/watch", {
    method: "POST",
    body: JSON.stringify({ wallet, watch })
  });
}

async function getDomain(name) {
  return api(`/api/domains/check?name=${encodeURIComponent(name)}`);
}

async function getPortfolio(wallet) {
  return api(`/api/portfolio?wallet=${encodeURIComponent(wallet)}`);
}

async function premiumStatus(wallet) {
  return api(`/api/premium/status?wallet=${encodeURIComponent(wallet)}`);
}

async function unlinkByChat(chatId) {
  return api("/api/bot/unlink", {
    method: "POST",
    body: JSON.stringify({ chatId })
  });
}

async function resyncBids(chatId) {
  return api("/api/bot/resync-bids", {
    method: "POST",
    body: JSON.stringify({ chatId })
  });
}

module.exports = {
  linkWithCode,
  statusByChat,
  unlinkByChat,
  resyncBids,
  listJobs,
  saveWatch,
  getDomain,
  getPortfolio,
  premiumStatus,
  SITE,
  SECRET
};
