'use strict';

/* ---------- small helpers ---------- */
const $ = (sel, root = document) => root.querySelector(sel);
const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (ch) =>
  ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));

const CATEGORIES = {
  POTHOLE: 'Pothole or road damage', STREETLIGHT: 'Streetlight', WATER_SUPPLY: 'Water supply',
  DRAINAGE: 'Drainage or sewage', GARBAGE: 'Garbage', OTHER: 'Something else'
};
const STATUS_LABEL = { REPORTED: 'Reported', ASSIGNED: 'Assigned', IN_PROGRESS: 'In progress', RESOLVED: 'Resolved', REJECTED: 'Rejected' };
const fmt = (iso) => iso ? new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', year: 'numeric', hour: 'numeric', minute: '2-digit' }) : '';
const pill = (s) => `<span class="pill ${s}">${STATUS_LABEL[s]}</span>`;
const plate = (id) => `<span class="plate">${esc(id)}</span>`;
const options = (map, selected) => Object.entries(map).map(([k, v]) =>
  `<option value="${esc(k)}"${k === selected ? ' selected' : ''}>${esc(v)}</option>`).join('');

let toastTimer;
function toast(message, isError = false) {
  const el = $('#toast');
  el.textContent = message;
  el.className = 'show' + (isError ? ' error' : '');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = ''; }, 5000);
}

/* ---------- session ---------- */
const session = {
  token: localStorage.getItem('cf_token'),
  user: JSON.parse(localStorage.getItem('cf_user') || 'null'),
  departments: []
};
let refreshList = () => {};

function saveSession(auth) {
  session.token = auth.token;
  session.user = { name: auth.name, email: auth.email, role: auth.role, department: auth.department };
  localStorage.setItem('cf_token', session.token);
  localStorage.setItem('cf_user', JSON.stringify(session.user));
}

function logout() {
  localStorage.removeItem('cf_token');
  localStorage.removeItem('cf_user');
  session.token = null; session.user = null; session.departments = [];
  render();
}

async function api(path, { method = 'GET', body, form } = {}) {
  const headers = {};
  if (session.token) headers.Authorization = 'Bearer ' + session.token;
  let payload;
  if (form) payload = form;
  else if (body) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }

  const res = await fetch('/api' + path, { method, headers, body: payload });
  if (res.status === 401 && session.token) {
    logout();
    throw new Error('Your session has ended. Please sign in again.');
  }
  const data = await res.json().catch(() => null);
  if (!res.ok) throw new Error((data && data.message) || 'Something went wrong. Please try again.');
  return data;
}

/* ---------- shared UI pieces ---------- */
function timeline(history) {
  if (!history || !history.length) return '<p class="empty">No updates yet.</p>';
  return `<ol class="timeline">${history.map((h) => `
    <li>${pill(h.to)}
      ${h.remarks ? `<div>${esc(h.remarks)}</div>` : ''}
      <small>${esc(h.changedBy)}, ${fmt(h.changedAt)}</small>
    </li>`).join('')}</ol>`;
}

function detailHtml(c, withActions) {
  const admin = session.user && session.user.role === 'ADMIN';
  const canAssign = withActions && admin && (c.status === 'REPORTED' || c.status === 'ASSIGNED');
  const canUpdate = withActions && session.user && session.user.role !== 'CITIZEN' && c.nextStatuses.length > 0;
  const deptOptions = Object.fromEntries(session.departments.map((d) => [d.id, d.name]));
  const current = session.departments.find((d) => d.name === c.department);

  return `
    <div class="dlg-head">${plate(c.trackingId)} ${pill(c.status)}</div>
    <h2 id="dlgTitle">${esc(c.title)}</h2>
    <dl class="meta">
      <div><dt>Type</dt><dd>${esc(CATEGORIES[c.category])}</dd></div>
      <div><dt>Location</dt><dd>${esc(c.location)}</dd></div>
      <div><dt>Handled by</dt><dd>${c.department ? esc(c.department) : 'Not assigned yet'}</dd></div>
      ${c.reportedBy ? `<div><dt>Reported by</dt><dd>${esc(c.reportedBy)}</dd></div>` : ''}
      <div><dt>Reported on</dt><dd>${fmt(c.createdAt)}</dd></div>
    </dl>
    <p>${esc(c.description)}</p>
    ${c.imageUrl ? `<img class="photo" src="${esc(c.imageUrl)}" alt="Photo of the problem">` : ''}
    <h3>Progress</h3>
    ${timeline(c.history)}
    ${canAssign || canUpdate ? `<div class="actions">
      ${canAssign ? `<form id="assignForm" class="stack">
        <label>Department<select name="departmentId">${options(deptOptions, current && String(current.id))}</select></label>
        <button class="btn primary" type="submit">${c.status === 'REPORTED' ? 'Assign department' : 'Re-assign department'}</button>
      </form>` : ''}
      ${canUpdate ? `<form id="statusForm" class="stack">
        <label>Next status<select name="status">${c.nextStatuses.map((s) => `<option value="${s}">${STATUS_LABEL[s]}</option>`).join('')}</select></label>
        <label>Note for the citizen<textarea name="remarks" rows="2" maxlength="500" placeholder="What was done, or why it was rejected"></textarea></label>
        <button class="btn primary" type="submit">Update status</button>
      </form>` : ''}
    </div>` : ''}`;
}

