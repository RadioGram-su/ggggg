const api = require("./api");
const tg = require("./telegram");

const TICK_MS = Number(process.env.WATCH_MS || 45000);
const RENEW_CHECK_MS = Number(process.env.RENEW_CHECK_MS || 6 * 60 * 60 * 1000);
const SITE = (process.env.SITE_URL || "https://gramradar.org").replace(/\/+$/, "");

let timer = null;

function normAddr(a) {
  if (!a) return "";
  return String(a).toLowerCase().replace(/^-?\d:/, "");
}

function fmtLeft(sec) {
  if (sec <= 0) return "завершён";
  if (sec < 3600) return `${Math.floor(sec / 60)}м ${sec % 60}с`;
  const h = Math.floor(sec / 3600);
  const m = Math.floor((sec % 3600) / 60);
  return `${h}ч ${m}м`;
}

async function processDomain(wallet, slug, row, settings, chatId) {
  const info = await api.getDomain(slug);
  if (!info.success) return;

  const now = Math.floor(Date.now() / 1000);
  const left = info.auction?.auction_end_time ? info.auction.auction_end_time - now : 0;
  const bid = info.auction?.max_bid_amount || 0;
  const leader = info.auction?.max_bid_address || info.ownerAddress;
  const url = info.url;
  const name = info.domain || `${slug}.gram`;
  const notified = row.notified || {};
  const walletNorm = normAddr(wallet);
  const leaderNorm = normAddr(leader);

  if (leaderNorm === walletNorm && bid > 0) {
    if (bid > (row.myBidGrm || 0) + 0.0001) {
      row.myBidGrm = bid;
      notified.outbid = false;
    }
  }

  if (settings.notifyOutbid !== false && row.myBidGrm > 0) {
    if (leaderNorm && leaderNorm !== walletNorm && bid > row.myBidGrm + 0.0001 && !notified.outbid) {
      await tg.send(
        chatId,
        `⚠️ <b>Ставку перебили</b>\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Ваш кошелёк: <code>${tg.fmtWallet(wallet)}</code>\n` +
          `Ваша ставка: <b>${row.myBidGrm.toFixed(2)} GRM</b>\n` +
          `Новая ставка: <b>${bid.toFixed(2)} GRM</b>\n` +
          `Лидер: <code>${tg.fmtWallet(leader)}</code>\n` +
          `До конца: ${fmtLeft(left)}\n` +
          `<a href="${url}">Открыть аукцион</a>`
      );
      notified.outbid = true;
    }
  }

  if (info.state === "auction" || info.state === "waiting") {
    if (settings.notify10m !== false && left <= 600 && left > 300 && !notified.m10) {
      await tg.send(
        chatId,
        `⏰ <b>10 минут до конца</b>\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Ставка: ${bid.toFixed(2)} GRM\n` +
          `Лидер: <code>${tg.fmtWallet(leader)}</code>\n` +
          `Осталось: ${Math.floor(left / 60)}м ${left % 60}с\n` +
          `<a href="${url}">Открыть</a>`
      );
      notified.m10 = true;
    }
    if (settings.notify5m !== false && left <= 300 && left > 0 && !notified.m5) {
      await tg.send(
        chatId,
        `🔥 <b>5 минут до конца</b>\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Ставка: ${bid.toFixed(2)} GRM\n` +
          `Лидер: <code>${tg.fmtWallet(leader)}</code>\n` +
          `Осталось: ${Math.floor(left / 60)}м ${left % 60}с\n` +
          `<a href="${url}">Открыть</a>`
      );
      notified.m5 = true;
    }
  }

  const ended = info.state === "taken" || left <= 0;
  if (ended && !notified.ended && !notified.won) {
    const buyer = info.ownerAddress || leader;
    const buyerNorm = normAddr(buyer);
    const won = buyerNorm === walletNorm && row.myBidGrm > 0;

    if (won && !notified.won) {
      const finalBid = row.myBidGrm || bid;
      await tg.send(
        chatId,
        `🏆 <b>Вы выиграли аукцион!</b>\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Ваш кошелёк: <code>${tg.fmtWallet(wallet)}</code>\n` +
          `Ваша ставка: <b>${finalBid.toFixed(2)} GRM</b>\n` +
          `<a href="${url}">Открыть домен</a>`
      );
      notified.won = true;
      notified.ended = true;
    } else if (settings.notifyEnded !== false && (row.myBidGrm > 0 || bid > 0)) {
      const finalBid = row.myBidGrm && bid <= row.myBidGrm ? row.myBidGrm : bid;
      await tg.send(
        chatId,
        `✅ <b>Аукцион завершён</b>\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Цена: ${finalBid.toFixed(2)} GRM\n` +
          `Покупатель: <code>${tg.fmtWallet(buyer)}</code>\n` +
          `<a href="${url}">Подробнее</a>`
      );
      notified.ended = true;
    }
  }

  row.notified = notified;
  row.lastBid = bid;
  row.lastLeader = leader;
  row.end = info.auction?.auction_end_time || row.end;
}

