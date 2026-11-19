/* ===== Helpers shared by the three test-case reports ===== */
const TC = DATA.testCase;
const STATUS_ORDER = ['PASS', 'FAIL', 'BLOCKED', 'SKIPPED', 'NOT RUN', 'OTHER'];
const STATUS_LABEL = { 'PASS': 'Passed', 'FAIL': 'Failed', 'BLOCKED': 'Blocked', 'SKIPPED': 'Skipped', 'NOT RUN': 'Not run', 'OTHER': 'Other' };
const STATUS_COLOR = { 'PASS': '#16a34a', 'FAIL': '#dc2626', 'BLOCKED': '#f59e0b', 'SKIPPED': '#94a3b8', 'NOT RUN': '#cbd5e1', 'OTHER': '#8b5cf6' };

function normStatus(v) {
  if (v === null || v === undefined || String(v).trim() === '') return 'NOT RUN';
  const s = String(v).trim().toLowerCase();
  if (/^(pass|passed|ok|success|successful)$/.test(s)) return 'PASS';
  if (/^(fail|failed|failure|error)$/.test(s)) return 'FAIL';
  if (/^block/.test(s)) return 'BLOCKED';
  if (/^(skip|skipped|n\/?a|not applicable)$/.test(s)) return 'SKIPPED';
  if (/^(not run|not executed|not tested|untested|pending|no run|todo|to do|in progress|inprogress|open)$/.test(s)) return 'NOT RUN';
  return 'OTHER';
}

const CASES = ROWS.map((r, i) => ({
  i, r,
  id: TC.id >= 0 ? r[TC.id] : null,
  title: TC.title >= 0 ? r[TC.title] : null,
  section: TC.section >= 0 ? r[TC.section] : null,
  status: normStatus(r[TC.status])
}));
const SECTIONS = [...new Set(CASES.map(c => c.section).filter(Boolean))];

function countStatus(list) {
  const m = { 'PASS': 0, 'FAIL': 0, 'BLOCKED': 0, 'SKIPPED': 0, 'NOT RUN': 0, 'OTHER': 0 };
  list.forEach(c => { m[c.status]++; });
  return m;
}

// Pass rate = passed / executed (cases that were run: not "not run" and not "skipped").
function passRate(counts) {
  const executed = counts['PASS'] + counts['FAIL'] + counts['BLOCKED'] + counts['OTHER'];
  return executed === 0 ? null : counts['PASS'] / executed * 100;
}
function pct(v) { return v === null ? '–' : (v >= 99.95 || v === 0 ? Math.round(v) : v.toFixed(1)) + '%'; }
function label(c) { return c.id ? String(c.id) : 'Row ' + (c.i + 1); }