function showDetail(c) {
  $('#dlgBody').innerHTML = detailHtml(c, true);
  const dlg = $('#dlg');
  if (!dlg.open) dlg.showModal();

  const act = (form, path, build) => form && form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = form.querySelector('button'); btn.disabled = true;
    try {
      const updated = await api(`/complaints/${c.id}/${path}`, { method: 'PUT', body: build(new FormData(form)) });
      toast('Saved');
      showDetail(updated);
      refreshList();
    } catch (err) { toast(err.message, true); btn.disabled = false; }
  });
  act($('#assignForm'), 'assign', (fd) => ({ departmentId: Number(fd.get('departmentId')) }));
  act($('#statusForm'), 'status', (fd) => ({ status: fd.get('status'), remarks: fd.get('remarks') }));
}

async function openDetail(id) {
  try { showDetail(await api('/complaints/' + id)); } catch (err) { toast(err.message, true); }
}

/* ---------- views ---------- */
function render() {
  const nav = $('#nav');
  nav.innerHTML = session.user
    ? `<span class="who">${esc(session.user.name)}<small>${esc(session.user.role === 'OFFICER' ? 'Officer, ' + session.user.department : session.user.role === 'ADMIN' ? 'Admin' : 'Citizen')}</small></span>
       <button class="btn ghost" id="logout" type="button">Sign out</button>`
    : '';
  const out = $('#logout'); if (out) out.addEventListener('click', logout);

  const app = $('#app');
  if (!session.user) return renderGuest(app);
  if (session.user.role === 'CITIZEN') return renderCitizen(app);
  return renderStaff(app);
}

