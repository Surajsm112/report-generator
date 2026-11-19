(() => {
  const $ = s => document.querySelector(s);
  const views = { upload: $('#uploadView'), progress: $('#progressView'), result: $('#resultView') };
  const drop = $('#drop'), fileInput = $('#file'), errorBox = $('#error');
  let jobId = null, poller = null, reports = [], current = 1;

  function show(name) {
    Object.entries(views).forEach(([k, el]) => { el.hidden = k !== name; });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }

  function showError(text) { errorBox.textContent = text; errorBox.hidden = false; }

  // ---------- upload ----------
  drop.addEventListener('click', () => fileInput.click());
  drop.addEventListener('keydown', e => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); fileInput.click(); } });
  fileInput.addEventListener('change', () => { if (fileInput.files[0]) upload(fileInput.files[0]); });
  ['dragenter', 'dragover'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.add('over'); }));
  ['dragleave', 'drop'].forEach(ev => drop.addEventListener(ev, e => { e.preventDefault(); drop.classList.remove('over'); }));
  drop.addEventListener('drop', e => { const f = e.dataTransfer.files[0]; if (f) upload(f); });

  $('#sampleBtn').addEventListener('click', async () => {
    const blob = await (await fetch('samples/sales-sample.csv')).blob();
    upload(new File([blob], 'sales-sample.csv', { type: 'text/csv' }));
  });
  $('#again').addEventListener('click', reset);
  $('#zip').addEventListener('click', () => { if (jobId) window.location = 'api/jobs/' + jobId + '/download-all'; });

  async function upload(file) {
    errorBox.hidden = true;
    if (file.size > 20 * 1024 * 1024) { showError('This file is larger than 20 MB. Please upload a smaller one.'); return; }
    const form = new FormData();
    form.append('file', file);
    $('#fileLabel').textContent = file.name + ' · ' + (file.size / 1024).toFixed(0) + ' KB';
    setProgress({ status: 'QUEUED', progress: 3, message: 'Uploading…' });
    show('progress');
    try {
      const res = await fetch('api/jobs', { method: 'POST', body: form });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) throw new Error(body.message || 'Upload failed. Please try again.');
      jobId = body.jobId;
      poll();
    } catch (e) {
      show('upload');
      showError(e.message);
    } finally {
      fileInput.value = '';
    }
  }

  // ---------- progress ----------
  const order = ['PARSING', 'ANALYZING', 'GENERATING', 'DONE'];

  function setProgress(s) {
    $('#barFill').style.width = s.progress + '%';
    $('#msg').textContent = s.message || '';
    const at = order.indexOf(s.status);
    document.querySelectorAll('#steps li').forEach((li, i) => {
      li.classList.toggle('done', s.status === 'DONE' || i < at);
      li.classList.toggle('active', s.status !== 'DONE' && i === Math.max(at, 0));
    });
  }

  function poll() {
    clearInterval(poller);
    poller = setInterval(async () => {
      try {
        const res = await fetch('api/jobs/' + jobId);
        if (!res.ok) throw new Error('We lost track of this upload. Please upload the file again.');
        const s = await res.json();
        setProgress(s);
        if (s.status === 'DONE') { clearInterval(poller); setTimeout(() => showResults(s), 500); }
        if (s.status === 'FAILED') { clearInterval(poller); show('upload'); showError(s.message); }
      } catch (e) { clearInterval(poller); show('upload'); showError(e.message); }
    }, 600);
  }

  // ---------- results ----------
  const thumbs = {
    1: '<div class="thumb t1"><div class="k"><b></b><b></b><b></b></div><div class="row"><i style="height:40%"></i><i style="height:70%"></i><i style="height:55%"></i><i style="height:90%"></i><i style="height:65%"></i></div></div>',
    2: '<div class="thumb t2"><div class="line h"></div><div class="line"></div><div class="line"></div><div class="line"></div><div class="row"><i style="height:30%"></i><i style="height:60%"></i><i style="height:45%"></i></div></div>',
    3: '<div class="thumb t3"><div class="line" style="width:70%"></div><div class="row"><i style="height:35%"></i><i style="height:80%"></i><i style="height:55%"></i><i style="height:95%"></i><i style="height:45%"></i><i style="height:70%"></i></div></div>'
  };

  const tcThumbs = {
    1: '<div class="thumb tt1"><div class="donutmock"></div><div class="k"><b></b><b></b><b></b></div></div>',
    2: '<div class="thumb t2"><div class="line h"></div><div class="line"></div><div class="line"></div><div class="line"></div><div class="pills"><i class="g"></i><i class="r"></i><i class="g"></i><i class="g"></i></div></div>',
    3: '<div class="thumb tt3"><div class="grid"><b></b><b></b><b></b><b></b></div><div class="line" style="width:60%"></div></div>'
  };

  // Reports 4-7 get a gradient card with their icon; reports 1-3 keep their small mock-ups.
  const gradients = {
    data: { 4: ['#0891b2', '#22d3ee'], 5: ['#ea580c', '#ec4899'], 6: ['#4f46e5', '#a855f7'], 7: ['#059669', '#d4a017'] },
    testcases: { 4: ['#0b8f6a', '#1ec8a5'], 5: ['#d1344a', '#ff8a3d'], 6: ['#2f4bd8', '#64748b'], 7: ['#3b2fc9', '#c026d3'] }
  };
  function thumbFor(r, isTc) {
    const base = isTc ? tcThumbs[r.no] : thumbs[r.no];
    if (base) return base;
    const g = (gradients[isTc ? 'testcases' : 'data'][r.no]) || ['#4f46e5', '#7c3aed'];
    return '<div class="thumb tg" style="background:linear-gradient(120deg,' + g[0] + ',' + g[1] + ')"><span class="ticon">' + (r.icon || '📊') + '</span>' +
      '<div class="row"><i style="height:35%"></i><i style="height:65%"></i><i style="height:50%"></i><i style="height:85%"></i><i style="height:60%"></i><i style="height:75%"></i></div></div>';
  }

  function showResults(s) {
    reports = s.reports;
    const isTc = s.kind === 'testcases';
    const isSheet = s.kind === 'sheet';
    const kn = $('#kindNote');
    kn.hidden = !(isTc || isSheet);
    if (isTc) kn.textContent = 'We recognised this as a test-case sheet, so these reports are built around pass / fail results, sections and test steps.';
    if (isSheet) kn.textContent = 'This file is a worksheet or form (labels and values placed around the sheet), not a table with column headings. So we show it as it is laid out instead of inventing column names.';
    $('#summary').textContent = s.fileName + ' · ' + s.rows.toLocaleString() + ' rows · ' + s.columns + ' columns';
    const note = $('#truncNote');
    note.hidden = !s.truncated;
    if (s.truncated) note.textContent = 'Your file has ' + s.totalRows.toLocaleString() + ' rows. The reports include the first ' + s.rows.toLocaleString() + '.';
    $('#reportCards').innerHTML = reports.map(r =>
      '<div class="rcard">' + thumbFor(r, isTc) + '<div class="rbody"><h3>' + r.no + '. ' + escapeHtml(r.name) + '</h3><p>' + escapeHtml(r.description) +
      '</p><div class="rbtns"><button class="ghost" data-preview="' + r.no + '">Preview</button><button data-download="' + r.no + '">Download</button></div></div></div>').join('');
    show('result');
  }

  document.addEventListener('click', e => {
    const p = e.target.closest('[data-preview]');
    const d = e.target.closest('[data-download]');
    if (p) openPreview(+p.dataset.preview);
    if (d) download(+d.dataset.download);
  });

  function download(no) { window.location = 'api/jobs/' + jobId + '/reports/' + no + '/download'; }

  function openPreview(no) {
    current = no;
    $('#tabs').innerHTML = reports.map(r => '<button data-tab="' + r.no + '" class="' + (r.no === no ? 'on' : '') + '">' + r.no + '. ' + escapeHtml(r.name) + '</button>').join('');
    $('#frame').src = 'api/jobs/' + jobId + '/reports/' + no + '/preview';
    $('#modal').hidden = false;
    document.body.style.overflow = 'hidden';
  }

  $('#tabs').addEventListener('click', e => { const t = e.target.closest('[data-tab]'); if (t) openPreview(+t.dataset.tab); });
  $('#dl').addEventListener('click', () => download(current));
  $('#closeModal').addEventListener('click', closePreview);
  $('#modal').addEventListener('click', e => { if (e.target.id === 'modal') closePreview(); });
  document.addEventListener('keydown', e => { if (e.key === 'Escape') closePreview(); });

  function closePreview() { $('#modal').hidden = true; $('#frame').src = 'about:blank'; document.body.style.overflow = ''; }

  function reset() { clearInterval(poller); jobId = null; show('upload'); }

  function escapeHtml(s) { return String(s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c])); }
})();
