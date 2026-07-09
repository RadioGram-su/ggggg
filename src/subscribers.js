// Список подписчиков (chat_id тех, кто нажал /start) — кому слать уведомления о пуле.
const fs = require("fs");
const path = require("path");

const FILE = path.join(__dirname, "..", "data", "subscribers.json");

function load() {
  try { return JSON.parse(fs.readFileSync(FILE, "utf8")); } catch (_) { return []; }
}
function save(list) {
  try {
    fs.mkdirSync(path.dirname(FILE), { recursive: true });
    fs.writeFileSync(FILE, JSON.stringify(list));
  } catch (e) { console.warn("[subs] save:", e.message); }
}

function add(id) {
  const l = load();
  if (!l.includes(id)) { l.push(id); save(l); }
}
function remove(id) {
  save(load().filter((x) => x !== id));
}
function all() {
  return load();
}

module.exports = { add, remove, all };