/* ----- header: title and the facts found in the sheet (Ticket, Screen Name, Date ...) ----- */
function setTcHeader() {
  const heading = (DATA.meta && DATA.meta['Screen Name'] && DATA.meta['Screen Name'][0]) || DATA.title;
  document.title = heading + ' – test report';
  const t = document.getElementById('title'); if (t) t.textContent = heading;
  const s = document.getElementById('sub');
  if (s) s.textContent = DATA.fileName + (DATA.sheetName ? ' · sheet "' + DATA.sheetName + '"' : '') + ' · ' +
    CASES.length.toLocaleString() + ' test cases · generated ' + DATA.generatedAt;
  const box = document.getElementById('meta');
  if (box) {
    const parts = [];
    for (const key of Object.keys(DATA.meta || {})) {
      const vals = DATA.meta[key].map(v => {
        if (/^https?:\/\//i.test(v)) {
          const short = v.replace(/^https?:\/\//i, '').replace(/\/+$/, '').split('/').slice(-1)[0] || v;
          return '<a href="' + esc(v) + '" target="_blank" rel="noopener noreferrer">' + esc(short.length > 40 ? short.slice(0, 40) + '…' : short) + '</a>';
        }
        return esc(v);
      });
      parts.push('<span class="mchip"><b>' + esc(key) + '</b> ' + vals.join(', ') + '</span>');
    }
    box.innerHTML = parts.join('');
  }
  const w = document.getElementById('truncWarn');
  if (w && DATA.truncated) { w.hidden = false; w.textContent = 'Only the first ' + DATA.rowCount.toLocaleString() + ' of ' + DATA.totalRows.toLocaleString() + ' test cases are included.'; }
}

/* ----- filter bar: status chips, section dropdown, search ----- */
const FILTER = { status: '', section: '', q: '' };
function filteredCases() {
  const q = FILTER.q.trim().toLowerCase();
  return CASES.filter(c => {
    if (FILTER.status && c.status !== FILTER.status) return false;
    if (FILTER.section && c.section !== FILTER.section) return false;
    if (q && !c.r.some(v => v !== null && String(v).toLowerCase().includes(q))) return false;
    return true;
  });
}

function mountFilterBar(el, onChange) {
  const all = countStatus(CASES);
  const present = STATUS_ORDER.filter(s => all[s] > 0);
  el.innerHTML =
    '<div class="chips" role="group" aria-label="Filter by status">' +
    '<button class="chipb on" data-s="">All <i>' + CASES.length + '</i></button>' +
    present.map(s => '<button class="chipb" data-s="' + s + '"><span class="dot" style="background:' + STATUS_COLOR[s] + '"></span>' + STATUS_LABEL[s] + ' <i>' + all[s] + '</i></button>').join('') +
    '</div>' +
    (SECTIONS.length > 1 ? '<select class="fsel" aria-label="Filter by section"><option value="">All sections</option>' +
      SECTIONS.map(s => '<option value="' + esc(s) + '">' + esc(s) + '</option>').join('') + '</select>' : '') +
    '<input class="fsearch" type="search" placeholder="Search test cases…" aria-label="Search test cases">' +
    '<button class="freset" type="button">Reset</button>';
  const sync = () => { el.querySelectorAll('.chipb').forEach(b => b.classList.toggle('on', b.dataset.s === FILTER.status)); };
  el.querySelector('.chips').addEventListener('click', e => {
    const b = e.target.closest('.chipb'); if (!b) return;
    FILTER.status = b.dataset.s; sync(); onChange();
  });
  const sel = el.querySelector('.fsel');
  if (sel) sel.addEventListener('change', () => { FILTER.section = sel.value; onChange(); });
  el.querySelector('.fsearch').addEventListener('input', e => { FILTER.q = e.target.value; onChange(); });
  el.querySelector('.freset').addEventListener('click', () => {
    FILTER.status = ''; FILTER.section = ''; FILTER.q = '';
    if (sel) sel.selectedIndex = 0;
    el.querySelector('.fsearch').value = ''; sync(); onChange();
  });
  return { setStatus: s => { FILTER.status = s; sync(); onChange(); } };
}

/* ----- stacked horizontal bar: one bar per group, split by status ----- */
function stackedBar(canvasId, boxId, groups) {
  // groups: [[name, countsByStatus], ...]
  const box = document.getElementById(boxId);
  box.style.height = Math.max(120, groups.length * 34 + 60) + 'px';
  const usedStatuses = STATUS_ORDER.filter(s => groups.some(g => g[1][s] > 0));
  draw(canvasId, {
    type: 'bar',
    data: {
      labels: groups.map(g => g[0].length > 46 ? g[0].slice(0, 45) + '…' : g[0]),
      datasets: usedStatuses.map(s => ({ label: STATUS_LABEL[s], data: groups.map(g => g[1][s]), backgroundColor: STATUS_COLOR[s], borderWidth: 0 }))
    },
    options: {
      indexAxis: 'y',
      scales: { x: { stacked: true, ticks: { precision: 0 } }, y: { stacked: true, grid: { display: false } } },
      plugins: { legend: { position: 'bottom', labels: { usePointStyle: true, boxWidth: 8 } } }
    }
  });
}

function statusBadge(status, rawText) {
  const text = rawText && String(rawText).trim() ? String(rawText).trim() : STATUS_LABEL[status];
  return '<span class="sbadge s-' + status.replace(' ', '') + '">' + esc(text) + '</span>';
}

/* base look for badges and the filter bar (each report adds its own layout) */
(function () {
  const css = `
  .chips{display:flex;flex-wrap:wrap;gap:8px}
  .chipb{border:1px solid var(--line);background:#fff;color:var(--ink);border-radius:999px;padding:6px 12px;font:inherit;font-size:13.5px;cursor:pointer;display:inline-flex;align-items:center;gap:6px}
  .chipb i{font-style:normal;background:#eef1f6;border-radius:10px;padding:0 7px;font-size:12px;color:var(--muted)}
  .chipb.on{background:var(--brand);border-color:var(--brand);color:#fff}
  .chipb.on i{background:rgba(255,255,255,.25);color:#fff}
  .dot{width:9px;height:9px;border-radius:50%;display:inline-block}
  .fsel,.fsearch{border:1px solid var(--line);border-radius:8px;padding:7px 10px;font:inherit;font-size:14px;background:#fff;color:var(--ink);min-width:0}
  .fsearch{flex:1;min-width:180px}
  .freset{background:#eef1f6;color:var(--ink);border:0;border-radius:8px;padding:7px 14px;cursor:pointer;font:inherit;font-size:14px}
  .sbadge{display:inline-block;padding:3px 11px;border-radius:999px;font-size:12px;font-weight:700;letter-spacing:.02em;white-space:nowrap}
  .s-PASS{background:#dcfce7;color:#166534}.s-FAIL{background:#fee2e2;color:#991b1b}.s-BLOCKED{background:#fef3c7;color:#92400e}
  .s-SKIPPED{background:#e2e8f0;color:#475569}.s-NOTRUN{background:#f1f5f9;color:#64748b}.s-OTHER{background:#ede9fe;color:#5b21b6}
  .mchip{display:inline-block;background:rgba(255,255,255,.18);border-radius:999px;padding:3px 12px;margin:4px 8px 0 0;font-size:13px}
  .mchip b{opacity:.8;font-weight:600;margin-right:4px}
  .mchip a{color:inherit;text-decoration:underline}
  .warn{background:#fff4e0;border:1px solid #ffd9a0;color:#8a5200;padding:10px 14px;border-radius:10px;margin-bottom:14px;font-size:14px}
  `;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
})();

/* ===== The test cases themselves: shown exactly as written in the sheet ===== */
// Only content that exists in the file is shown. Nothing is added or invented.
// A cell that only holds a placeholder such as "-", "—" or "N/A" means "nothing here".
function hasText(v) {
  if (v === null || v === undefined) return false;
  return !/^(?:[-–—_.\s]*|n\/?a|nil|none|null)$/i.test(String(v).trim());
}

function caseDetails(c) {
  const skip = [TC.id, TC.title, TC.section, TC.status];
  const order = [TC.precondition, TC.steps, TC.expected].filter(i => i >= 0);
  COLS.forEach(col => { if (!skip.includes(col.i) && !order.includes(col.i)) order.push(col.i); });
  return order.filter(i => hasText(c.r[i])).map(i =>
    '<div><div class="tlbl">' + esc(COLS[i].name) + '</div><div class="ttxt">' + esc(typeof c.r[i] === 'number' ? c.r[i].toLocaleString() : String(c.r[i])) + '</div></div>').join('');
}

function caseRowHtml(c, open) {
  const d = caseDetails(c);
  const head = (c.id ? '<span class="tid">' + esc(c.id) + '</span>' : '') + '<span class="tt">' + esc(c.title || (c.id ? '' : label(c))) + '</span>' + statusBadge(c.status, c.r[TC.status]);
  const cls = 'tcase tc-' + c.status.replace(' ', '');
  if (!d) return '<div class="' + cls + ' nodetail"><div class="thead">' + head + '</div></div>';
  return '<details class="' + cls + '"' + (open ? ' open' : '') + '><summary class="thead">' + head + '</summary><div class="tbody">' + d + '</div></details>';
}

// Test cases grouped by section, in the order of the sheet. limit = maximum number of cases drawn.
function groupedCasesHtml(list, limit, openCases) {
  if (!list.length) return '<div class="tnone">No test cases match the current filters.</div>';
  const keep = new Set(list.slice(0, limit));
  const groups = new Map();
  list.forEach(c => { const k = c.section || ''; if (!groups.has(k)) groups.set(k, []); groups.get(k).push(c); });
  let html = '';
  groups.forEach((items, name) => {
    const drawn = items.filter(c => keep.has(c));
    if (!drawn.length) return;
    const counts = countStatus(items);
    const chips = STATUS_ORDER.filter(s => counts[s] > 0).map(s =>
      '<span class="gchip" style="--c:' + STATUS_COLOR[s] + '"><i></i>' + counts[s] + ' ' + STATUS_LABEL[s].toLowerCase() + '</span>').join('');
    html += (SECTIONS.length > 1 || name ? '<details class="tgroup" open><summary><b>' + esc(name || 'Test cases') + '</b><span class="gcount">' + items.length +
      ' test case' + (items.length === 1 ? '' : 's') + '</span><span class="gchips">' + chips + '</span></summary>' : '<div class="tgroup flat">') +
      drawn.map(c => caseRowHtml(c, openCases)).join('') + (SECTIONS.length > 1 || name ? '</details>' : '</div>');
  });
  return html;
}

(function () {
  const css = `
  .tgroup{margin:0 0 14px}
  .tgroup>summary{list-style:none;cursor:pointer;display:flex;gap:12px;align-items:center;flex-wrap:wrap;padding:10px 4px;border-bottom:2px solid var(--line);margin-bottom:6px}
  .tgroup>summary::-webkit-details-marker{display:none}
  .tgroup>summary::before{content:"▾";color:var(--muted)} .tgroup:not([open])>summary::before{content:"▸"}
  .tgroup>summary b{font-size:15px}
  .gcount{color:var(--muted);font-size:13px}
  .gchips{display:flex;gap:6px;flex-wrap:wrap;margin-left:auto}
  .gchip{display:inline-flex;align-items:center;gap:5px;font-size:12px;color:var(--muted);background:#f3f4f8;border-radius:999px;padding:2px 10px}
  .gchip i{width:8px;height:8px;border-radius:50%;background:var(--c)}
  .tcase{border:1px solid var(--line);border-left:4px solid #94a3b8;border-radius:10px;margin:6px 0;background:#fff}
  .tcase.tc-PASS{border-left-color:#16a34a}.tcase.tc-FAIL{border-left-color:#dc2626}.tcase.tc-BLOCKED{border-left-color:#f59e0b}
  .tcase.tc-SKIPPED{border-left-color:#94a3b8}.tcase.tc-NOTRUN{border-left-color:#cbd5e1}.tcase.tc-OTHER{border-left-color:#8b5cf6}
  .thead{display:flex;gap:12px;align-items:center;padding:9px 14px;list-style:none;cursor:pointer}
  .tcase.nodetail .thead{cursor:default}
  .thead::-webkit-details-marker{display:none}
  details.tcase>summary::after{content:"▾";color:var(--muted);margin-left:2px}
  details.tcase[open]>summary::after{content:"▴"}
  .tid{font-weight:700;white-space:nowrap;min-width:78px}
  .tt{flex:1;min-width:0;word-break:break-word}
  .tbody{padding:2px 18px 14px 18px;display:grid;gap:10px;border-top:1px dashed var(--line);margin:0 14px;padding-left:4px}
  .tlbl{font-size:11px;text-transform:uppercase;letter-spacing:.06em;color:var(--muted);margin:8px 0 2px}
  .ttxt{white-space:pre-wrap;line-height:1.55;font-size:14px;word-break:break-word}
  .tnone{text-align:center;color:var(--muted);padding:26px 10px}
  .tmore[hidden]{display:none}
  .tmore{display:block;margin:12px auto 0;background:#eef1f6;color:var(--ink);border:0;border-radius:8px;padding:8px 18px;cursor:pointer;font:inherit}
  .listhead{display:flex;justify-content:space-between;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:6px}
  .listhead h3{margin:0}
  .listbtn{background:#fff;border:1px solid var(--line);border-radius:8px;padding:6px 14px;cursor:pointer;font:inherit;font-size:14px;color:var(--ink)}
  .listbtn:hover{border-color:var(--brand);color:var(--brand)}
  .hint{color:var(--muted);font-size:13px;margin:0 0 8px}
  `;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
})();

/* ===== Grouping used by the visual test-case reports ===== */
function moduleKey(c) {
  const m = c.id ? String(c.id).match(/^(.*?)[-_ ]?\d+$/) : null;
  return m && m[1] ? m[1] : null;
}
// Sections when the sheet has them; otherwise modules taken from the ID prefix; otherwise one group.
function caseGroups() {
  if (SECTIONS.length > 1) return SECTIONS.map(s => [s, CASES.filter(c => c.section === s)]);
  const mods = new Map();
  CASES.forEach(c => { const k = moduleKey(c); if (k) { if (!mods.has(k)) mods.set(k, []); mods.get(k).push(c); } });
  if (mods.size >= 2 && mods.size <= 40) return [...mods.entries()];
  return [['All test cases', CASES]];
}
function shortName(s, n) { n = n || 34; return s.length > n ? s.slice(0, n - 1) + '…' : s; }
