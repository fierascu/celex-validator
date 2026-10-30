// ── Configuration ──────────────────────────────────────────────────────────
const API_BASE = '/api';

// ── Utilities ──────────────────────────────────────────────────────────────
function el(id) { return document.getElementById(id); }

// Escape any server- or user-supplied value before it goes into innerHTML.
function esc(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

// Only ever link to EUR-Lex; returns null for anything else.
function safeEurLexUrl(url) {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && u.hostname === 'eur-lex.europa.eu' ? u.href : null;
  } catch {
    return null;
  }
}

function segment(value, label, cssClass) {
  return `<div class="celex-segment ${cssClass}">
    <div class="seg-value">${esc(value)}</div>
    <div class="seg-label">${esc(label)}</div>
  </div>`;
}

function pill(value, cls) {
  return value ? `<span class="pill ${cls}">${esc(value)}</span>` : '–';
}

// Wrap an emoji so screen readers skip it.
function icon(emoji) {
  return `<span aria-hidden="true">${emoji}</span>`;
}

// ── Validate ───────────────────────────────────────────────────────────────
// Only the latest request may update the page; older ones are aborted.
let validateController = null;

async function validate() {
  const raw = el('celexInput').value.trim();
  if (!raw) { clearAll(); return; }

  validateController?.abort();
  const controller = new AbortController();
  validateController = controller;

  const input = el('celexInput');
  input.removeAttribute('aria-invalid');
  el('statusArea').setAttribute('aria-busy', 'true');

  el('statusArea').innerHTML = `<span class="status-badge idle">${icon('⏳')} Checking…</span>`;
  el('errorArea').innerHTML  = '';
  el('visualArea').innerHTML = '';
  resetDetails();

  try {
    const res  = await fetch(`${API_BASE}/validate?celex=${encodeURIComponent(raw)}`,
                             { signal: controller.signal });
    const data = await res.json();
    if (controller !== validateController) return;

    if (data.valid) {
      const eurLexUrl = safeEurLexUrl(data.eurLexUrl);
      const linkBtn = eurLexUrl
        ? `<a class="eurlex-btn" href="${esc(eurLexUrl)}" target="_blank" rel="noopener">
             Open in EUR-Lex ${icon('↗')}<span class="sr-only"> (opens in new tab)</span>
           </a>`
        : '';
      const existSpan = eurLexUrl
        ? `<span class="exist-badge checking" id="existBadge">${icon('⏳')} Checking EUR-Lex…</span>`
        : '';
      el('statusArea').innerHTML =
        `<span class="status-badge valid">${icon('✔')} Valid syntax</span>${linkBtn}${existSpan}`;
      renderVisual(data);
      renderDetails(data);
      el('detailsToggleWrap').hidden = false;
      if (eurLexUrl) checkEurLexExists(eurLexUrl, data.input);
    } else {
      input.setAttribute('aria-invalid', 'true');
      el('statusArea').innerHTML =
        `<span class="status-badge invalid">${icon('✘')} Invalid</span>`;
      el('errorArea').innerHTML =
        `<div class="error-msg">${esc(data.error)}</div>`;
    }
  } catch (err) {
    if (err.name === 'AbortError' || controller !== validateController) return;
    el('statusArea').innerHTML =
      `<span class="status-badge invalid">${icon('✘')} Connection error</span>`;
    el('errorArea').innerHTML =
      `<div class="error-msg">Could not reach the backend at <code>${API_BASE}</code>. Is Quarkus running?<br><small>${esc(err)}</small></div>`;
  } finally {
    if (controller === validateController) el('statusArea').removeAttribute('aria-busy');
  }
}

// Hide and collapse the details panel.
function resetDetails() {
  el('detailsPanel').classList.remove('open');
  el('detailsPanel').innerHTML = '';
  el('detailsToggle').setAttribute('aria-expanded', 'false');
  el('detailsToggleWrap').hidden = true;
}