function renderGuest(app) {
  app.innerHTML = `
  <div class="guest">
    <section>
      <h1>Report a problem. Follow it until it is fixed.</h1>
      <p>Potholes, broken streetlights, water leaks, overflowing bins. Every complaint gets a tracking ID and goes straight to the department that can fix it.</p>
      <form id="trackForm" class="card track">
        <label for="tid">Already reported something? Check its status</label>
        <div class="row"><input id="tid" placeholder="CF-1A2B3C4D" autocomplete="off" required>
        <button class="btn primary" type="submit">Check status</button></div>
      </form>
      <div id="trackResult" aria-live="polite"></div>
    </section>
    <section class="card">
      <div class="tabs" role="tablist">
        <button role="tab" aria-selected="true" data-tab="login" type="button">Sign in</button>
        <button role="tab" aria-selected="false" data-tab="register" type="button">Create account</button>
      </div>
      <form id="loginForm" class="stack">
        <label>Email<input name="email" type="email" required autocomplete="username"></label>
        <label>Password<input name="password" type="password" required autocomplete="current-password"></label>
        <button class="btn primary" type="submit">Sign in</button>
      </form>
      <form id="registerForm" class="stack" hidden>
        <label>Full name<input name="name" required maxlength="80" autocomplete="name"></label>
        <label>Email<input name="email" type="email" required autocomplete="email"></label>
        <label>Password (at least 6 characters)<input name="password" type="password" required minlength="6" autocomplete="new-password"></label>
        <button class="btn primary" type="submit">Create account</button>
      </form>
      <div class="demo">
        <small>Demo accounts. Tap one to fill the sign-in form.</small>
        <div class="row">
          <button class="btn ghost" type="button" data-fill="citizen@cityfix.com|Citizen@123">Citizen</button>
          <button class="btn ghost" type="button" data-fill="officer.roads@cityfix.com|Officer@123">Roads officer</button>
          <button class="btn ghost" type="button" data-fill="admin@cityfix.com|Admin@123">Admin</button>
        </div>
      </div>
    </section>
  </div>`;

  $('#trackForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const box = $('#trackResult');
    try {
      const c = await api('/public/track/' + encodeURIComponent($('#tid').value.trim()));
      box.innerHTML = `<div class="card">${detailHtml(c, false).replace('id="dlgTitle"', '')}</div>`;
    } catch (err) { box.innerHTML = `<p class="empty">${esc(err.message)}</p>`; }
  });

  const tabs = app.querySelectorAll('[data-tab]');
  tabs.forEach((t) => t.addEventListener('click', () => {
    tabs.forEach((x) => x.setAttribute('aria-selected', String(x === t)));
    $('#loginForm').hidden = t.dataset.tab !== 'login';
    $('#registerForm').hidden = t.dataset.tab !== 'register';
  }));

  app.querySelectorAll('[data-fill]').forEach((b) => b.addEventListener('click', () => {
    const [email, pass] = b.dataset.fill.split('|');
    tabs[0].click();
    $('#loginForm [name=email]').value = email;
    $('#loginForm [name=password]').value = pass;
  }));

  const authForm = (selector, path, build) => $(selector).addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = e.target.querySelector('button[type=submit]'); btn.disabled = true;
    try {
      saveSession(await api(path, { method: 'POST', body: build(new FormData(e.target)) }));
      await start();
    } catch (err) { toast(err.message, true); btn.disabled = false; }
  });
  authForm('#loginForm', '/auth/login', (fd) => ({ email: fd.get('email'), password: fd.get('password') }));
  authForm('#registerForm', '/auth/register', (fd) => ({ name: fd.get('name'), email: fd.get('email'), password: fd.get('password') }));
}

function renderCitizen(app) {
  app.innerHTML = `
  <div class="split">
    <section class="card">
      <h2>Report a problem</h2>
      <form id="reportForm" class="stack">
        <label>What is the problem?<input name="title" maxlength="120" required placeholder="Deep pothole near the bus stop"></label>
        <label>Type<select name="category">${options(CATEGORIES)}</select></label>
        <label>Where is it?<input name="location" maxlength="200" required placeholder="Street, landmark and area"></label>
        <label>Details<textarea name="description" rows="4" maxlength="1000" required placeholder="How big is it? How long has it been there?"></textarea></label>
        <label>Photo (optional, up to 5 MB)<input type="file" name="photo" accept="image/jpeg,image/png,image/webp"></label>
        <button class="btn primary" type="submit">Submit complaint</button>
      </form>
    </section>
    <section>
      <h2>Your complaints</h2>
      <div id="myList" class="list" aria-live="polite"></div>
    </section>
  </div>`;

  async function loadMine() {
    try {
      const list = await api('/complaints/my');
      $('#myList').innerHTML = list.length ? list.map((c) => `
        <button class="item" type="button" data-id="${c.id}">
          <span class="top-row">${plate(c.trackingId)}${pill(c.status)}</span>
          <strong>${esc(c.title)}</strong>
          <small>${esc(CATEGORIES[c.category])}, ${esc(c.location)}</small>
        </button>`).join('') : '<p class="empty">Nothing reported yet. Your complaints will appear here.</p>';
      $('#myList').querySelectorAll('.item').forEach((b) => b.addEventListener('click', () => openDetail(b.dataset.id)));
    } catch (err) { toast(err.message, true); }
  }
  refreshList = loadMine;
  loadMine();

  const form = $('#reportForm');
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    const btn = form.querySelector('button[type=submit]'); btn.disabled = true;
    const fd = new FormData(form);
    try {
      const created = await api('/complaints', { method: 'POST', body: {
        title: fd.get('title'), category: fd.get('category'),
        location: fd.get('location'), description: fd.get('description') } });
      const photo = fd.get('photo');
      let photoNote = '';
      if (photo && photo.size > 0) {
        try {
          const up = new FormData(); up.append('file', photo);
          await api(`/complaints/${created.id}/photo`, { method: 'POST', form: up });
        } catch (err) { photoNote = ' The photo could not be uploaded: ' + err.message; }
      }
      form.reset();
      toast(`Complaint registered. Your tracking ID is ${created.trackingId}.${photoNote}`, !!photoNote);
      loadMine();
    } catch (err) { toast(err.message, true); }
    finally { btn.disabled = false; }
  });
}

