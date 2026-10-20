/* MiCuota - panel del administrador de la plataforma (admin.html). Se apoya en app.js (window.MC). */
(function () {
  "use strict";

  const { api, session, humanError, toast, busy, esc, money, icons, shortDate, relativeDays } = window.MC;
  const view = document.getElementById("view");
  const state = { me: null, overview: null, tenants: null, filter: "" };

  document.querySelectorAll("[data-icon]").forEach((n) => { n.innerHTML = icons[n.dataset.icon] || ""; });

  /* ---------------- Utilidades ---------------- */
  const percent = (bps) => `${(Number(bps || 0) / 100).toLocaleString("es-UY", { maximumFractionDigits: 2 })}%`;
  const toBps = (value) => Math.round(Number(String(value).replace(",", ".")) * 100);
  const bpsInput = (bps) => String(Number(bps || 0) / 100);

  function setView(html, title) {
    view.innerHTML = html;
    document.title = title ? `${title} · MiCuota Admin` : "MiCuota Admin";
    view.focus({ preventScroll: true });
  }

  function mpPill(connection) {
    if (connection === "OAUTH") return `<span class="pill pill-ok">Mercado Pago conectado</span>`;
    if (connection === "MANUAL") return `<span class="pill pill-warn">Token manual · sin comisión</span>`;
    return `<span class="pill pill-late">Sin Mercado Pago</span>`;
  }

  function markNav(name) {
    document.querySelectorAll("[data-nav]").forEach((a) => {
      if (a.dataset.nav === name) a.setAttribute("aria-current", "page");
      else a.removeAttribute("aria-current");
    });
  }

  function loadingView(text) {
    setView(`<div class="card empty"><span class="spinner" aria-hidden="true"></span><p class="muted">${esc(text)}</p></div>`);
  }

  function errorView(error, retryAction) {
    setView(`<div class="card empty"><div class="empty-icon">${icons.alert}</div><h1 style="font-size:1.5rem">No pudimos cargar esto</h1>
      <p class="muted">${esc(humanError(error))}</p><button class="btn btn-primary btn-lg" type="button" data-action="${retryAction}">Intentar de nuevo</button></div>`);
  }

  /* ---------------- Login ---------------- */
  function renderLogin(message = "") {
    document.body.classList.add("is-logged-out");
    setView(`<div class="narrow" style="max-width:440px;padding-top:6vh">
        <div class="center" style="margin-bottom:18px"><img src="/assets/micuota-mate-mark.png" alt="" width="56" height="56" /></div>
        <div class="card">
          <h1 style="font-size:1.6rem">Panel de administración</h1>
          <p class="muted">Acceso solo para el administrador de MiCuota.</p>
          <form id="login-form" novalidate>
            <div class="field"><label for="l-email">Email</label><input class="input" id="l-email" name="email" type="email" autocomplete="username" required /></div>
            <div class="field"><label for="l-pass">Contraseña</label><input class="input" id="l-pass" name="password" type="password" autocomplete="current-password" required /></div>
            <p class="field-error" id="login-error" role="alert" ${message ? "" : "hidden"}>${esc(message)}</p>
            <button class="btn btn-primary btn-lg btn-block" type="submit" id="login-submit" style="margin-top:8px">Entrar</button>
          </form>
        </div>
      </div>`, "Entrar");
    const form = document.getElementById("login-form");
    document.getElementById("l-email").focus();
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("login-error");
      const email = form.email.value.trim();
      const password = form.password.value;
      if (!email || !password) { err.textContent = "Completá email y contraseña."; err.hidden = false; return; }
      const btn = document.getElementById("login-submit");
      busy(btn, true, "Entrando…");
      try {
        const result = await api("/api/auth/admin-login", { method: "POST", auth: false, body: { email, password } });
        session.save(result);
        await boot();
      } catch (error) {
        err.textContent = /demasiados intentos/i.test(error.message || "") ? error.message : humanError(error);
        err.hidden = false;
      } finally { busy(btn, false); }
    });
  }

  function logout() {
    try { if (window.MicuotaSessionTelemetry) window.MicuotaSessionTelemetry.logout(); } catch (_e) { /* no bloquea */ }
    session.clear();
    state.me = null; state.overview = null; state.tenants = null;
    history.replaceState(null, "", location.pathname);
    renderLogin();
  }

  /* ---------------- Datos ---------------- */
  async function loadOverview() { state.overview = await api("/api/admin/overview"); }
  async function loadTenants() { state.tenants = await api("/api/admin/tenants"); }

  /* ---------------- Resumen ---------------- */
  async function renderSummary() {
    if (!state.overview) {
      loadingView("Cargando el resumen…");
      try { await loadOverview(); } catch (error) { return errorView(error, "reload-summary"); }
    }
    const o = state.overview;
    const totals = o.totals.length
      ? o.totals.map((t) => `<li>${esc(money(t.paidAmount, t.currency))}</li>`).join("")
      : `<li>${esc(money(0))}</li>`;
    const commission = o.totals.length
      ? o.totals.map((t) => `<li>${esc(money(t.commissionCollected, t.currency))}</li>`).join("")
      : `<li>${esc(money(0))}</li>`;
    const maxMonth = Math.max(1, ...o.months.map((m) => Math.max(m.newAccounts, m.paidPayments)));
    const monthLabel = (period) => new Intl.DateTimeFormat("es-UY", { month: "short", year: "2-digit" }).format(new Date(`${period}-01T12:00:00`));
    const bars = (key, label) => o.months.map((m) => `<div class="bar-row"><span class="muted">${esc(monthLabel(m.period))}</span>
        <div class="bar-track" aria-hidden="true"><span style="width:${Math.round((m[key] / maxMonth) * 100)}%"></span></div>
        <strong>${m[key]}<span class="sr-only"> ${label}</span></strong></div>`).join("");

    let html = `<div class="view-head row-between"><div><h1>Resumen</h1><p>Actualizado ${esc(new Date(o.generatedAt).toLocaleTimeString("es-UY", { hour: "2-digit", minute: "2-digit" }))}</p></div>
        <button class="btn btn-sm" type="button" data-action="reload-summary">${icons.refresh} Actualizar</button></div>`;
    if (o.professionalsWithManualToken > 0) {
      html += `<div class="notice notice-warn" style="margin-bottom:16px">${icons.alert}<p><strong>${o.professionalsWithManualToken} ${o.professionalsWithManualToken === 1 ? "profesional cobra" : "profesionales cobran"} sin comisión</strong> porque ${o.professionalsWithManualToken === 1 ? "pegó" : "pegaron"} su Access Token a mano. Cuando abran la sección Mercado Pago de su app se les pide reconectar con un clic.</p></div>`;
    }
    html += `<div class="kpis">
        <div class="stat stat-main"><p class="label">Comisión cobrada</p><ul class="money-lines value" style="font-size:1.6rem">${commission}</ul><p class="sub">Vía marketplace de Mercado Pago</p></div>
        <div class="stat"><p class="label">Cobrado por los profesionales</p><ul class="money-lines value" style="font-size:1.6rem">${totals}</ul><p class="sub muted">${o.paymentsPaid} ${o.paymentsPaid === 1 ? "cobro pagado" : "cobros pagados"}</p></div>
        <div class="stat"><p class="label">Cuentas</p><p class="value">${o.accounts}</p><p class="sub muted">${o.professionals} profesionales · ${o.students} alumnos</p></div>
        <div class="stat"><p class="label">Conectados a Mercado Pago</p><p class="value">${o.professionalsConnectedViaOAuth}<span class="muted" style="font-size:1.1rem"> / ${o.professionals}</span></p><p class="sub muted">Con comisión activa</p></div>
      </div>
      <h2 class="section-title">Cobros</h2>
      <div class="card"><dl class="facts">
        <dt>Creados</dt><dd>${o.paymentsCreated}</dd>
        <dt>Pagados</dt><dd>${o.paymentsPaid}</dd>
        <dt>· por Mercado Pago (solos)</dt><dd>${o.paymentsPaid - o.paymentsPaidByHand}</dd>
        <dt>· marcados a mano</dt><dd>${o.paymentsPaidByHand}</dd>
        <dt>Sin pagar o en proceso</dt><dd>${o.paymentsPending}</dd>
        ${o.totals.map((t) => `<dt>Pagado en ${esc(t.currency)}</dt><dd>${esc(money(t.paidAmount, t.currency))} · comisión ${esc(money(t.commissionCollected, t.currency))}</dd>`).join("")}
        <dt>Leads de la landing</dt><dd>${o.leads}</dd>
      </dl></div>
      <h2 class="section-title">Últimos 6 meses</h2>
      <div class="card">
        <p class="label" style="margin:0 0 10px">Cuentas nuevas</p><div class="bars">${bars("newAccounts", "cuentas nuevas")}</div>
        <p class="label" style="margin:20px 0 10px">Cobros pagados</p><div class="bars">${bars("paidPayments", "cobros pagados")}</div>
      </div>
      <h2 class="section-title">Cuentas nuevas <a href="#cuentas">Ver todas</a></h2>`;
    html += o.recentAccounts.length
      ? `<ul class="rows">${o.recentAccounts.map((a) => `<li class="row-item"><div class="row-text">
            <p class="row-name">${esc(a.contactName || a.tenantName)}</p>
            <p class="row-sub">${esc(a.contactEmail || a.tenantSlug)} · ${esc(relativeDays(a.createdAt) || "")}</p></div>
            <div class="row-right">${mpPill(a.mercadoPagoConnection)}</div></li>`).join("")}</ul>`
      : `<div class="card empty"><p class="muted">Todavía no hay cuentas.</p></div>`;
    setView(html, "Resumen");
  }

  /* ---------------- Cuentas y comisiones ---------------- */
  async function renderAccounts() {
    if (!state.tenants) {
      loadingView("Cargando las cuentas…");
      try { await loadTenants(); } catch (error) { return errorView(error, "reload-accounts"); }
    }
    const q = state.filter.trim().toLowerCase();
    const list = state.tenants.filter((t) => !q || [t.tenantName, t.tenantSlug, t.contactName, t.contactEmail].some((v) => (v || "").toLowerCase().includes(q)));
    let html = `<div class="view-head row-between"><div><h1>Cuentas y comisiones</h1><p>${state.tenants.length} ${state.tenants.length === 1 ? "cuenta" : "cuentas"}. La comisión se cobra solo en pagos únicos de profesionales conectados a Mercado Pago; los cambios aplican a los cobros nuevos.</p></div>
        <button class="btn btn-sm" type="button" data-action="reload-accounts">${icons.refresh} Actualizar</button></div>
      <div class="field search"><label class="sr-only" for="acc-search">Buscar cuenta</label><input class="input" id="acc-search" type="search" placeholder="Buscar por nombre, email o código" value="${esc(state.filter)}" /></div>`;
    if (!list.length) {
      html += `<div class="card empty"><p class="muted">${state.tenants.length ? "No hay cuentas que coincidan." : "Todavía no hay cuentas."}</p></div>`;
    }
    html += list.map((t) => `<article class="card account-card">
        <div class="row-between"><div style="min-width:0"><h2 class="card-title" style="margin:0">${esc(t.contactName || t.tenantName)}</h2>
          <p class="muted small" style="margin:2px 0 0;overflow-wrap:anywhere">${esc(t.contactEmail || "sin contacto")} · <code>${esc(t.tenantSlug)}</code></p></div>${mpPill(t.mercadoPagoConnection)}</div>
        <dl class="facts">
          <dt>Alta</dt><dd>${esc(t.createdAt ? shortDate(t.createdAt) : "—")}</dd>
          <dt>Profesionales conectados</dt><dd>${t.professionalsConnectedViaOAuth} / ${t.professionals}</dd>
          <dt>Cobros con comisión</dt><dd>${t.paymentsWithCommission}</dd>
          <dt>Comisión cobrada</dt><dd>${esc(Number(t.commissionCollected || 0).toLocaleString("es-UY", { maximumFractionDigits: 2 }))} <span class="muted small">(suma de todas las monedas)</span></dd>
          <dt>Comisión total por cobro</dt><dd>${percent(t.takeRateBps + (t.recoveryAutomationEnabled ? t.advancedDunningFeeBps : 0))}</dd>
        </dl>
        <form class="fee-form" data-fee-form="${t.tenantId}" novalidate>
          <div class="field"><label for="fee-${t.tenantId}">Comisión %</label><input class="input" id="fee-${t.tenantId}" name="takeRate" type="number" inputmode="decimal" min="0" max="50" step="0.01" value="${bpsInput(t.takeRateBps)}" /></div>
          <div class="field"><label for="dun-${t.tenantId}">Recupero % ${t.recoveryAutomationEnabled ? "" : '<span class="muted">(inactivo)</span>'}</label><input class="input" id="dun-${t.tenantId}" name="dunning" type="number" inputmode="decimal" min="0" max="25" step="0.01" value="${bpsInput(t.advancedDunningFeeBps)}" /></div>
          <button class="btn btn-primary" type="submit">Guardar</button>
        </form>
      </article>`).join("");
    setView(html, "Cuentas");
    const search = document.getElementById("acc-search");
    search.addEventListener("input", () => {
      state.filter = search.value;
      const pos = search.selectionStart;
      renderAccounts().then(() => { const s = document.getElementById("acc-search"); s.focus(); s.setSelectionRange(pos, pos); });
    });
  }

  async function saveFee(form) {
    const tenant = state.tenants.find((t) => String(t.tenantId) === form.dataset.feeForm);
    const takeRateBps = toBps(form.takeRate.value);
    const advancedDunningFeeBps = toBps(form.dunning.value);
    if (!Number.isFinite(takeRateBps) || !Number.isFinite(advancedDunningFeeBps) || takeRateBps < 0 || advancedDunningFeeBps < 0) {
      return toast("Revisá los porcentajes.", true);
    }
    const btn = form.querySelector("button");
    busy(btn, true, "Guardando…");
    try {
      const updated = await api(`/api/admin/tenants/${tenant.tenantId}/billing`, {
        method: "PUT",
        body: { planCode: tenant.planCode, takeRateBps, advancedDunningFeeBps }
      });
      state.tenants = state.tenants.map((t) => (t.tenantId === updated.tenantId ? updated : t));
      state.overview = null;
      toast(`Comisión de ${updated.contactName || updated.tenantName} guardada`);
      renderAccounts();
    } catch (error) {
      toast(humanError(error), true);
      busy(btn, false);
    }
  }

  /* ---------------- Mantenimiento ---------------- */
  async function renderMaintenance() {
    if (!state.tenants) {
      loadingView("Cargando las cuentas…");
      try { await loadTenants(); } catch (error) { return errorView(error, "reload-maintenance"); }
    }
    const html = `<div class="view-head"><h1>Empezar limpio</h1><p>Borra las cuentas que no marques, con sus profesionales, alumnos, cursos y cobros. Tu acceso de administrador no se toca.</p></div>
      <form id="reset-form" class="card" novalidate>
        <fieldset class="choice-group"><legend>Cuentas que se conservan</legend>
          <div class="keep-list">${state.tenants.map((t) => `<label class="choice"><input type="checkbox" name="keep" value="${esc(t.tenantSlug)}" ${t.mercadoPagoConnection === "OAUTH" ? "checked" : ""} />
            <span class="choice-box"><strong>${esc(t.contactName || t.tenantName)}</strong><span>${esc(t.contactEmail || "")} · ${esc(t.tenantSlug)} · ${t.mercadoPagoConnection === "OAUTH" ? "Mercado Pago conectado" : t.mercadoPagoConnection === "MANUAL" ? "token manual" : "sin Mercado Pago"}</span></span></label>`).join("") || '<p class="muted">No hay cuentas.</p>'}</div>
        </fieldset>
        <label class="choice" style="margin-bottom:16px"><input type="checkbox" name="deleteAllPayments" checked />
          <span class="choice-box"><strong>Borrar también todos los cobros</strong><span>Incluye los de las cuentas que se conservan. El profesional y su conexión con Mercado Pago quedan.</span></span></label>
        <div class="notice notice-late" style="margin-bottom:16px">${icons.alert}<p><strong>No se puede deshacer.</strong></p></div>
        <div class="field"><label for="reset-confirm">Para confirmar escribí <strong>BORRAR</strong></label><input class="input" id="reset-confirm" autocomplete="off" autocapitalize="characters" /></div>
        <p class="field-error" id="reset-error" role="alert" hidden></p>
        <button class="btn btn-danger btn-lg btn-block" type="submit" id="reset-submit">Borrar datos</button>
      </form>`;
    setView(html, "Mantenimiento");
    document.getElementById("reset-form").addEventListener("submit", runReset);
  }

  async function runReset(e) {
    e.preventDefault();
    const form = e.target;
    const err = document.getElementById("reset-error");
    err.hidden = true;
    const keep = [...form.querySelectorAll("input[name=keep]:checked")].map((i) => i.value);
    if (document.getElementById("reset-confirm").value.trim().toUpperCase() !== "BORRAR") {
      err.textContent = "Escribí BORRAR para confirmar."; err.hidden = false; return;
    }
    const deleted = state.tenants.length - keep.length;
    if (!window.confirm(`Se van a borrar ${deleted} ${deleted === 1 ? "cuenta" : "cuentas"}${form.deleteAllPayments.checked ? " y todos los cobros" : ""}. ¿Seguimos?`)) return;
    const btn = document.getElementById("reset-submit");
    busy(btn, true, "Borrando…");
    try {
      const result = await api("/api/admin/maintenance/reset", {
        method: "POST",
        body: { keepTenantSlugs: keep, deleteAllPayments: form.deleteAllPayments.checked }
      });
      state.overview = null; state.tenants = null;
      toast(`Listo: ${result.deletedTenants} cuentas, ${result.deletedUsers} usuarios y ${result.deletedPayments} cobros borrados`);
      location.hash = "#resumen";
    } catch (error) {
      err.textContent = humanError(error); err.hidden = false;
      busy(btn, false);
    }
  }

  /* ---------------- Ruteo y acciones ---------------- */
  function route() {
    if (!state.me) return;
    const name = (location.hash || "#resumen").slice(1).split("/")[0];
    const routes = { resumen: renderSummary, cuentas: renderAccounts, mantenimiento: renderMaintenance };
    const target = routes[name] ? name : "resumen";
    routes[target]();
    markNav(target);
  }

  document.addEventListener("click", (e) => {
    const el = e.target.closest("[data-action]");
    if (!el) return;
    switch (el.dataset.action) {
      case "logout": logout(); break;
      case "reload-summary": state.overview = null; renderSummary(); break;
      case "reload-accounts": state.tenants = null; renderAccounts(); break;
      case "reload-maintenance": state.tenants = null; renderMaintenance(); break;
      default: break;
    }
  });
  document.addEventListener("submit", (e) => {
    const form = e.target.closest("[data-fee-form]");
    if (!form) return;
    e.preventDefault();
    saveFee(form);
  });
  window.addEventListener("hashchange", route);

  async function boot() {
    session.adoptTokenFromUrl();
    if (!session.token()) return renderLogin();
    loadingView("Entrando…");
    try {
      const me = await api("/api/auth/me");
      if (me.role !== "ADMIN") {
        session.clear();
        return renderLogin("Esa cuenta no es la del administrador de MiCuota.");
      }
      state.me = me;
      document.body.classList.remove("is-logged-out");
      document.getElementById("sidebar-user").textContent = me.email || me.fullName || "";
      route();
    } catch (error) {
      session.clear();
      renderLogin(error.network ? humanError(error) : "");
    }
  }

  boot();
})();