// ── Visual breakdown ───────────────────────────────────────────────────────
function renderVisual(data) {
  let html = '<div class="celex-display" role="group" aria-label="CELEX number breakdown">';
  html += segment(data.sector,             'Sector',    'seg-sector');
  if (data.year)
    html += segment(data.year,             'Year',      'seg-year');
  if (data.documentTypeCode)
    html += segment(data.documentTypeCode, 'Doc type',  'seg-doctype');
  if (data.number)
    html += segment(data.number,           'Number',    'seg-number');
  if (data.suffix)
    html += segment(data.suffix,           'Suffix',    'seg-suffix');
  html += '</div>';
  el('visualArea').innerHTML = html;
}

// ── Details table ──────────────────────────────────────────────────────────
function renderDetails(data) {
  const eurLexUrl = safeEurLexUrl(data.eurLexUrl);
  const rows = [
    ['Input',         `<code>${esc(data.input)}</code>`],
    ['Sector',        `${pill(data.sector, 'pill-sector')} ${esc(data.sectorDescription)}`],
    ['Year',          data.year ? pill(data.year, 'pill-year') : '–'],
    ['Document type', data.documentTypeCode
                        ? `${pill(data.documentTypeCode, 'pill-doctype')} ${esc(data.documentTypeDescription)}`
                        : '–'],
    ['Number',        data.number ? pill(data.number, 'pill-number') : '–'],
    ['Suffix',        data.suffix ? pill(data.suffix, 'pill-suffix') : '–'],
    ['Description',   `<em>${esc(data.humanDescription)}</em>`],
    ['EUR-Lex link',  eurLexUrl
                        ? `<a href="${esc(eurLexUrl)}" target="_blank" rel="noopener" class="eur-link">${esc(eurLexUrl)}<span class="sr-only"> (opens in new tab)</span></a>`
                        : '–'],
  ];

  el('detailsPanel').innerHTML = `
    <table class="result-table">
      <caption>Parsed CELEX components</caption>
      <tbody>
        ${rows.map(([k, v]) =>
          `<tr><th scope="row">${k}</th><td>${v}</td></tr>`
        ).join('')}
      </tbody>
    </table>`;
}

// ── EUR-Lex existence check ────────────────────────────────────────────────
const CHECK_URL_TIMEOUT_MS = 10000;

async function checkEurLexExists(url, celex) {
  const badge = el('existBadge');
  if (!badge) return;
  const controller = new AbortController();
  const timeoutId  = setTimeout(() => controller.abort(), CHECK_URL_TIMEOUT_MS);
  try {
    const res  = await fetch(
      `${API_BASE}/check-url?url=${encodeURIComponent(url)}&celex=${encodeURIComponent(celex)}`,
      { signal: controller.signal }
    );
    const data = await res.json();
    if (data.error) {
      badge.className = 'exist-badge error';
      badge.innerHTML = `${icon('⚠')} Could not check`;
    } else if (data.found) {
      badge.className = 'exist-badge found';
      badge.innerHTML = `${icon('✔')} Found in EUR-Lex`;
    } else {
      badge.className = 'exist-badge notfound';
      badge.innerHTML = `${icon('✘')} Not found in EUR-Lex`;
    }
  } catch (e) {
    badge.className = 'exist-badge error';
    badge.innerHTML = e.name === 'AbortError'
      ? `${icon('⚠')} Check timed out`
      : `${icon('⚠')} Could not check`;
  } finally {
    clearTimeout(timeoutId);
  }
}

// ── Clear ──────────────────────────────────────────────────────────────────
function clearAll() {
  clearTimeout(debounceTimer);
  validateController?.abort();
  validateController = null;
  const input = el('celexInput');
  input.value = '';
  input.removeAttribute('aria-invalid');
  el('statusArea').removeAttribute('aria-busy');
  el('statusArea').innerHTML  = '';
  el('errorArea').innerHTML   = '';
  el('visualArea').innerHTML  = '';
  resetDetails();
}

