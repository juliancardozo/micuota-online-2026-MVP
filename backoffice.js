/* MiCuota - backoffice de la academia (backoffice.html), para el rol TENANT_ADMIN.
   Misma base visual y de interaccion que la app del profesor; se apoya en app.js (window.MC). */
(function () {
  "use strict";

  const MC = window.MC;
  const { store, session, api, humanError, money, esc, initials, firstName, relativeDays, shortDate, monthName,
    statusOf, flowLabel, payLink, waLink, copy, toast, busy, icons } = MC;

  session.adoptTokenFromUrl();
  if (!session.token()) { location.replace("/cuenta.html#entrar"); return; }

  const view = document.getElementById("view");
  const state = {
    me: null,
    payments: [],
    teachers: [],
    students: [],
    phones: {},
    filter: "all",
    teacherFilter: "",
    query: "",
    loaded: false
  };
  const slugKey = () => (state.me && state.me.tenantSlug) || session.slug() || "default";

  document.querySelectorAll("[data-icon]").forEach((n) => { n.innerHTML = icons[n.dataset.icon] || ""; });

  /* ---------------- Datos ---------------- */
  async function loadAll() {
    const me = await api("/api/auth/me").catch((err) => { err.fromSession = true; throw err; });
    if (me.role !== "TENANT_ADMIN") {
      location.replace(session.homeFor(me.role));
      return false;
    }
    state.me = me;
    const [payments, users] = await Promise.all([api("/api/backoffice/payments"), api("/api/backoffice/users")]);
    state.payments = sortByDate(payments || []);
    state.teachers = (users || []).filter((u) => u.role === "TEACHER").sort(byName);
    state.students = (users || []).filter((u) => u.role === "STUDENT").sort(byName);
    // Los telefonos se guardan solo en este navegador (igual que en la app del profesor).
    state.phones = store.getJson(`micuota.phones.${slugKey()}`, {});
    state.loaded = true;
    paintChrome();
    return true;
  }

  async function refresh({ silent = true } = {}) {
    try {
      const [payments, users] = await Promise.all([
        api("/api/backoffice/payments", { slowAfter: silent ? 60000 : 3500 }),
        api("/api/backoffice/users", { slowAfter: silent ? 60000 : 3500 })
      ]);
      const before = new Map(state.payments.map((p) => [p.id, p.status]));
      state.payments = sortByDate(payments || []);
      state.teachers = (users || []).filter((u) => u.role === "TEACHER").sort(byName);
      state.students = (users || []).filter((u) => u.role === "STUDENT").sort(byName);
      const newlyPaid = state.payments.filter((p) => p.status === "SUCCESS" && before.has(p.id) && before.get(p.id) !== "SUCCESS");
      newlyPaid.forEach((p) => toast(`${studentName(p)} pagó ${money(p.amount, p.currency)}`));
      if (newlyPaid.length || !silent) route();
    } catch (error) {
      if (!silent) toast(humanError(error), true);
    }
  }

  const sortByDate = (list) => list.slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
  const byName = (a, b) => a.fullName.localeCompare(b.fullName, "es");
  const savePhones = () => store.setJson(`micuota.phones.${slugKey()}`, state.phones);

  const studentById = (id) => state.students.find((s) => String(s.id) === String(id));
  const teacherById = (id) => state.teachers.find((t) => String(t.id) === String(id));
  const teacherByProfile = (profileId) => state.teachers.find((t) => String(t.teacherProfileId) === String(profileId));
  const paymentById = (id) => state.payments.find((p) => String(p.id) === String(id));
  const studentName = (p) => { const s = p && p.studentUserId ? studentById(p.studentUserId) : null; return s ? s.fullName : "Sin alumno asignado"; };
  const teacherName = (p) => { const t = teacherByProfile(p && p.teacherId); return t ? t.fullName : "Profe"; };
  const studentPhone = (p) => (p && p.studentUserId ? state.phones[p.studentUserId] || "" : "");
  const canCharge = (t) => t && (t.mercadoPagoConnection === "OAUTH" || t.mercadoPagoConnection === "MANUAL");
  const paymentsOfTeacher = (t) => state.payments.filter((p) => String(p.teacherId) === String(t.teacherProfileId));
  const paymentsOfStudent = (s) => state.payments.filter((p) => String(p.studentUserId) === String(s.id));

  /* ---------------- Formato ---------------- */
  // Suma por moneda: nunca se mezclan pesos con dolares.
  function sumByCurrency(list, pick = (p) => p.amount) {
    const totals = {};
    list.forEach((p) => { const c = (p.currency || "UYU").toUpperCase(); totals[c] = (totals[c] || 0) + Number(pick(p) || 0); });
    return totals;
  }
  function moneyLines(totals) {
    const entries = Object.entries(totals);
    if (!entries.length) return esc(money(0));
    return entries.map(([c, v]) => esc(money(v, c))).join("<br>");
  }

  function mpPill(t) {
    if (t.mercadoPagoConnection === "OAUTH") return `<span class="pill pill-ok">Mercado Pago conectado</span>`;
    if (t.mercadoPagoConnection === "MANUAL") return `<span class="pill pill-warn">Tiene que reconectar</span>`;
    return `<span class="pill pill-late">Sin Mercado Pago</span>`;
  }

  /* ---------------- WhatsApp ---------------- */
  function messageFor(p, kind) {
    const who = firstName(studentName(p));
    const hi = who && p.studentUserId ? `¡Hola ${who}!` : "¡Hola!";
    const amount = money(p.amount, p.currency);
    const link = payLink(p.id);
    const from = state.me ? state.me.tenantName || firstName(state.me.fullName) : "";
    const concept = p.description || "el pago";
    if (kind === "reminder") return `${hi} Te recordamos que quedó pendiente ${concept} (${amount}). Lo podés pagar acá, es seguro y con Mercado Pago:\n${link}\n¡Gracias! ${from}`.trim();
    if (p.flowType === "SUBSCRIPTION") return `${hi} Te pasamos el link para activar tu cuota mensual de ${concept} (${amount} por mes). La autorizás una vez y Mercado Pago la cobra sola cada mes:\n${link}\n¡Gracias! ${from}`.trim();
    return `${hi} Te pasamos el link para pagar ${concept} (${amount}). Es seguro, con Mercado Pago:\n${link}\n¡Gracias! ${from}`.trim();
  }
  const reminderKind = (p) => (Date.now() - new Date(p.createdAt).getTime() > 86400000 ? "reminder" : "first");
  function waButton(p, { label, cls = "btn btn-wa", kind } = {}) {
    const k = kind || reminderKind(p);
    return `<a class="${cls}" href="${esc(waLink(messageFor(p, k), studentPhone(p)))}" target="_blank" rel="noopener">${icons.whatsapp}<span>${esc(label || (k === "reminder" ? "Recordar" : "Enviar"))}</span></a>`;
  }

  function teacherInviteMessage(t) {
    const academy = state.me.tenantName || "la academia";
    return `¡Hola ${firstName(t.fullName)}! Te sumé a ${academy} en MiCuota para cobrar las clases.\n1. Entrá a ${location.origin}/entrar con tu email (${t.email}); te llega un código, sin contraseña.\n2. Tocá "Conectar con Mercado Pago" y autorizá con tu cuenta: la plata te llega directo a vos.\n¡Gracias!`;
  }

  /* ---------------- Chrome y ruteo ---------------- */
  function paintChrome() {
    document.getElementById("topbar-avatar").textContent = initials(state.me.fullName);
    document.getElementById("sidebar-user").textContent = `${state.me.tenantName || ""} · ${state.me.email}`;
  }
  function markNav(name) {
    const map = { cobro: "cobros", listo: "nuevo", alumno: "alumnos", profe: "equipo" };
    const active = map[name] || name;
    document.querySelectorAll("[data-nav]").forEach((a) => {
      if (a.dataset.nav === active) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
  }
  function parseHash() {
    const parts = (location.hash.replace(/^#\/?/, "") || "inicio").split("/");
    return { name: parts[0], parts };
  }
  function route() {
    if (!state.loaded) return;
    const { name, parts } = parseHash();
    const routes = {
      inicio: renderHome,
      cobros: renderPayments,
      cobro: () => renderPayment(parts[1]),
      nuevo: () => renderNew({ studentId: parts[1] === "alumno" ? parts[2] : null, teacherId: parts[1] === "profe" ? parts[2] : null }),
      listo: () => renderCreated(parts[1]),
      alumnos: renderStudents,
      alumno: () => (parts[1] === "nuevo" ? renderNewPerson("STUDENT") : renderStudent(parts[1])),
      equipo: renderTeam,
      profe: () => (parts[1] === "nuevo" ? renderNewPerson("TEACHER") : renderTeacher(parts[1])),
      cuenta: renderAccount
    };
    (routes[name] || renderHome)();
    markNav(routes[name] ? name : "inicio");
  }
  function setView(html, title) {
    view.innerHTML = html;
    document.title = `${title} | MiCuota`;
    window.scrollTo(0, 0);
    const h = view.querySelector("h1");
    if (h) { h.setAttribute("tabindex", "-1"); h.focus({ preventScroll: true }); }
  }

  /* ---------------- Componentes ---------------- */
  function paymentRow(p, { actions = true, showTeacher = state.teachers.length > 1 } = {}) {
    const st = statusOf(p);
    const person = studentName(p);
    const when = st.key === "paid" ? `Pagó ${relativeDays(p.updatedAt || p.createdAt)}` : st.key === "late" ? `Venció ${relativeDays(p.dueAt)}` : `Creado ${relativeDays(p.createdAt)}`;
    return `<li class="row-item">
      <a class="row-link" href="#cobro/${p.id}">
        <span class="avatar" aria-hidden="true">${esc(initials(person))}</span>
        <span class="row-text"><p class="row-name">${esc(person)}</p><p class="row-sub">${esc(p.description)}${showTeacher ? ` · ${esc(teacherName(p))}` : ""} · ${esc(when)}</p></span>
        <span class="row-right"><span class="amount">${esc(money(p.amount, p.currency))}</span><span class="pill ${st.pill}">${esc(st.label)}</span></span>
      </a>
      ${actions && st.owes ? `<span class="row-actions">${waButton(p, { cls: "btn btn-wa btn-sm" })}</span>` : ""}
    </li>`;
  }
  function emptyState(icon, title, text, href, cta) {
    return `<div class="card empty"><div class="empty-icon">${icons[icon]}</div><h2>${esc(title)}</h2><p class="muted">${esc(text)}</p>${href ? `<a class="btn btn-primary btn-lg" href="${href}">${esc(cta)}</a>` : ""}</div>`;
  }

  /* ---------------- Inicio ---------------- */
  function onboardingSteps() {
    const anyConnected = state.teachers.some(canCharge);
    return [
      { done: true, title: "Creaste la cuenta de la academia", copy: "¡Bienvenida/o a MiCuota!" },
      { done: state.teachers.length > 0, title: "Sumá a tu primer profe", copy: "Le llega un email para entrar, sin contraseña.", href: "#profe/nuevo", cta: "Sumar un profe" },
      { done: anyConnected, title: "Que conecte su Mercado Pago", copy: "Así cada pago le llega directo a su cuenta.", href: "#equipo", cta: "Ver el equipo" },
      { done: state.payments.length > 0, title: "Creá el primer cobro", copy: "A quién, por qué y cuánto. Nada más.", href: "#nuevo", cta: "Crear un cobro" }
    ];
  }

  function renderHome() {
    const steps = onboardingSteps();
    const done = steps.filter((s) => s.done).length;
    const next = steps.find((s) => !s.done);
    const hour = new Date().getHours();
    const greet = hour < 12 ? "Buen día" : hour < 20 ? "Buenas tardes" : "Buenas noches";
    const owes = state.payments.filter((p) => statusOf(p).owes);
    const late = owes.filter((p) => statusOf(p).key === "late");
    const now = new Date();
    const paidThisMonth = state.payments.filter((p) => {
      if (p.status !== "SUCCESS") return false;
      const d = new Date(p.updatedAt || p.createdAt);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });
    const notConnected = state.teachers.filter((t) => !canCharge(t));
    const reconnect = state.teachers.filter((t) => t.mercadoPagoConnection === "MANUAL");

    let html = `<div class="view-head"><h1 class="hello">${greet}, ${esc(firstName(state.me.fullName))}</h1><p>${esc(state.me.tenantName || "")}</p></div>`;

    if (next) {
      html += `<section class="onboard" aria-labelledby="ob-title">
        <h2 id="ob-title">Poné en marcha tu academia</h2>
        <p class="muted" style="margin:0">Llevás ${done} de ${steps.length}. Ahora: <strong>${esc(next.title.toLowerCase())}</strong>.</p>
        <div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="${steps.length}" aria-valuenow="${done}" aria-label="Progreso"><span style="width:${(done / steps.length) * 100}%"></span></div>
        <ol class="steps">${steps.map((s, i) => `<li class="step ${s.done ? "is-done" : ""} ${s === next ? "is-next" : ""}">
          <span class="dot" aria-hidden="true">${s.done ? icons.check : i + 1}</span>
          <span class="grow"><p class="step-title">${esc(s.title)}<span class="sr-only">${s.done ? " (hecho)" : " (pendiente)"}</span></p><p class="step-copy">${esc(s.copy)}</p></span>
          ${s === next && s.href ? `<a class="btn btn-primary btn-sm" href="${s.href}">${esc(s.cta)}</a>` : ""}</li>`).join("")}</ol>
      </section>`;
    }

    if (notConnected.length && state.teachers.length) {
      const names = notConnected.map((t) => firstName(t.fullName)).join(", ");
      html += `<div class="banner-mp" role="note" style="margin-top:18px">${icons.alert.replace("<svg", '<svg width="28" height="28" style="flex:none;color:var(--warn)"')}
        <p class="grow"><strong>${esc(names)} ${notConnected.length === 1 ? "todavía no puede" : "todavía no pueden"} cobrar.</strong> ${reconnect.length ? "Tienen que conectar Mercado Pago con el botón nuevo." : "Falta que conecten su Mercado Pago."}</p>
        <a class="btn btn-primary" href="${notConnected.length === 1 ? `#profe/${notConnected[0].id}` : "#equipo"}">Ver cómo</a></div>`;
    }

    html += `<div class="stats" style="margin-top:18px">
        <div class="stat stat-main"><p class="label">Cobrado en ${esc(monthName(now))}</p><p class="value">${moneyLines(sumByCurrency(paidThisMonth))}</p><p class="sub">${paidThisMonth.length} ${paidThisMonth.length === 1 ? "pago" : "pagos"}</p></div>
        <a class="stat stat-owed ${owes.length ? "" : "is-zero"}" href="#cobros" data-action="filter" data-filter="owes" style="text-decoration:none;color:inherit">
          <p class="label">Les deben</p><p class="value">${moneyLines(sumByCurrency(owes))}</p>
          <p class="sub muted">${owes.length ? `${owes.length} ${owes.length === 1 ? "cobro" : "cobros"} sin pagar${late.length ? ` · ${late.length} vencidos` : ""}` : "Nadie, ¡todo al día!"}</p></a>
      </div>`;

    if (late.length) {
      html += `<h2 class="section-title">Para recordar hoy <a href="#cobros" data-action="filter" data-filter="late">Ver todos</a></h2>
        <ul class="rows">${late.slice(0, 5).map((p) => paymentRow(p)).join("")}</ul>`;
    }
    const recent = state.payments.slice(0, 5);
    if (recent.length) {
      html += `<h2 class="section-title">Últimos cobros <a href="#cobros" data-action="filter" data-filter="all">Ver todos</a></h2>
        <ul class="rows">${recent.map((p) => paymentRow(p)).join("")}</ul>`;
    } else if (!next || next.href !== "#profe/nuevo") {
      html += `<div style="margin-top:18px">${emptyState("wallet", "Todavía no hay cobros", "Cuando creen el primero, acá vas a ver quién pagó y quién no.", "#nuevo", "Crear un cobro")}</div>`;
    }
    setView(html, "Inicio");
  }

  /* ---------------- Cobros ---------------- */
  function renderPayments() {
    const filters = [["all", "Todos"], ["owes", "Sin pagar"], ["late", "Vencidos"], ["paid", "Pagados"]];
    const q = state.query.trim().toLowerCase();
    const list = state.payments.filter((p) => {
      const st = statusOf(p);
      if (state.filter === "owes" && !st.owes) return false;
      if (state.filter === "late" && st.key !== "late") return false;
      if (state.filter === "paid" && st.key !== "paid") return false;
      if (state.teacherFilter && String(p.teacherId) !== state.teacherFilter) return false;
      if (q && !`${studentName(p)} ${p.description} ${teacherName(p)}`.toLowerCase().includes(q)) return false;
      return true;
    });
    let html = `<div class="view-head row-between"><div><h1>Cobros</h1><p>De todos los profes de la academia.</p></div>
        <button class="btn btn-sm" type="button" data-action="refresh">${icons.refresh} Actualizar</button></div>
      <div class="toolbar">
        <div class="chips" role="group" aria-label="Filtrar cobros">${filters.map(([k, l]) => `<button type="button" class="chip" data-action="filter" data-filter="${k}" aria-pressed="${state.filter === k}">${l}</button>`).join("")}</div>
        <div class="row" style="flex-wrap:nowrap">
          <label class="sr-only" for="pay-search">Buscar</label><input class="input grow" id="pay-search" type="search" placeholder="Buscar alumno o concepto" value="${esc(state.query)}" />
          ${state.teachers.length > 1 ? `<label class="sr-only" for="pay-teacher">Profe</label><select class="select" id="pay-teacher" style="width:auto;min-width:150px"><option value="">Todos los profes</option>${state.teachers.map((t) => `<option value="${t.teacherProfileId}" ${state.teacherFilter === String(t.teacherProfileId) ? "selected" : ""}>${esc(t.fullName)}</option>`).join("")}</select>` : ""}
        </div>
      </div>`;
    if (!state.payments.length) html += emptyState("wallet", "Todavía no hay cobros", "Creá el primero: elegís profe, alumno y monto, y te damos el link para mandar.", "#nuevo", "Crear un cobro");
    else if (!list.length) html += `<div class="card empty"><p class="muted">No hay cobros con ese filtro.</p></div>`;
    else html += `<ul class="rows">${list.map((p) => paymentRow(p)).join("")}</ul>`;
    setView(html, "Cobros");
    const search = document.getElementById("pay-search");
    search.addEventListener("input", () => {
      state.query = search.value;
      const pos = search.selectionStart;
      renderPayments();
      const again = document.getElementById("pay-search"); again.focus(); again.setSelectionRange(pos, pos);
    });
    const sel = document.getElementById("pay-teacher");
    if (sel) sel.addEventListener("change", () => { state.teacherFilter = sel.value; renderPayments(); });
  }

  function renderPayment(id) {
    const p = paymentById(id);
    if (!p) return setView(`<a class="back" href="#cobros">${icons.arrowLeft} Cobros</a>` + emptyState("alert", "No encontramos ese cobro", "Puede que sea muy viejo o que el link esté mal.", "#cobros", "Ver los cobros"), "Cobro");
    const st = statusOf(p);
    const person = studentName(p);
    const statusCopy = {
      paid: `Mercado Pago confirmó el pago ${relativeDays(p.updatedAt || p.createdAt)}. La plata está en la cuenta de Mercado Pago de ${teacherName(p)}.`,
      pending: `Todavía no pagó. Vence ${relativeDays(p.dueAt)}.`,
      late: `Venció ${relativeDays(p.dueAt)}. Mandale un recordatorio amable.`,
      processing: "Pagó con un medio que tarda en acreditarse (por ejemplo, efectivo). Se marca solo cuando Mercado Pago confirme.",
      failed: "Intentó pagar pero Mercado Pago lo rechazó. Puede volver a intentar con el mismo link."
    }[st.key];
    const html = `<a class="back" href="#cobros">${icons.arrowLeft} Cobros</a>
      <div class="card detail-hero">
        <span class="pill ${st.pill}">${esc(st.label)}</span>
        <span class="amount">${esc(money(p.amount, p.currency))}</span>
        <h1 style="font-size:1.4rem;margin:6px 0 2px">${esc(p.description)}</h1>
        <p class="muted" style="margin:0">${p.studentUserId ? `<a href="#alumno/${p.studentUserId}">${esc(person)}</a>` : esc(person)} · ${esc(flowLabel(p.flowType))}</p>
        <p style="margin:14px 0 0">${esc(statusCopy)}</p>
        <div class="detail-actions">
          ${st.owes ? waButton(p, { cls: "btn btn-wa btn-lg btn-block", label: reminderKind(p) === "reminder" ? `Recordarle a ${firstName(person) || "el alumno"} por WhatsApp` : "Mandárselo por WhatsApp" }) : ""}
          <div class="detail-actions-2">
            <button type="button" class="btn" data-action="copy" data-id="${p.id}">${icons.copy} Copiar link</button>
            <a class="btn" href="${esc(payLink(p.id))}" target="_blank" rel="noopener">${icons.eye} Ver como alumno</a>
          </div>
        </div>
      </div>
      <div class="card">
        <h2 class="card-title">Detalle</h2>
        <dl class="facts">
          <dt>Alumno</dt><dd>${esc(person)}</dd>
          <dt>Profe</dt><dd>${esc(teacherName(p))}</dd>
          <dt>Tipo</dt><dd>${esc(flowLabel(p.flowType))}</dd>
          <dt>Creado</dt><dd>${esc(shortDate(p.createdAt))}</dd>
          ${p.dueAt && st.owes ? `<dt>Vence</dt><dd>${esc(shortDate(p.dueAt))}</dd>` : ""}
          <dt>Número</dt><dd>#${p.id}</dd>
        </dl>
      </div>
      <div class="card"><h2 class="card-title">Historial</h2><div id="timeline"><div class="skeleton" style="height:1.2em;width:70%"></div></div></div>`;
    setView(html, `${person} · ${p.description}`);
    loadTimeline(p.id);
  }

  async function loadTimeline(id) {
    const box = document.getElementById("timeline");
    try {
      const events = await api(`/api/backoffice/payments/${encodeURIComponent(id)}/events`, { slowAfter: 60000 });
      if (!document.body.contains(box)) return;
      const items = (events || []).slice().sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt)).map((e) => {
        const [text, cls] = eventText(e);
        return text ? `<li class="${cls}">${esc(text)}<span class="when">${esc(new Date(e.createdAt).toLocaleString("es-UY", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" }))}</span></li>` : "";
      }).join("");
      box.innerHTML = items ? `<ol class="timeline">${items}</ol>` : `<p class="muted">Sin movimientos todavía.</p>`;
    } catch (_e) {
      if (document.body.contains(box)) box.innerHTML = `<p class="muted">No pudimos cargar el historial ahora.</p>`;
    }
  }
  function eventText(e) {
    switch (e.eventType) {
      case "PAYMENT_CREATED": return ["Se creó el cobro", ""];
      case "PAYMENT_LINK_SENT": return ["Se envió el link por email", ""];
      case "WEBHOOK_RECEIVED": return ["Mercado Pago avisó un movimiento", ""];
      case "PAYMENT_STATUS_CHANGED":
        if (e.statusTo === "SUCCESS") return ["Mercado Pago confirmó el pago", "ok"];
        if (e.statusTo === "FAILURE") return ["Mercado Pago rechazó un intento de pago", "bad"];
        if (e.statusTo === "PENDING") return ["El pago quedó en proceso", ""];
        return ["Cambió el estado del cobro", ""];
      case "REMINDER_SENT": return ["Se envió un recordatorio", ""];
      case "DEBT_MARKED": return ["El cobro quedó vencido", "bad"];
      case "RECONCILIATION_MATCHED": return ["Pago verificado con Mercado Pago", "ok"];
      case "RECONCILIATION_MISMATCHED": return ["Hay una diferencia con Mercado Pago: la estamos revisando", "bad"];
      default: return ["", ""];
    }
  }

  /* ---------------- Nuevo cobro ---------------- */
  function renderNew({ studentId, teacherId }) {
    const ready = state.teachers.filter(canCharge);
    if (!state.teachers.length) {
      return setView(`<div class="view-head"><h1>Nuevo cobro</h1><p>Un paso antes de empezar.</p></div>` +
        emptyState("people", "Primero sumá a un profe", "Cada cobro lo recibe un profe en su Mercado Pago. Sumalo y le llega un email para entrar.", "#profe/nuevo", "Sumar un profe"), "Nuevo cobro");
    }
    if (!ready.length) {
      return setView(`<div class="view-head"><h1>Nuevo cobro</h1><p>Un paso antes de empezar.</p></div>` +
        emptyState("wallet", "Ningún profe conectó Mercado Pago todavía", "Hasta que lo conecten no podemos crear links de pago. Te mostramos qué mandarles.", "#equipo", "Ver el equipo"), "Nuevo cobro");
    }
    const last = store.getJson("micuota.academy.lastCharge", {});
    const presetTeacher = (teacherId && canCharge(teacherById(teacherId)) ? String(teacherId) : "") || (last.teacherId && canCharge(teacherById(last.teacherId)) ? String(last.teacherId) : "") || (ready.length === 1 ? String(ready[0].id) : "");
    const presetStudent = studentId && studentById(studentId) ? String(studentId) : "";
    const month = monthName(new Date());
    const suggestions = [`Cuota de ${month}`, "Clase", "Matrícula", "Taller"];
    const hasPeople = state.students.length > 0;

    const teacherOptions = state.teachers.map((t) => {
      const ok = canCharge(t);
      return `<label class="choice person-opt ${ok ? "" : "is-disabled"}"><input type="radio" name="teacher" value="${t.id}" ${presetTeacher === String(t.id) ? "checked" : ""} ${ok ? "" : "disabled"} />
        <span class="choice-box"><span class="avatar" aria-hidden="true">${esc(initials(t.fullName))}</span><span><strong>${esc(t.fullName)}</strong><span>${ok ? "Recibe en su Mercado Pago" : "Todavía no conectó Mercado Pago"}</span></span></span></label>`;
    }).join("");
    const studentOptions = state.students.map((s) => `<label class="choice person-opt"><input type="radio" name="person" value="${s.id}" ${presetStudent === String(s.id) ? "checked" : ""} /><span class="choice-box"><span class="avatar" aria-hidden="true">${esc(initials(s.fullName))}</span><span><strong>${esc(s.fullName)}</strong><span>${esc(s.email)}</span></span></span></label>`).join("");

    const html = `<a class="back" href="#inicio">${icons.arrowLeft} Inicio</a>
      <div class="view-head"><h1>Nuevo cobro</h1><p>Completá 4 cosas y te damos el link para mandar.</p></div>
      <form id="new-form" novalidate>
        <section class="form-section" aria-labelledby="q0">
          <h2 id="q0"><span class="n">1</span>¿Qué profe cobra?</h2>
          <fieldset class="choice-group" style="margin:0"><legend class="sr-only">Elegí el profe</legend><div class="people-select">${teacherOptions}</div></fieldset>
          <p class="hint" style="margin-top:8px">La plata va a la cuenta de Mercado Pago de ese profe.</p>
        </section>
        <section class="form-section" aria-labelledby="q1">
          <h2 id="q1"><span class="n">2</span>¿A quién le cobran?</h2>
          ${hasPeople ? `<fieldset class="choice-group" style="margin:0"><legend class="sr-only">Elegí un alumno</legend>
            ${state.students.length > 6 ? `<input class="input" type="search" id="person-filter" placeholder="Buscar alumno" aria-label="Buscar alumno" style="margin-bottom:8px" />` : ""}
            <div class="people-select" id="people-select">${studentOptions}
              <label class="choice person-opt"><input type="radio" name="person" value="new" /><span class="choice-box"><span class="avatar" aria-hidden="true">${icons.plus}</span><span><strong>Alguien nuevo</strong><span>Lo agregamos a la lista de alumnos</span></span></span></label>
            </div></fieldset>` : `<input type="hidden" name="person" value="new" />`}
          <div id="new-person" ${hasPeople ? "hidden" : ""} style="margin-top:14px">
            <div class="field"><label for="np-name">Nombre</label><input class="input" id="np-name" autocomplete="off" placeholder="Ej: Laura Gómez" /></div>
            <div class="field"><label for="np-email">Email</label><input class="input" id="np-email" type="email" inputmode="email" autocomplete="off" placeholder="laura@gmail.com" /><p class="hint">Mercado Pago lo usa para mandarle el comprobante.</p></div>
            <div class="field" style="margin-bottom:0"><label for="np-phone">WhatsApp <span class="muted">(opcional)</span></label><input class="input" id="np-phone" type="tel" inputmode="tel" autocomplete="off" placeholder="099 123 456" /><p class="hint">Con el número, el botón de WhatsApp abre directo su chat.</p></div>
          </div>
        </section>
        <section class="form-section" aria-labelledby="q2">
          <h2 id="q2"><span class="n">3</span>¿Por qué y cuánto?</h2>
          <div class="field">
            <label for="concept">Concepto</label>
            <input class="input" id="concept" maxlength="120" value="${esc(last.description || "")}" placeholder="Ej: Cuota de ${esc(month)}" />
            <div class="chips suggestions" aria-label="Sugerencias">${suggestions.map((s) => `<button type="button" class="chip" data-action="suggest" data-value="${esc(s)}">${esc(s)}</button>`).join("")}</div>
          </div>
          <div class="field" style="margin-bottom:0">
            <label for="amount">Monto</label>
            <div class="row" style="flex-wrap:nowrap">
              <div class="prefix-group grow"><span class="prefix" id="cur-prefix">$</span><input class="input input-money" id="amount" inputmode="decimal" autocomplete="off" placeholder="0" value="${last.amount ? esc(String(last.amount).replace(".", ",")) : ""}" /></div>
              <label class="sr-only" for="currency">Moneda</label>
              <select class="select" id="currency" style="width:auto;min-width:104px">${["UYU", "USD", "ARS"].map((c) => `<option value="${c}" ${(last.currency || "UYU") === c ? "selected" : ""}>${c}</option>`).join("")}</select>
            </div>
            <p class="hint">Lo que paga el alumno. Sin puntos, por ejemplo 1500.</p>
          </div>
        </section>
        <section class="form-section" aria-labelledby="q3">
          <h2 id="q3"><span class="n">4</span>¿Cada cuánto?</h2>
          <fieldset class="choice-group choice-grid-2" style="margin:0"><legend class="sr-only">Frecuencia</legend>
            <label class="choice"><input type="radio" name="flow" value="ONE_TIME" ${last.flow !== "SUBSCRIPTION" ? "checked" : ""} /><span class="choice-box"><strong>Una sola vez</strong><span>Paga y listo</span></span></label>
            <label class="choice"><input type="radio" name="flow" value="SUBSCRIPTION" ${last.flow === "SUBSCRIPTION" ? "checked" : ""} /><span class="choice-box"><strong>Todos los meses</strong><span>Autoriza una vez y se cobra solo</span></span></label>
          </fieldset>
        </section>
        <p class="field-error" id="new-error" role="alert" hidden></p>
        <div class="submit-bar"><button class="btn btn-primary btn-lg btn-block" type="submit" id="new-submit">Crear cobro y obtener link</button></div>
      </form>`;
    setView(html, "Nuevo cobro");

    const form = document.getElementById("new-form");
    const newPerson = document.getElementById("new-person");
    const prefix = document.getElementById("cur-prefix");
    prefix.textContent = (last.currency || "UYU") === "USD" ? "US$" : "$";
    form.addEventListener("change", (e) => {
      if (e.target.name === "person") newPerson.hidden = e.target.value !== "new";
      if (e.target.id === "currency") prefix.textContent = e.target.value === "USD" ? "US$" : "$";
    });
    const filter = document.getElementById("person-filter");
    if (filter) filter.addEventListener("input", () => {
      const q = filter.value.toLowerCase();
      document.querySelectorAll("#people-select .person-opt").forEach((opt) => {
        opt.hidden = opt.querySelector("input").value !== "new" && !opt.textContent.toLowerCase().includes(q);
      });
    });
    form.addEventListener("submit", submitNew);
  }

  function parseAmount(raw) {
    let s = String(raw || "").trim().replace(/[^\d.,]/g, "");
    if (!s) return NaN;
    if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
    else if (/^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, "");
    return Math.round(Number(s) * 100) / 100;
  }

  async function submitNew(e) {
    e.preventDefault();
    const err = document.getElementById("new-error");
    const fail = (msg, el) => { err.textContent = msg; err.hidden = false; if (el) { el.setAttribute("aria-invalid", "true"); el.focus(); } };
    err.hidden = true;
    view.querySelectorAll("[aria-invalid]").forEach((n) => n.removeAttribute("aria-invalid"));

    const teacher = teacherById((view.querySelector('input[name="teacher"]:checked') || {}).value);
    if (!teacher) return fail("Elegí qué profe cobra.", view.querySelector('input[name="teacher"]:not([disabled])'));
    const personChoice = (view.querySelector('input[name="person"]:checked') || view.querySelector('input[name="person"][type="hidden"]') || {}).value;
    if (!personChoice) return fail("Elegí a quién le cobran.", view.querySelector('input[name="person"]'));
    let student = personChoice !== "new" ? studentById(personChoice) : null;
    const npName = document.getElementById("np-name");
    const npEmail = document.getElementById("np-email");
    const npPhone = document.getElementById("np-phone");
    if (personChoice === "new") {
      if (!npName.value.trim()) return fail("Escribí el nombre del alumno.", npName);
      if (!/^\S+@\S+\.\S+$/.test(npEmail.value.trim())) return fail("Revisá el email del alumno.", npEmail);
    }
    const concept = document.getElementById("concept");
    if (!concept.value.trim()) return fail(`Escribí el concepto, por ejemplo “Cuota de ${monthName()}”.`, concept);
    const amountInput = document.getElementById("amount");
    const amount = parseAmount(amountInput.value);
    if (!(amount > 0)) return fail("Escribí el monto, por ejemplo 1500.", amountInput);
    const currency = document.getElementById("currency").value;
    const flow = (view.querySelector('input[name="flow"]:checked') || {}).value || "ONE_TIME";

    const btn = document.getElementById("new-submit");
    busy(btn, true, "Creando el link…");
    try {
      if (personChoice === "new") {
        const email = npEmail.value.trim().toLowerCase();
        const existing = state.students.find((s) => s.email.toLowerCase() === email);
        student = existing || await api("/api/backoffice/users", { method: "POST", body: { fullName: npName.value.trim(), email, role: "STUDENT" } });
        if (!existing) { state.students.push(student); state.students.sort(byName); }
        if (npPhone.value.trim()) { state.phones[student.id] = npPhone.value.trim(); savePhones(); }
      }
      const path = flow === "SUBSCRIPTION" ? "/api/backoffice/payments/subscriptions" : "/api/backoffice/payments/one-time";
      const op = await api(path, {
        method: "POST",
        body: { provider: "MERCADOPAGO", description: concept.value.trim(), amount, currency, payerEmail: student.email, studentUserId: student.id, teacherUserId: teacher.id }
      });
      store.setJson("micuota.academy.lastCharge", { description: concept.value.trim(), amount, currency, flow, teacherId: teacher.id });
      state.payments.unshift(op);
      location.hash = `#listo/${op.id}`;
    } catch (error) {
      fail(humanError(error));
    } finally {
      busy(btn, false);
    }
  }

  function renderCreated(id) {
    const p = paymentById(id);
    if (!p) { location.hash = "#cobros"; return; }
    const person = studentName(p);
    const msg = messageFor(p, "first");
    setView(`<div class="success-hero"><div class="icon">${icons.checkCircle}</div>
        <h1>¡Listo el cobro!</h1><p class="muted">${esc(money(p.amount, p.currency))} · ${esc(p.description)} · lo cobra ${esc(teacherName(p))}</p></div>
      <div class="card">
        <h2 class="card-title">Ahora mandáselo a ${esc(firstName(person) || "el alumno")}</h2>
        <a class="btn btn-wa btn-lg btn-block" href="${esc(waLink(msg, studentPhone(p)))}" target="_blank" rel="noopener">${icons.whatsapp} Mandar por WhatsApp</a>
        <div class="preview-msg" aria-label="Mensaje que se envía">${esc(msg)}</div>
        <div class="detail-actions-2" style="margin-top:14px">
          <button type="button" class="btn" data-action="copy" data-id="${p.id}">${icons.copy} Copiar link</button>
          <a class="btn" href="#cobro/${p.id}">${icons.eye} Ver el cobro</a>
        </div>
        <p class="small muted" style="margin:12px 0 0">Si tiene email, Mercado Pago también le manda el link.</p>
      </div>
      <a class="btn btn-ghost btn-block" href="#nuevo" style="margin-top:12px">Crear otro cobro</a>`, "Cobro creado");
  }

  /* ---------------- Alumnos ---------------- */
  function renderStudents() {
    const q = state.query.trim().toLowerCase();
    const list = state.students.filter((s) => !q || `${s.fullName} ${s.email}`.toLowerCase().includes(q));
    let html = `<div class="view-head row-between"><div><h1>Alumnos</h1><p>${state.students.length} ${state.students.length === 1 ? "alumno" : "alumnos"} en la academia.</p></div>
        <a class="btn btn-primary btn-sm" href="#alumno/nuevo">${icons.plus} Agregar</a></div>`;
    if (!state.students.length) {
      html += emptyState("people", "Todavía no hay alumnos", "Agregalos acá o directamente al crear un cobro.", "#alumno/nuevo", "Agregar un alumno");
      return setView(html, "Alumnos");
    }
    html += `<div class="field search"><label class="sr-only" for="st-search">Buscar alumno</label><input class="input" id="st-search" type="search" placeholder="Buscar por nombre o email" value="${esc(state.query)}" /></div>`;
    html += list.length ? `<ul class="rows">${list.map((s) => {
      const owes = paymentsOfStudent(s).filter((p) => statusOf(p).owes);
      return `<li class="row-item"><a class="row-link" href="#alumno/${s.id}"><span class="avatar" aria-hidden="true">${esc(initials(s.fullName))}</span>
        <span class="row-text"><p class="row-name">${esc(s.fullName)}</p><p class="row-sub">${esc(s.email)}</p></span>
        <span class="row-right">${owes.length ? `<span class="pill pill-warn">Debe ${owes.length}</span>` : `<span class="pill pill-ok">Al día</span>`}</span></a></li>`;
    }).join("")}</ul>` : `<div class="card empty"><p class="muted">No hay alumnos que coincidan.</p></div>`;
    setView(html, "Alumnos");
    const search = document.getElementById("st-search");
    search.addEventListener("input", () => {
      state.query = search.value;
      const pos = search.selectionStart;
      renderStudents();
      const again = document.getElementById("st-search"); again.focus(); again.setSelectionRange(pos, pos);
    });
  }

  function renderStudent(id) {
    const s = studentById(id);
    if (!s) return setView(`<a class="back" href="#alumnos">${icons.arrowLeft} Alumnos</a>` + emptyState("alert", "No encontramos a ese alumno", "Puede que lo hayan borrado.", "#alumnos", "Ver alumnos"), "Alumno");
    const pays = paymentsOfStudent(s);
    const owes = pays.filter((p) => statusOf(p).owes);
    setView(`<a class="back" href="#alumnos">${icons.arrowLeft} Alumnos</a>
      <div class="card center"><span class="avatar avatar-lg" style="margin:0 auto 10px" aria-hidden="true">${esc(initials(s.fullName))}</span>
        <h1 style="font-size:1.5rem;margin:0">${esc(s.fullName)}</h1><p class="muted" style="margin:4px 0 0">${esc(s.email)}</p>
        <p style="margin:12px 0 0">${owes.length ? `Debe <strong>${moneyLines(sumByCurrency(owes))}</strong>` : "Está al día."}</p>
        <a class="btn btn-primary btn-lg btn-block" href="#nuevo/alumno/${s.id}" style="margin-top:16px">Cobrarle</a></div>
      <form class="card" id="phone-form" novalidate>
        <div class="field" style="margin:0"><label for="ph">WhatsApp</label>
          <div class="input-group"><input class="input" id="ph" type="tel" inputmode="tel" value="${esc(state.phones[s.id] || "")}" placeholder="099 123 456" /><button class="btn" type="submit">Guardar</button></div>
          <p class="hint">Se guarda solo en este dispositivo, para abrir su chat directo.</p></div>
      </form>
      <h2 class="section-title">Cobros</h2>
      ${pays.length ? `<ul class="rows">${pays.map((p) => paymentRow(p)).join("")}</ul>` : `<div class="card empty"><p class="muted">Todavía no tiene cobros.</p></div>`}`, s.fullName);
    document.getElementById("phone-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const v = document.getElementById("ph").value.trim();
      if (v) state.phones[s.id] = v; else delete state.phones[s.id];
      savePhones();
      toast("Guardado");
    });
  }

  /* ---------------- Equipo ---------------- */
  function renderTeam() {
    let html = `<div class="view-head row-between"><div><h1>Equipo</h1><p>Los profes que cobran en la academia. Cada uno recibe la plata en su propio Mercado Pago.</p></div>
        <a class="btn btn-primary btn-sm" href="#profe/nuevo">${icons.plus} Sumar profe</a></div>`;
    if (!state.teachers.length) {
      html += emptyState("people", "Todavía no hay profes", "Sumá al primero: le llega un email para entrar y conectar su Mercado Pago.", "#profe/nuevo", "Sumar un profe");
      return setView(html, "Equipo");
    }
    html += `<ul class="rows">${state.teachers.map((t) => {
      const paid = paymentsOfTeacher(t).filter((p) => p.status === "SUCCESS");
      return `<li class="row-item"><a class="row-link" href="#profe/${t.id}"><span class="avatar" aria-hidden="true">${esc(initials(t.fullName))}</span>
        <span class="row-text"><p class="row-name">${esc(t.fullName)}</p><p class="row-sub">${esc(t.email)} · ${paid.length} ${paid.length === 1 ? "pago" : "pagos"}</p></span>
        <span class="row-right">${mpPill(t)}</span></a></li>`;
    }).join("")}</ul>`;
    setView(html, "Equipo");
  }

  function renderTeacher(id) {
    const t = teacherById(id);
    if (!t) return setView(`<a class="back" href="#equipo">${icons.arrowLeft} Equipo</a>` + emptyState("alert", "No encontramos a ese profe", "Puede que lo hayan borrado.", "#equipo", "Ver el equipo"), "Profe");
    const pays = paymentsOfTeacher(t);
    const paid = pays.filter((p) => p.status === "SUCCESS");
    const owes = pays.filter((p) => statusOf(p).owes);
    let status;
    if (t.mercadoPagoConnection === "OAUTH") {
      status = `<div class="notice notice-ok">${icons.checkCircle}<p><strong>Mercado Pago conectado.</strong> Ya puede cobrar: los pagos le llegan directo a su cuenta.</p></div>`;
    } else {
      const msg = teacherInviteMessage(t);
      status = `<div class="notice notice-warn">${icons.alert}<p><strong>${t.mercadoPagoConnection === "MANUAL" ? "Tiene que reconectar Mercado Pago" : "Todavía no conectó Mercado Pago"}.</strong> Hasta que lo haga no se le pueden crear cobros. Mandale estos pasos:</p></div>
        <div class="link-box copy-box" style="margin-top:12px">${esc(msg)}</div>
        <div class="detail-actions-2" style="margin-top:10px">
          <a class="btn btn-wa" href="${esc(waLink(msg, ""))}" target="_blank" rel="noopener">${icons.whatsapp} Mandar por WhatsApp</a>
          <button type="button" class="btn" data-action="copy-text" data-text="${esc(msg)}">${icons.copy} Copiar</button>
        </div>`;
    }
    setView(`<a class="back" href="#equipo">${icons.arrowLeft} Equipo</a>
      <div class="card center"><span class="avatar avatar-lg" style="margin:0 auto 10px" aria-hidden="true">${esc(initials(t.fullName))}</span>
        <h1 style="font-size:1.5rem;margin:0">${esc(t.fullName)}</h1><p class="muted" style="margin:4px 0 0">${esc(t.email)}</p></div>
      <div class="card">${status}</div>
      <div class="stats" style="margin-top:16px">
        <div class="stat"><p class="label">Cobrado</p><p class="value" style="font-size:1.8rem">${moneyLines(sumByCurrency(paid))}</p><p class="sub muted">${paid.length} ${paid.length === 1 ? "pago" : "pagos"}</p></div>
        <div class="stat"><p class="label">Le deben</p><p class="value" style="font-size:1.8rem">${moneyLines(sumByCurrency(owes))}</p><p class="sub muted">${owes.length} sin pagar</p></div>
      </div>
      ${canCharge(t) ? `<a class="btn btn-primary btn-lg btn-block" href="#nuevo/profe/${t.id}" style="margin-top:16px">Crear un cobro de ${esc(firstName(t.fullName))}</a>` : ""}
      <h2 class="section-title">Cobros</h2>
      ${pays.length ? `<ul class="rows">${pays.map((p) => paymentRow(p, { showTeacher: false })).join("")}</ul>` : `<div class="card empty"><p class="muted">Todavía no tiene cobros.</p></div>`}`, t.fullName);
  }

  /* ---------------- Alta de profe o alumno ---------------- */
  function renderNewPerson(role) {
    const isTeacher = role === "TEACHER";
    const back = isTeacher ? `<a class="back" href="#equipo">${icons.arrowLeft} Equipo</a>` : `<a class="back" href="#alumnos">${icons.arrowLeft} Alumnos</a>`;
    setView(`${back}
      <div class="view-head"><h1>${isTeacher ? "Sumar un profe" : "Agregar un alumno"}</h1>
        <p>${isTeacher ? "Le mandamos un email para que entre con su email (sin contraseña) y conecte su Mercado Pago." : "Después le podés crear cobros desde su ficha."}</p></div>
      <form class="card" id="person-form" novalidate>
        <div class="field"><label for="p-name">Nombre</label><input class="input" id="p-name" autocomplete="off" placeholder="Ej: ${isTeacher ? "Pedro Silva" : "Laura Gómez"}" /></div>
        <div class="field"><label for="p-email">Email</label><input class="input" id="p-email" type="email" inputmode="email" autocomplete="off" placeholder="${isTeacher ? "pedro@gmail.com" : "laura@gmail.com"}" />
          <p class="hint">${isTeacher ? "Con este email va a entrar a MiCuota." : "Mercado Pago lo usa para mandarle el comprobante."}</p></div>
        ${isTeacher ? "" : `<div class="field"><label for="p-phone">WhatsApp <span class="muted">(opcional)</span></label><input class="input" id="p-phone" type="tel" inputmode="tel" autocomplete="off" placeholder="099 123 456" /></div>`}
        <p class="field-error" id="p-error" role="alert" hidden></p>
        <button class="btn btn-primary btn-lg btn-block" type="submit" id="p-submit">${isTeacher ? "Sumar y mandar invitación" : "Agregar alumno"}</button>
      </form>`, isTeacher ? "Sumar profe" : "Agregar alumno");
    document.getElementById("p-name").focus();
    document.getElementById("person-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("p-error");
      const name = document.getElementById("p-name");
      const email = document.getElementById("p-email");
      err.hidden = true;
      if (!name.value.trim()) { err.textContent = "Escribí el nombre."; err.hidden = false; name.focus(); return; }
      if (!/^\S+@\S+\.\S+$/.test(email.value.trim())) { err.textContent = "Revisá el email: parece que le falta algo."; err.hidden = false; email.focus(); return; }
      const btn = document.getElementById("p-submit");
      busy(btn, true, "Guardando…");
      try {
        const created = await api("/api/backoffice/users", { method: "POST", body: { fullName: name.value.trim(), email: email.value.trim().toLowerCase(), role } });
        if (isTeacher) {
          state.teachers.push(created); state.teachers.sort(byName);
          toast(`Listo, le mandamos la invitación a ${firstName(created.fullName)}`);
          location.hash = `#profe/${created.id}`;
        } else {
          state.students.push(created); state.students.sort(byName);
          const phone = document.getElementById("p-phone").value.trim();
          if (phone) { state.phones[created.id] = phone; savePhones(); }
          toast(`${firstName(created.fullName)} quedó en la lista`);
          location.hash = `#alumno/${created.id}`;
        }
      } catch (error) {
        err.textContent = /ya existe un usuario con ese email/i.test(error.message || "") ? "Ya hay alguien con ese email en la academia." : humanError(error);
        err.hidden = false;
      } finally { busy(btn, false); }
    });
  }

  /* ---------------- Cuenta ---------------- */
  function renderAccount() {
    const big = document.documentElement.classList.contains("big-text");
    setView(`<div class="view-head"><h1>Mi cuenta</h1></div>
      <div class="card"><dl class="facts">
        <dt>Academia</dt><dd>${esc(state.me.tenantName || "—")}</dd>
        <dt>Código</dt><dd>${esc(state.me.tenantSlug || "—")}</dd>
        <dt>Tu nombre</dt><dd>${esc(state.me.fullName)}</dd>
        <dt>Tu email</dt><dd>${esc(state.me.email)}</dd>
      </dl>
      <p class="small muted" style="margin:14px 0 0">Para entrar usás tu email: te mandamos un código, sin contraseña.</p></div>
      <div class="card"><label class="row-between" style="cursor:pointer"><span><strong>Letra más grande</strong><br><span class="small muted">Más fácil de leer</span></span>
        <input type="checkbox" id="big-text" ${big ? "checked" : ""} style="width:24px;height:24px" /></label></div>
      <button type="button" class="btn btn-block btn-danger" data-action="logout" style="margin-top:16px">${icons.logout} Cerrar sesión</button>`, "Mi cuenta");
    document.getElementById("big-text").addEventListener("change", (e) => {
      document.documentElement.classList.toggle("big-text", e.target.checked);
      store.set("micuota.bigText", e.target.checked ? "1" : "0");
    });
  }

  /* ---------------- Acciones ---------------- */
  document.addEventListener("click", async (e) => {
    const el = e.target.closest("[data-action]");
    if (!el) return;
    const p = el.dataset.id ? paymentById(el.dataset.id) : null;
    switch (el.dataset.action) {
      case "copy": { if (!p) break; const ok = await copy(payLink(p.id)); toast(ok ? "Link copiado" : "No se pudo copiar", !ok); break; }
      case "copy-text": { const ok = await copy(el.dataset.text || ""); toast(ok ? "Copiado" : "No se pudo copiar", !ok); break; }
      case "filter": state.filter = el.dataset.filter || "all"; if (parseHash().name === "cobros") renderPayments(); break;
      case "suggest": { const c = document.getElementById("concept"); if (c) { c.value = el.dataset.value; c.focus(); } break; }
      case "refresh": busy(el, true, "Actualizando…"); await refresh({ silent: false }); toast("Listo, está actualizado"); break;
      case "logout": logout(); break;
      default: break;
    }
  });

  async function logout() {
    try { if (window.MicuotaSessionTelemetry) await window.MicuotaSessionTelemetry.logout(); } catch (_e) { /* no bloquea */ }
    session.clear();
    location.href = "/cuenta.html#entrar";
  }

  window.addEventListener("hashchange", () => { state.query = ""; route(); });
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible" && state.loaded) refresh(); });
  setInterval(() => { if (document.visibilityState === "visible" && state.loaded) refresh(); }, 60000);

  (async function boot() {
    try {
      if (await loadAll()) route();
    } catch (error) {
      if (MC.isSessionError(error) || (error.fromSession && (error.status === 400 || error.status === 401))) {
        session.clear();
        location.replace("/cuenta.html#entrar");
        return;
      }
      view.innerHTML = `<div class="card empty"><div class="empty-icon">${icons.alert}</div><h1 style="font-size:1.5rem">No pudimos cargar la academia</h1><p class="muted">${esc(humanError(error))}</p><button class="btn btn-primary btn-lg" type="button" id="boot-retry">Intentar de nuevo</button></div>`;
      document.getElementById("boot-retry").addEventListener("click", () => location.reload());
    }
  })();
})();