function renderStaff(app) {
  const admin = session.user.role === 'ADMIN';
  app.innerHTML = `
    <h2>${admin ? 'All complaints' : 'Complaints for ' + esc(session.user.department)}</h2>
    ${admin ? '<section id="stats" class="stats" aria-label="Summary"></section>' : ''}
    <div class="card toolbar">
      <label>Status<select id="fStatus"><option value="">Any status</option>${options(STATUS_LABEL)}</select></label>
      ${admin ? `<label>Department<select id="fDept"><option value="">All departments</option>${session.departments.map((d) => `<option value="${d.id}">${esc(d.name)}</option>`).join('')}</select></label>` : ''}
    </div>
    <div class="table-wrap"><table>
      <thead><tr><th>Tracking ID</th><th>Problem</th><th>Type</th><th>Department</th><th>Status</th><th>Reported</th></tr></thead>
      <tbody id="rows"></tbody>
    </table></div>
    ${admin ? `<details class="card"><summary>Add a department officer</summary>
      <form id="officerForm" class="stack">
        <label>Full name<input name="name" required maxlength="80"></label>
        <label>Email<input name="email" type="email" required></label>
        <label>Temporary password<input name="password" type="text" required minlength="6"></label>
        <label>Department<select name="departmentId">${session.departments.map((d) => `<option value="${d.id}">${esc(d.name)}</option>`).join('')}</select></label>
        <button class="btn primary" type="submit">Create officer</button>
      </form></details>` : ''}`;

  async function load() {
    const params = new URLSearchParams();
    if ($('#fStatus').value) params.set('status', $('#fStatus').value);
    if (admin && $('#fDept').value) params.set('departmentId', $('#fDept').value);
    try {
      const list = await api('/complaints?' + params);
      $('#rows').innerHTML = list.length ? list.map((c) => `
        <tr><td>${plate(c.trackingId)}</td>
        <td><button class="link" type="button" data-id="${c.id}">${esc(c.title)}</button></td>
        <td>${esc(CATEGORIES[c.category])}</td><td>${c.department ? esc(c.department) : '<small>Not assigned</small>'}</td>
        <td>${pill(c.status)}</td><td>${fmt(c.createdAt)}</td></tr>`).join('')
        : '<tr><td colspan="6" class="empty">No complaints match these filters.</td></tr>';
      $('#rows').querySelectorAll('.link').forEach((b) => b.addEventListener('click', () => openDetail(b.dataset.id)));
    } catch (err) { toast(err.message, true); }
    if (admin) loadStats();
  }

  async function loadStats() {
    try {
      const s = await api('/admin/stats');
      $('#stats').innerHTML = [['Total', s.total], ...Object.entries(s.byStatus).map(([k, v]) => [STATUS_LABEL[k], v]),
        ['Avg. hours to resolve', s.avgResolutionHours]].map(([label, n]) =>
        `<div class="stat"><b>${n}</b><small>${esc(label)}</small></div>`).join('');
    } catch (err) { /* stats are optional, the table still works */ }
  }

  refreshList = load;
  $('#fStatus').addEventListener('change', load);
  if (admin) {
    $('#fDept').addEventListener('change', load);
    $('#officerForm').addEventListener('submit', async (e) => {
      e.preventDefault();
      const fd = new FormData(e.target);
      try {
        await api('/admin/officers', { method: 'POST', body: {
          name: fd.get('name'), email: fd.get('email'), password: fd.get('password'), departmentId: Number(fd.get('departmentId')) } });
        e.target.reset();
        toast('Officer account created');
      } catch (err) { toast(err.message, true); }
    });
  }
  load();
}

/* ---------- start ---------- */
$('#dlgClose').addEventListener('click', () => $('#dlg').close());
$('#dlg').addEventListener('click', (e) => { if (e.target === $('#dlg')) $('#dlg').close(); });

async function start() {
  if (session.user) {
    try { session.departments = await api('/departments'); } catch (err) { /* logout() already handled a 401 */ }
  }
  render();
}
start();