// ── Details toggle ─────────────────────────────────────────────────────────
el('detailsToggle').addEventListener('click', () => {
  const expanded = el('detailsToggle').getAttribute('aria-expanded') === 'true';
  el('detailsToggle').setAttribute('aria-expanded', String(!expanded));
  el('detailsPanel').classList.toggle('open', !expanded);
});

// ── Examples toggle ────────────────────────────────────────────────────────
el('examplesToggle').addEventListener('click', () => {
  const expanded = el('examplesToggle').getAttribute('aria-expanded') === 'true';
  el('examplesToggle').setAttribute('aria-expanded', String(!expanded));
  el('examplesList').classList.toggle('open', !expanded);
});

// ── Example chip clicks ────────────────────────────────────────────────────
document.querySelectorAll('.example-chip').forEach(chip =>
  chip.addEventListener('click', () => {
    el('celexInput').value = chip.textContent.trim();
    validate();
  })
);

// ── Enter key + live-validate (debounced) ──────────────────────────────────
el('celexInput').addEventListener('keydown', e => { if (e.key === 'Enter') validate(); });

let debounceTimer;
el('celexInput').addEventListener('input', () => {
  clearTimeout(debounceTimer);
  debounceTimer = setTimeout(() => {
    if (el('celexInput').value.trim().length >= 5) validate();
  }, 450);
});

// ── Reference section: lazy-load on first open ─────────────────────────────
let refLoaded = false;

el('refDetails').addEventListener('toggle', () => {
  if (el('refDetails').open && !refLoaded) {
    refLoaded = true;
    loadSectors();
  }
});

// ── Init ───────────────────────────────────────────────────────────────────
el('celexInput').focus();

// ── Event wiring ───────────────────────────────────────────────────────────
el('validateBtn').addEventListener('click', validate);
el('clearBtn').addEventListener('click', clearAll);
el('sectorFilter').addEventListener('change', loadDocTypes);

// ── Load reference data (only called after the <details> is opened) ────────
async function loadSectors() {
  el('sectorGrid').innerHTML    = '<div class="empty-display">Loading…</div>';
  el('docTypeGrid').innerHTML   = '<div class="empty-display">Loading…</div>';
  try {
    const data = await (await fetch(`${API_BASE}/sectors`)).json();
    el('sectorGrid').innerHTML = data.map(s =>
      `<div class="ref-item">
        <span class="code">${esc(s.code)}</span>
        <span class="desc">${esc(s.description)}</span>
      </div>`
    ).join('');

    const sel = el('sectorFilter');
    data.forEach(s => {
      const opt = document.createElement('option');
      opt.value = s.code;
      opt.textContent = `${s.code} – ${s.description}`;
      sel.appendChild(opt);
    });

    // now load doc types with the populated filter
    await loadDocTypes();
  } catch {
    refLoaded = false;   // retry on next open
    el('sectorGrid').innerHTML =
      '<div class="empty-display">Backend not available — start Quarkus to load reference data.</div>';
    el('docTypeGrid').innerHTML = '';
  }
}

async function loadDocTypes() {
  const sector = el('sectorFilter').value;
  const url = sector
    ? `${API_BASE}/document-types?sector=${encodeURIComponent(sector)}`
    : `${API_BASE}/document-types`;
  try {
    const data = await (await fetch(url)).json();
    el('docTypeGrid').innerHTML = data.length === 0
      ? '<div class="empty-display">No document types for this sector.</div>'
      : data.map(d =>
          `<div class="ref-item">
            <span class="code">${esc(d.code)}</span>
            <span class="desc">${esc(d.description)}
              <em class="sectors"> (${esc([...d.sectors].join(', '))})</em>
            </span>
          </div>`
        ).join('');
  } catch {
    el('docTypeGrid').innerHTML =
      '<div class="empty-display">Backend not available.</div>';
  }
}
