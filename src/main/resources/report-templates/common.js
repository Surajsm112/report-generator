/* ===== Shared helpers used by all three reports ===== */
const $ = (s, el = document) => el.querySelector(s);
const COLS = DATA.columns;
const ROWS = DATA.rows;
COLS.forEach((c, i) => { c.i = i; });
// Main measures first: money-like columns (revenue, sales, amount...) before quantities, ratings and ids.
function measureScore(name) {
  if (/revenue|sales|amount|income|profit|turnover|total/i.test(name)) return 3;
  if (/cost|spend|value|salary|fee|budget/i.test(name)) return 2;
  if (/price|rate|rating|score|age|percent|%/i.test(name)) return 0;
  return 1;
}
// Averages make sense for these, sums do not.
function isSummable(name) { return !/price|rate|rating|score|age|percent|%/i.test(name); }
const numCols = COLS.filter(c => c.type === 'NUMBER')
  .map((c, k) => ({ c, k }))
  .sort((a, b) => measureScore(b.c.name) - measureScore(a.c.name) || a.k - b.k)
  .map(x => x.c);
const dateCols = COLS.filter(c => c.type === 'DATE');
const catCols = COLS.filter(c => c.type === 'CATEGORY');
const textCols = COLS.filter(c => c.type === 'TEXT');
const PALETTE = ['#4e79a7', '#f28e2b', '#e15759', '#76b7b2', '#59a14f', '#edc948', '#b07aa1', '#ff9da7', '#9c755f', '#bab0ac'];

function esc(s) {
  return String(s).replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function fmt(n, d = 2) {
  if (n === null || n === undefined || Number.isNaN(n)) return '–';
  const a = Math.abs(n);
  if (a >= 1e9) return (n / 1e9).toFixed(d) + 'B';
  if (a >= 1e6) return (n / 1e6).toFixed(d) + 'M';
  if (a >= 1e4) return (n / 1e3).toFixed(1) + 'K';
  return n.toLocaleString(undefined, { maximumFractionDigits: d });
}

function cellText(v) {
  if (v === null || v === undefined || v === '') return '–';
  if (typeof v === 'number') return v.toLocaleString(undefined, { maximumFractionDigits: 4 });
  return String(v);
}

function setHeader() {
  document.title = DATA.title + ' – report';
  const t = $('#title'); if (t) t.textContent = DATA.title;
  const s = $('#sub');
  if (s) {
    s.textContent = DATA.fileName + ' · ' + DATA.rowCount.toLocaleString() + ' rows · ' +
      COLS.length + ' columns · generated ' + DATA.generatedAt;
  }
  const w = $('#truncWarn');
  if (w && DATA.truncated) {
    w.hidden = false;
    w.textContent = 'Only the first ' + DATA.rowCount.toLocaleString() + ' of ' +
      DATA.totalRows.toLocaleString() + ' rows are included in this report.';
  }
}

/* ----- aggregation ----- */
// agg: 'sum' | 'avg' | 'count'. valIdx may be null (then count rows).
function aggregate(rows, keyIdx, valIdx, agg) {
  const m = new Map();
  for (const r of rows) {
    const k = r[keyIdx];
    if (k === null || k === undefined || k === '') continue;
    let e = m.get(k);
    if (!e) { e = { s: 0, n: 0, c: 0 }; m.set(k, e); }
    e.c++;
    if (valIdx !== null && typeof r[valIdx] === 'number') { e.s += r[valIdx]; e.n++; }
  }
  const out = [];
  for (const [k, e] of m) {
    out.push([k, agg === 'count' || valIdx === null ? e.c : agg === 'avg' ? (e.n ? e.s / e.n : 0) : e.s]);
  }
  return out;
}

function topN(pairs, n, other = 'Other') {
  const sorted = [...pairs].sort((a, b) => b[1] - a[1]);
  if (sorted.length <= n) return sorted;
  const head = sorted.slice(0, n);
  const rest = sorted.slice(n).reduce((s, p) => s + p[1], 0);
  head.push([other, rest]);
  return head;
}

// Group a date column into days (short ranges) or months (long ranges).
function dateBucketLen(col) {
  if (!col.minDate || !col.maxDate) return 7;
  const days = (new Date(col.maxDate) - new Date(col.minDate)) / 86400000;
  return days > 62 ? 7 : 10; // 'YYYY-MM' or 'YYYY-MM-DD'
}

function trendSeries(rows, dateCol, valIdx, agg) {
  const len = dateBucketLen(dateCol);
  const m = new Map();
  for (const r of rows) {
    const d = r[dateCol.i];
    if (!d) continue;
    const k = d.slice(0, len);
    let e = m.get(k);
    if (!e) { e = { s: 0, n: 0, c: 0 }; m.set(k, e); }
    e.c++;
    if (valIdx !== null && typeof r[valIdx] === 'number') { e.s += r[valIdx]; e.n++; }
  }
  return [...m.entries()].sort((a, b) => a[0] < b[0] ? -1 : 1).map(([k, e]) =>
    [k, agg === 'count' || valIdx === null ? e.c : agg === 'avg' ? (e.n ? e.s / e.n : 0) : e.s]);
}

function numbersOf(rows, idx) {
  const out = [];
  for (const r of rows) if (typeof r[idx] === 'number') out.push(r[idx]);
  return out;
}

function histogram(values, bins = 10) {
  if (!values.length) return { labels: [], counts: [] };
  const min = Math.min(...values), max = Math.max(...values);
  if (min === max) return { labels: [fmt(min)], counts: [values.length] };
  const w = (max - min) / bins;
  const counts = new Array(bins).fill(0);
  for (const v of values) counts[Math.min(bins - 1, Math.floor((v - min) / w))]++;
  const labels = counts.map((_, i) => fmt(min + i * w, 1) + '–' + fmt(min + (i + 1) * w, 1));
  return { labels, counts };
}

function quantile(sorted, q) {
  if (!sorted.length) return null;
  const pos = (sorted.length - 1) * q, lo = Math.floor(pos), hi = Math.ceil(pos);
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo);
}

