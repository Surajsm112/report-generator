/* ===== Helpers for worksheets shown as laid out (forms, models, calculators) ===== */
const LAY = DATA.layout;
const GRID = LAY.cells;
const NROWS = GRID.length;
const NCOLS = LAY.columns.length;

function isNumLike(s) {
  if (s === null || s === undefined) return false;
  const t = String(s).trim();
  if (!t) return false;
  return /^[-+(]?[₹$€£¥]?\s*-?(\d[\d,]*\.?\d*|\.\d+)\s*%?\)?$/.test(t);
}
function toNum(s) {
  let t = String(s).trim();
  const neg = /^\(.*\)$/.test(t) || /^-/.test(t) || /^[₹$€£¥]-/.test(t);
  t = t.replace(/[^0-9.]/g, '');
  const n = parseFloat(t);
  return Number.isNaN(n) ? null : (neg ? -n : n);
}
function cellsOf(r) {
  const out = [];
  GRID[r].forEach((v, c) => { if (v !== '') out.push({ c, v }); });
  return out;
}
function isUpperHeading(v) {
  const letters = v.replace(/[^A-Za-z]/g, '');
  return letters.length >= 3 && letters === letters.toUpperCase() && !isNumLike(v);
}
function cleanLabel(v) { return v.replace(/[=:\s]+$/, '').replace(/\s+/g, ' ').trim(); }

// Longest run of number-like cells in neighbouring columns (3 or more) = a row of figures such as yearly values.
function numericRun(r) {
  let best = null, cur = null;
  for (let c = 0; c < NCOLS; c++) {
    if (isNumLike(GRID[r][c])) {
      if (!cur) cur = { start: c, end: c }; else cur.end = c;
    } else {
      if (cur && (!best || cur.end - cur.start > best.end - best.start)) best = cur;
      cur = null;
    }
  }
  if (cur && (!best || cur.end - cur.start > best.end - best.start)) best = cur;
  return best && best.end - best.start + 1 >= 3 ? best : null;
}

function setSheetHeader(tagText) {
  const heading = DATA.title;
  document.title = heading + ' – ' + tagText;
  const t = document.getElementById('title'); if (t) t.textContent = heading;
  const s = document.getElementById('sub');
  if (s) s.textContent = DATA.fileName + (DATA.sheetName ? ' · sheet "' + DATA.sheetName + '"' : '') + ' · ' +
    NROWS.toLocaleString() + ' rows with content · generated ' + DATA.generatedAt;
  const w = document.getElementById('truncWarn');
  if (w && LAY.truncated) { w.hidden = false; w.textContent = 'This sheet is long, so only the first ' + NROWS + ' rows with content are shown.'; }
}
