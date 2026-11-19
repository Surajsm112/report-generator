/* ===== Look and chart helpers shared by the visual reports ===== */
function hexToRgb(h) { const n = parseInt(h.replace('#', ''), 16); return [(n >> 16) & 255, (n >> 8) & 255, n & 255]; }
function lerpColor(a, b, t) {
  const A = hexToRgb(a), B = hexToRgb(b), k = Math.max(0, Math.min(1, t));
  return 'rgb(' + A.map((v, i) => Math.round(v + (B[i] - v) * k)).join(',') + ')';
}
// 0..100 pass rate -> red, amber, green
function rateColor(p) {
  if (p === null || p === undefined) return '#cbd5e1';
  return p < 50 ? lerpColor('#dc2626', '#f59e0b', p / 50) : lerpColor('#f59e0b', '#16a34a', (p - 50) / 50);
}
// Chart.js scriptable colours: a gradient that follows the chart area
function vGradient(c1, c2) {
  return ctx => {
    const a = ctx.chart.chartArea; if (!a) return c1;
    const g = ctx.chart.ctx.createLinearGradient(0, a.bottom, 0, a.top);
    g.addColorStop(0, c2); g.addColorStop(1, c1); return g;
  };
}
function hGradient(c1, c2) {
  return ctx => {
    const a = ctx.chart.chartArea; if (!a) return c1;
    const g = ctx.chart.ctx.createLinearGradient(a.left, 0, a.right, 0);
    g.addColorStop(0, c1); g.addColorStop(1, c2); return g;
  };
}
function statCard(label, value, sub, c1, c2) {
  return '<div class="vstat" style="background:linear-gradient(135deg,' + c1 + ',' + c2 + ')"><div class="vl">' + esc(label) + '</div><div class="vv">' +
    esc(String(value)) + '</div>' + (sub ? '<div class="vs">' + esc(sub) + '</div>' : '') + '</div>';
}
function vcard(title, sub, inner, cls) {
  return '<div class="vcard ' + (cls || '') + '"><h3>' + esc(title) + '</h3>' + (sub ? '<div class="vsub">' + esc(sub) + '</div>' : '') + inner + '</div>';
}
function vbox(id, height) { return '<div class="vbox" style="height:' + (height || 260) + 'px"><canvas id="' + id + '"></canvas></div>'; }
function emptyNote(text) { return '<div class="vempty">' + esc(text) + '</div>'; }

if (typeof Chart !== 'undefined') {
  Chart.defaults.font.family = 'Segoe UI, system-ui, -apple-system, Arial, sans-serif';
  Chart.defaults.color = '#5b6678';
  Chart.defaults.borderColor = '#e8edf5';
}

(function () {
  const css = `
  *{box-sizing:border-box}
  body{margin:0;font-family:Segoe UI,system-ui,-apple-system,Arial,sans-serif;background:var(--bg,#f4f6fb);color:var(--ink,#1b2a41)}
  header{background:linear-gradient(120deg,var(--g1),var(--g2));color:#fff;padding:26px 24px 64px}
  header .tag{display:inline-block;background:rgba(255,255,255,.2);border-radius:20px;padding:3px 12px;font-size:12px;margin-bottom:8px;letter-spacing:.03em}
  header h1{margin:0 0 4px;font-size:27px} header p{margin:0;opacity:.9;font-size:14px}
  main{max-width:1240px;margin:-42px auto 40px;padding:0 16px}
  .vstats{display:grid;grid-template-columns:repeat(auto-fit,minmax(170px,1fr));gap:14px;margin-bottom:18px}
  .vstat{color:#fff;border-radius:16px;padding:16px 18px;box-shadow:0 8px 22px rgba(30,40,90,.18)}
  .vstat .vl{font-size:12px;text-transform:uppercase;letter-spacing:.06em;opacity:.9}
  .vstat .vv{font-size:32px;font-weight:700;line-height:1.15;margin-top:2px;word-break:break-word}
  .vstat .vs{font-size:12.5px;opacity:.9;margin-top:2px;word-break:break-word}
  .vgrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:16px;align-items:start}
  .vcard{background:#fff;border-radius:16px;padding:16px 20px 18px;box-shadow:0 6px 20px rgba(30,40,90,.08)}
  .vcard h3{margin:0 0 2px;font-size:16px} .vsub{color:#7a8599;font-size:12.5px;margin-bottom:10px}
  .vwide{grid-column:1/-1}
  .vbox{position:relative}
  .vempty{color:#8793a6;text-align:center;padding:46px 12px;font-size:14px}
  .vwarn{background:#fff4e0;border:1px solid #ffd9a0;color:#8a5200;padding:10px 14px;border-radius:10px;margin-bottom:14px;font-size:14px}
  table.vt{border-collapse:collapse;width:100%;font-size:13.5px}
  table.vt th{text-align:left;color:#6b778c;font-weight:600;font-size:12px;text-transform:uppercase;letter-spacing:.04em;padding:6px 8px;border-bottom:2px solid #e8edf5}
  table.vt td{padding:7px 8px;border-bottom:1px solid #eef1f7;vertical-align:middle}
  table.vt td.n,table.vt th.n{text-align:right;font-variant-numeric:tabular-nums}
  .pbar{height:9px;border-radius:6px;background:#edf0f6;overflow:hidden;min-width:70px} .pbar i{display:block;height:100%;border-radius:6px}
  @media(max-width:520px){.vgrid{grid-template-columns:1fr}}
  @media print{header{-webkit-print-color-adjust:exact;print-color-adjust:exact}.vstat{-webkit-print-color-adjust:exact;print-color-adjust:exact}}
  `;
  const st = document.createElement('style'); st.textContent = css; document.head.appendChild(st);
})();