/* ----- charts (Chart.js) ----- */
const charts = {};
function draw(id, config) {
  if (charts[id]) charts[id].destroy();
  const canvas = document.getElementById(id);
  if (!canvas) return;
  config.options = Object.assign({ responsive: true, maintainAspectRatio: false }, config.options || {});
  charts[id] = new Chart(canvas, config);
}

function colors(n) { return Array.from({ length: n }, (_, i) => PALETTE[i % PALETTE.length]); }

/* ----- filters: one control per column, chosen by column type ----- */
function makeFilters(container, cols, onChange) {
  const state = {};
  cols.forEach(c => {
    const wrap = document.createElement('div');
    wrap.className = 'f';
    let html = '<label>' + esc(c.name) + '</label>';
    if (c.type === 'DATE') {
      html += '<div class="pair"><input type="date" data-k="from" min="' + c.minDate + '" max="' + c.maxDate +
        '" title="From"><input type="date" data-k="to" min="' + c.minDate + '" max="' + c.maxDate + '" title="To"></div>';
    } else if (c.type === 'NUMBER') {
      html += '<div class="pair"><input type="number" step="any" data-k="min" placeholder="min ' + fmt(c.min) +
        '"><input type="number" step="any" data-k="max" placeholder="max ' + fmt(c.max) + '"></div>';
    } else if (c.type === 'CATEGORY') {
      const vals = [...new Set(ROWS.map(r => r[c.i]).filter(v => v !== null && v !== ''))].sort();
      html += '<select data-k="eq"><option value="">All</option>' +
        vals.map(v => '<option value="' + esc(v) + '">' + esc(v) + '</option>').join('') + '</select>';
    } else {
      html += '<input type="text" data-k="has" placeholder="contains…">';
    }
    wrap.innerHTML = html;
    container.appendChild(wrap);
    wrap.querySelectorAll('input,select').forEach(el => {
      el.addEventListener('input', () => {
        (state[c.i] = state[c.i] || {})[el.dataset.k] = el.value;
        onChange();
      });
    });
  });

  function apply() {
    return ROWS.filter(r => {
      for (const k in state) {
        const s = state[k], v = r[k], c = COLS[k];
        if (c.type === 'DATE') {
          if (s.from && (v === null || v < s.from)) return false;
          if (s.to && (v === null || v > s.to)) return false;
        } else if (c.type === 'NUMBER') {
          if (s.min !== undefined && s.min !== '' && (typeof v !== 'number' || v < +s.min)) return false;
          if (s.max !== undefined && s.max !== '' && (typeof v !== 'number' || v > +s.max)) return false;
        } else if (c.type === 'CATEGORY') {
          if (s.eq && String(v) !== s.eq) return false;
        } else if (s.has && !String(v === null ? '' : v).toLowerCase().includes(s.has.toLowerCase())) {
          return false;
        }
      }
      return true;
    });
  }

  function reset() {
    container.querySelectorAll('input').forEach(i => { i.value = ''; });
    container.querySelectorAll('select').forEach(s => { s.selectedIndex = 0; });
    for (const k in state) delete state[k];
    onChange();
  }
  return { apply, reset };
}