async function processRenewals(wallet, settings, watch, chatId) {
  const nowMs = Date.now();
  if (watch.renewCheckedAt && nowMs - watch.renewCheckedAt < RENEW_CHECK_MS) return false;
  watch.renewCheckedAt = nowMs;
  watch.renew = watch.renew || {};

  const portfolio = await api.getPortfolio(wallet);
  const now = Math.floor(Date.now() / 1000);
  let dirty = true;

  for (const domain of portfolio.domains || []) {
    const name = domain.name;
    const slug = String(name || "").replace(/\.gram$/i, "").toLowerCase();
    const expiry = Number(domain.expiry || 0);
    if (!name || !slug || !expiry) continue;

    const secLeft = expiry - now;
    if (secLeft <= 0) continue;

    const daysLeft = Math.ceil(secLeft / 86400);
    const hoursLeft = secLeft / 3600;
    const notified = watch.renew[slug] || {};
    const url = domain.url || `${SITE}/#portfolio`;

    const checks = [
      [30, settings.notifyRenew30d !== false, "d30", false],
      [7, settings.notifyRenew7d !== false, "d7", false],
      [1, settings.notifyRenew1d !== false, "d1", false],
      [0, settings.notifyRenew1h !== false, "h1", true]
    ];

    for (const [days, enabled, key, urgent] of checks) {
      if (!enabled || notified[key]) continue;
      if (urgent) {
        if (hoursLeft > 1 || hoursLeft <= 0) continue;
      } else if (daysLeft > days) {
        continue;
      }

      const leftText = urgent
        ? `${Math.max(1, Math.ceil(hoursLeft * 60))} мин.`
        : `${daysLeft} дн.`;

      await tg.send(
        chatId,
        (urgent ? `🚨 <b>Срочно продлите!</b>` : `🔔 <b>Пора продлить домен</b>`) +
          `\n\n` +
          `<b>${tg.esc(name)}</b>\n` +
          `Кошелёк: <code>${tg.fmtWallet(wallet)}</code>\n` +
          `Осталось: ${leftText}\n` +
          `<a href="${url}">Открыть домен</a>`,
        {
          reply_markup: {
            inline_keyboard: [
              [{ text: "Продлить на сайте", url: `${SITE}/#portfolio` }],
              [{ text: "Открыть домен", url }]
            ]
          }
        }
      );
      notified[key] = true;
    }
    watch.renew[slug] = notified;
  }

  return dirty;
}

async function tick() {
  let data;
  try {
    data = await api.listJobs();
  } catch (e) {
    console.warn("[watcher] jobs:", e.message);
    return;
  }

  for (const job of data.jobs || []) {
    const { wallet, chatId, settings, watch } = job;

    const slugs = new Set(Object.keys(watch?.domains || {}));
    for (const s of settings?.watchlist || []) slugs.add(String(s).toLowerCase().replace(/\.gram$/, ""));

    let dirty = false;
    const state = watch || { domains: {}, renew: {} };
    for (const slug of slugs) {
      if (!slug) continue;
      if (!state.domains) state.domains = {};
      if (!state.domains[slug]) state.domains[slug] = { notified: {} };
      try {
        await processDomain(wallet, slug, state.domains[slug], settings || {}, chatId);
        dirty = true;
      } catch (e) {
        console.warn("[watcher]", slug, e.message);
      }
    }

    try {
      if (await processRenewals(wallet, settings || {}, state, chatId)) dirty = true;
    } catch (e) {
      console.warn("[watcher] renewals:", e.message);
    }

    if (dirty) {
      try {
        await api.saveWatch(wallet, state);
      } catch (e) {
        console.warn("[watcher] save:", e.message);
      }
    }
  }
}

function startWatcher() {
  if (timer) return;
  console.log("[watcher] auction alerts started");
  tick();
  timer = setInterval(tick, TICK_MS);
}

module.exports = { startWatcher, tick };