/* ----- table with sorting, search and paging ----- */
function makeTable(container, getRows, pageSize = 25) {
  let sortIdx = null, sortDir = 1, page = 0, search = '';
  container.innerHTML =
    '<div class="tbar"><input type="search" class="tsearch" placeholder="Search in table…">' +
    '<span class="tinfo"></span></div><div class="twrap"><table><thead></thead><tbody></tbody></table></div>' +
    '<div class="pager"><button class="prev">‹ Prev</button><span class="pg"></span><button class="next">Next ›</button></div>';
  const thead = $('thead', container), tbody = $('tbody', container);
  thead.innerHTML = '<tr>' + COLS.map(c => '<th data-i="' + c.i + '" class="' + (c.type === 'NUMBER' ? 'num' : '') + '">' +
    esc(c.name) + '<span class="arr"></span></th>').join('') + '</tr>';
  thead.addEventListener('click', e => {
    const th = e.target.closest('th'); if (!th) return;
    const i = +th.dataset.i;
    if (sortIdx === i) sortDir = -sortDir; else { sortIdx = i; sortDir = 1; }
    page = 0; render();
  });
  $('.tsearch', container).addEventListener('input', e => { search = e.target.value.toLowerCase(); page = 0; render(); });
  $('.prev', container).onclick = () => { if (page > 0) { page--; render(); } };
  $('.next', container).onclick = () => { page++; render(); };

  function current() {
    let rows = getRows();
    if (search) rows = rows.filter(r => r.some(v => v !== null && String(v).toLowerCase().includes(search)));
    if (sortIdx !== null) {
      const i = sortIdx, d = sortDir;
      rows = [...rows].sort((a, b) => {
        const x = a[i], y = b[i];
        if (x === null) return 1; if (y === null) return -1;
        return (x < y ? -1 : x > y ? 1 : 0) * d;
      });
    }
    return rows;
  }

  function render() {
    const rows = current();
    const pages = Math.max(1, Math.ceil(rows.length / pageSize));
    if (page >= pages) page = pages - 1;
    const slice = rows.slice(page * pageSize, page * pageSize + pageSize);
    tbody.innerHTML = slice.map(r => '<tr>' + r.map((v, i) =>
      '<td class="' + (COLS[i].type === 'NUMBER' ? 'num' : '') + '">' + esc(cellText(v)) + '</td>').join('') + '</tr>').join('');
    thead.querySelectorAll('th').forEach(th => {
      th.querySelector('.arr').textContent = (+th.dataset.i === sortIdx) ? (sortDir === 1 ? ' ▲' : ' ▼') : '';
    });
    $('.tinfo', container).textContent = rows.length.toLocaleString() + ' rows';
    $('.pg', container).textContent = 'Page ' + (page + 1) + ' of ' + pages;
    $('.prev', container).disabled = page === 0;
    $('.next', container).disabled = page >= pages - 1;
  }
  render();
  return { render, rows: current };
}

function downloadCsv(rows) {
  const q = v => { const s = v === null || v === undefined ? '' : String(v); return /[",\n]/.test(s) ? '"' + s.replace(/"/g, '""') + '"' : s; };
  const text = [COLS.map(c => q(c.name)).join(',')].concat(rows.map(r => r.map(q).join(','))).join('\n');
  const a = document.createElement('a');
  a.href = URL.createObjectURL(new Blob(['﻿' + text], { type: 'text/csv;charset=utf-8' }));
  a.download = 'filtered-data.csv';
  a.click();
  URL.revokeObjectURL(a.href);
}
