/* MiCuota - app del que cobra (profesor.html).
   Vistas por hash: #inicio, #cobros, #cobro/{id}, #nuevo, #nuevo/persona/{id}, #listo/{id},
   #personas, #persona/{id}, #persona/nueva, #mercadopago, #cuenta */
(function () {
  "use strict";
  const MC = window.MC;
  const { api, session, store, esc, money, initials, firstName, statusOf, flowLabel, payLink, waLink, copy, toast, busy, icons, humanError, relativeDays, shortDate, monthName, qrSvg } = MC;

  session.adoptTokenFromUrl();
  if (!session.token()) { location.replace("/cuenta.html#entrar"); return; }

  const view = document.getElementById("view");
  const slugKey = () => session.slug() || "default";
  const state = {
    me: null,
    payments: [],
    students: [],
    settings: null,
    loaded: false,
    phones: store.getJson(`micuota.phones.${slugKey()}`, {}),
    shared: store.getJson(`micuota.shared.${slugKey()}`, {}),
    filter: "owes",
    search: "",
    lastCreated: null
  };

  /* ---------------- Iconos estaticos ---------------- */
  document.querySelectorAll("[data-icon]").forEach((n) => { n.innerHTML = icons[n.dataset.icon] || ""; });

  /* ---------------- Datos ---------------- */
  async function loadAll() {
    const me = await api("/api/auth/me").catch((err) => { err.fromSession = true; throw err; });
    if (me.role !== "TEACHER") {
      // Esta app es para quien cobra. Los demas roles siguen en sus pantallas.
      location.replace(session.homeFor(me.role));
      return false;
    }
    state.me = me;
    const [payments, students, settings, recurring] = await Promise.all([
      api("/api/backoffice/payments"),
      api("/api/backoffice/users?role=STUDENT").catch(() => []),
      api("/api/backoffice/payment-settings").catch(() => null),
      api("/api/backoffice/recurring-charges").catch(() => [])
    ]);
    state.recurring = recurring || [];
    state.payments = (payments || []).slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
    state.students = (students || []).slice().sort((a, b) => a.fullName.localeCompare(b.fullName, "es"));
    state.settings = settings;
    state.loaded = true;
    paintChrome();
    return true;
  }

  async function refreshPayments({ silent = true } = {}) {
    try {
      const payments = await api("/api/backoffice/payments", { slowAfter: silent ? 60000 : 3500 });
      const before = new Map(state.payments.map((p) => [p.id, p.status]));
      state.payments = (payments || []).slice().sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
      const newlyPaid = state.payments.filter((p) => p.status === "SUCCESS" && before.has(p.id) && before.get(p.id) !== "SUCCESS");
      newlyPaid.forEach((p) => toast(`${personName(p)} te pagó ${money(p.amount, p.currency)}`));
      if (newlyPaid.length || !silent) route();
    } catch (error) {
      if (!silent) toast(humanError(error), true);
    }
  }

  function saveLocal() {
    store.setJson(`micuota.phones.${slugKey()}`, state.phones);
    store.setJson(`micuota.shared.${slugKey()}`, state.shared);
  }

  const studentById = (id) => state.students.find((s) => String(s.id) === String(id));
  const paymentById = (id) => state.payments.find((p) => String(p.id) === String(id));
  function personName(p) { const s = p && p.studentUserId ? studentById(p.studentUserId) : null; return s ? s.fullName : "Sin cliente asignado"; }
  function personPhone(p) { return p && p.studentUserId ? state.phones[p.studentUserId] || "" : ""; }
  const mpConnected = () => Boolean(state.settings && state.settings.mercadoPagoConfigured);
  // Transferencia, giro o efectivo: el cliente ve los datos en la pagina de pago y vos marcas el cobro como pagado.
  const manualConfigured = () => Boolean(state.settings && (state.settings.transferAccountNumber || state.settings.transferAlias || state.settings.otherPaymentInstructions));
  const canCharge = () => mpConnected() || manualConfigured();
  const PAID_METHODS = [["TRANSFER", "Transferencia"], ["CASH", "Efectivo"], ["GIRO", "Giro"], ["OTHER", "Otro"]];
  const methodLabel = (m) => ({ MERCADOPAGO: "Mercado Pago", TRANSFER: "transferencia", CASH: "efectivo", GIRO: "giro", OTHER: "otro medio" }[m] || "");
  const paidByHand = (p) => p && p.status === "SUCCESS" && p.paidMethod && p.paidMethod !== "MERCADOPAGO";
  // Cobros mensuales sin Mercado Pago: MiCuota genera el cobro de cada mes a partir de esta plantilla.
  const recurringOf = (p) => (p && p.recurringChargeId ? (state.recurring || []).find((r) => String(r.id) === String(p.recurringChargeId)) : null);
  const kindLabel = (p) => (p && p.recurringChargeId ? "Cuota mensual" : flowLabel(p.flowType));

  /* ---------------- Mensajes de WhatsApp ---------------- */
  function messageFor(p, kind) {
    const who = firstName(personName(p));
    const hi = who && p.studentUserId ? `¡Hola ${who}!` : "¡Hola!";
    const amount = money(p.amount, p.currency);
    const link = payLink(p.id);
    const me = firstName(state.me && state.me.fullName);
    const concept = p.description || "el pago";
    if (kind === "reminder") {
      return `${hi} Te recuerdo que quedó pendiente ${concept} (${amount}). Lo podés pagar acá, es seguro y con Mercado Pago:\n${link}\n¡Gracias! ${me}`.trim();
    }
    if (p.flowType === "SUBSCRIPTION") {
      return `${hi} Te paso el link para activar tu cuota mensual de ${concept} (${amount} por mes). La autorizás una vez y Mercado Pago la cobra sola cada mes:\n${link}\n¡Gracias! ${me}`.trim();
    }
    return `${hi} Te paso el link para pagar ${concept} (${amount}). Es seguro, con Mercado Pago:\n${link}\n¡Gracias! ${me}`.trim();
  }
  function reminderKind(p) { return state.shared[p.id] || (Date.now() - new Date(p.createdAt).getTime() > 86400000) ? "reminder" : "first"; }
  function markShared(id) { state.shared[id] = new Date().toISOString(); saveLocal(); }

  function waButton(p, { label, cls = "btn btn-wa", kind } = {}) {
    const k = kind || reminderKind(p);
    const text = label || (k === "reminder" ? "Recordar" : "Enviar");
    return `<a class="${cls}" href="${esc(waLink(messageFor(p, k), personPhone(p)))}" target="_blank" rel="noopener" data-action="shared" data-id="${p.id}">${icons.whatsapp}<span>${esc(text)}</span></a>`;
  }

  /* ---------------- Chrome (menu, avatar) ---------------- */
  function paintChrome() {
    const name = state.me ? state.me.fullName : "";
    document.getElementById("topbar-avatar").textContent = initials(name);
    document.getElementById("sidebar-user").textContent = name ? `${name} · ${state.me.email}` : "";
  }
  function markNav(name) {
    const map = { cobro: "cobros", listo: "nuevo", persona: "personas", mercadopago: "cuenta", medios: "cuenta" };
    const active = map[name] || name;
    document.querySelectorAll("[data-nav]").forEach((a) => {
      if (a.dataset.nav === active) a.setAttribute("aria-current", "page"); else a.removeAttribute("aria-current");
    });
  }

  /* ---------------- Router ---------------- */
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
      nuevo: () => renderNew(parts[1] === "persona" ? parts[2] : null),
      listo: () => renderCreated(parts[1]),
      personas: renderPeople,
      persona: () => (parts[1] === "nueva" ? renderNewPerson() : renderPerson(parts[1])),
      medios: renderMethods,
      mercadopago: renderMercadoPago,
      cuenta: renderAccount
    };
    (routes[name] || renderHome)();
    markNav(name);
  }

  function setView(html, title) {
    view.innerHTML = html;
    document.title = `${title} | MiCuota`;
    window.scrollTo(0, 0);
    const h = view.querySelector("h1");
    if (h) { h.setAttribute("tabindex", "-1"); h.focus({ preventScroll: true }); }
  }

  /* ---------------- Componentes ---------------- */
  function paymentRow(p, { actions = true } = {}) {
    const st = statusOf(p);
    const person = personName(p);
    const when = st.key === "paid" ? `Pagó ${relativeDays(p.paidAt || p.updatedAt || p.createdAt)}${paidByHand(p) ? ` · ${methodLabel(p.paidMethod)}` : ""}` : st.key === "late" ? `Venció ${relativeDays(p.dueAt)}` : `Creado ${relativeDays(p.createdAt)}`;
    return `<li class="row-item">
      <a class="row-link" href="#cobro/${p.id}">
        <span class="avatar" aria-hidden="true">${esc(initials(person))}</span>
        <span class="row-text"><p class="row-name">${esc(person)}</p><p class="row-sub">${esc(p.description)} · ${esc(when)}</p></span>
        <span class="row-right"><span class="amount">${esc(money(p.amount, p.currency))}</span><span class="pill ${st.pill}">${esc(st.label)}</span></span>
      </a>
      ${actions && st.owes ? `<span class="row-actions">${waButton(p, { cls: "btn btn-wa btn-sm" })}</span>` : ""}
    </li>`;
  }

  function emptyState(icon, title, copyText, ctaHref, ctaLabel) {
    return `<div class="card empty"><div class="empty-icon">${icons[icon]}</div><h2>${esc(title)}</h2><p class="muted">${esc(copyText)}</p>${ctaHref ? `<a class="btn btn-primary btn-lg" href="${ctaHref}">${esc(ctaLabel)}</a>` : ""}</div>`;
  }

  function mpBanner() {
    if (canCharge()) return "";
    return `<div class="banner-mp" role="note">${icons.alert.replace("<svg", '<svg width="28" height="28" style="flex:none;color:var(--warn)"')}<p class="grow"><strong>Falta decir cómo te pagan.</strong> Mercado Pago, transferencia, giro o efectivo: elegí uno y ya podés cobrar.</p><a class="btn btn-primary" href="#medios">Elegir ahora</a></div>`;
  }

  /* ---------------- Inicio ---------------- */
  function onboardingSteps() {
    const anyShared = Object.keys(state.shared).length > 0 || state.payments.some((p) => p.status === "SUCCESS");
    return [
      { done: true, title: "Creaste tu cuenta", copy: "¡Bienvenida/o a MiCuota!" },
      { done: canCharge(), title: "Elegí cómo te pagan", copy: "Mercado Pago, transferencia, giro o efectivo.", href: "#medios", cta: "Elegir cómo me pagan" },
      { done: state.payments.length > 0, title: "Creá tu primer cobro", copy: "A quién, por qué y cuánto. Nada más.", href: "#nuevo", cta: "Crear mi primer cobro" },
      { done: anyShared, title: "Mandalo por WhatsApp", copy: "Tu cliente ve cómo pagarte desde el celular.", href: state.payments[0] ? `#listo/${state.payments[0].id}` : "#nuevo", cta: "Mandar por WhatsApp" }
    ];
  }

  function renderHome() {
    const name = firstName(state.me.fullName);
    const steps = onboardingSteps();
    const doneCount = steps.filter((s) => s.done).length;
    const next = steps.find((s) => !s.done);
    const hideOnboarding = !next || store.get(`micuota.onboardingHidden.${slugKey()}`) === "1";
    const hour = new Date().getHours();
    const greet = hour < 12 ? "Buen día" : hour < 20 ? "Buenas tardes" : "Buenas noches";

    let html = `<div class="view-head"><h1 class="hello">${greet}, ${esc(name)}</h1><p>${esc(todayLine())}</p></div>`;

    if (!hideOnboarding) {
      html += `<section class="onboard" aria-labelledby="ob-title">
        <h2 id="ob-title">Empezá a cobrar en 3 pasos</h2>
        <p class="muted" style="margin:0">Llevás ${doneCount} de ${steps.length}. ${next ? `Ahora: <strong>${esc(next.title.toLowerCase())}</strong>.` : ""}</p>
        <div class="progress" role="progressbar" aria-valuemin="0" aria-valuemax="${steps.length}" aria-valuenow="${doneCount}" aria-label="Progreso"><span style="width:${(doneCount / steps.length) * 100}%"></span></div>
        <ol class="steps">${steps.map((s, i) => `<li class="step ${s.done ? "is-done" : ""} ${s === next ? "is-next" : ""}">
          <span class="dot" aria-hidden="true">${s.done ? icons.check : i + 1}</span>
          <span class="grow"><p class="step-title">${esc(s.title)}<span class="sr-only">${s.done ? " (hecho)" : " (pendiente)"}</span></p><p class="step-copy">${esc(s.copy)}</p></span>
        </li>`).join("")}</ol>
        ${next ? `<a class="btn btn-primary btn-lg btn-block" style="margin-top:16px" href="${next.href}">${esc(next.cta)} ${icons.arrowRight}</a>` : ""}
        ${doneCount >= 3 && next ? `<button type="button" class="btn btn-ghost btn-sm btn-block" style="margin-top:8px" data-action="hide-onboarding">Ocultar esta guía</button>` : ""}
      </section>`;
    } else {
      html += mpBanner();
    }

    if (state.payments.length) {
      html += statsBlock();
      const todo = state.payments.filter((p) => statusOf(p).owes)
        .sort((a, b) => rank(a) - rank(b) || new Date(a.createdAt) - new Date(b.createdAt));
      html += `<h2 class="section-title">Para hacer hoy ${todo.length > 5 ? `<a href="#cobros">Ver todos (${todo.length})</a>` : ""}</h2>`;
      html += todo.length
        ? `<p class="muted small" style="margin-top:-4px">Tocá <strong>Recordar</strong> y se abre WhatsApp con el mensaje listo.</p><ul class="rows">${todo.slice(0, 5).map((p) => paymentRow(p)).join("")}</ul>`
        : `<div class="notice notice-ok">${icons.checkCircle}<p><strong>¡Nadie te debe nada!</strong> Todo lo que cobraste está pagado.</p></div>`;
      const paid = state.payments.filter((p) => p.status === "SUCCESS").slice(0, 3);
      if (paid.length) {
        html += `<h2 class="section-title">Últimos pagos <a href="#cobros" data-action="filter" data-filter="paid">Ver todos</a></h2><ul class="rows">${paid.map((p) => paymentRow(p, { actions: false })).join("")}</ul>`;
      }
    } else if (hideOnboarding) {
      html += emptyState("wallet", "Todavía no creaste cobros", "Creá tu primer cobro y mandalo por WhatsApp. Te avisamos acá cuando te paguen.", "#nuevo", "Crear mi primer cobro");
    }
    setView(html, "Inicio");
  }

  function rank(p) { return { late: 0, failed: 1, pending: 2, processing: 3 }[statusOf(p).key] ?? 4; }

  function todayLine() {
    const owes = state.payments.filter((p) => statusOf(p).owes);
    const late = owes.filter((p) => statusOf(p).key === "late").length;
    if (!state.payments.length) return "Acá vas a ver quién te pagó y quién no.";
    if (late) return `Tenés ${late} ${late === 1 ? "cobro vencido" : "cobros vencidos"} para recordar.`;
    if (owes.length) return `${owes.length} ${owes.length === 1 ? "persona tiene" : "personas tienen"} el pago pendiente.`;
    return "Todo al día. ¡Buenísimo!";
  }

  function sumByCurrency(list) {
    const totals = {};
    list.forEach((p) => { const c = (p.currency || "UYU").toUpperCase(); totals[c] = (totals[c] || 0) + Number(p.amount || 0); });
    const entries = Object.entries(totals).sort((a, b) => (a[0] === "UYU" ? -1 : b[0] === "UYU" ? 1 : 0));
    return entries.length ? entries : [["UYU", 0]];
  }

  function statsBlock() {
    const now = new Date();
    const paidThisMonth = state.payments.filter((p) => {
      if (p.status !== "SUCCESS") return false;
      const d = new Date(p.updatedAt || p.createdAt);
      return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
    });
    const owed = state.payments.filter((p) => statusOf(p).owes);
    const people = new Set(owed.map((p) => p.studentUserId || `op${p.id}`)).size;
    const [mainCur, mainVal] = sumByCurrency(paidThisMonth)[0];
    const extraPaid = sumByCurrency(paidThisMonth).slice(1).map(([c, v]) => money(v, c)).join(" + ");
    const [owCur, owVal] = sumByCurrency(owed)[0];
    return `<div class="stats" role="group" aria-label="Resumen">
      <div class="stat stat-main"><p class="label">Cobraste en ${esc(monthName(now))}</p><p class="value">${esc(money(mainVal, mainCur))}</p><p class="sub">${paidThisMonth.length} ${paidThisMonth.length === 1 ? "pago" : "pagos"}${extraPaid ? ` · y ${esc(extraPaid)}` : ""}</p></div>
      <a class="stat stat-owed ${owed.length ? "" : "is-zero"}" href="#cobros" data-action="filter" data-filter="owes" style="text-decoration:none;color:inherit"><p class="label">Te deben</p><p class="value">${esc(money(owVal, owCur))}</p><p class="sub muted">${owed.length ? `${people} ${people === 1 ? "persona" : "personas"} · ver quiénes` : "Nadie, ¡todo al día!"}</p></a>
    </div>`;
  }

  /* ---------------- Cobros ---------------- */
  function renderPayments() {
    const filters = [["owes", "Sin pagar"], ["paid", "Pagados"], ["all", "Todos"]];
    const q = state.search.trim().toLowerCase();
    const list = state.payments.filter((p) => {
      const st = statusOf(p);
      if (state.filter === "owes" && !st.owes) return false;
      if (state.filter === "paid" && st.key !== "paid") return false;
      if (q && !(`${personName(p)} ${p.description}`.toLowerCase().includes(q))) return false;
      return true;
    }).sort((a, b) => (state.filter === "owes" ? rank(a) - rank(b) : 0) || new Date(b.createdAt) - new Date(a.createdAt));

    let html = `<div class="view-head row-between"><div><h1>Cobros</h1><p>Los de Mercado Pago se marcan solos; si te pagan de otra forma, lo marcás vos.</p></div>
      <button class="btn btn-sm" type="button" data-action="refresh">${icons.refresh} Actualizar</button></div>`;
    if (!state.payments.length) {
      html += mpBanner() + emptyState("list", "Acá van a aparecer tus cobros", "Creá uno y mandalo por WhatsApp. Acá ves quién te pagó y quién no.", "#nuevo", "Crear un cobro");
      return setView(html, "Cobros");
    }
    html += `<div class="toolbar">
      <div class="chips" role="group" aria-label="Filtrar cobros">${filters.map(([k, l]) => `<button type="button" class="chip" data-action="filter" data-filter="${k}" aria-pressed="${state.filter === k}">${l}</button>`).join("")}</div>
      <label class="sr-only" for="search">Buscar por nombre o concepto</label>
      <input class="input search" id="search" type="search" placeholder="Buscar por nombre o concepto" value="${esc(state.search)}" autocomplete="off" />
    </div>`;
    html += list.length
      ? `<ul class="rows" id="payments-list">${list.map((p) => paymentRow(p)).join("")}</ul>`
      : `<div class="card empty"><p class="muted" style="margin:0">${state.filter === "owes" && !q ? "¡Nadie te debe nada! 🎉" : "No hay cobros que coincidan."}</p></div>`;
    html += `<p class="hint center" style="margin-top:14px">Mostramos tus últimos ${state.payments.length} cobros.</p>`;
    setView(html, "Cobros");
    const search = document.getElementById("search");
    search.addEventListener("input", () => {
      state.search = search.value;
      const pos = search.selectionStart;
      renderPayments();
      const s2 = document.getElementById("search"); s2.focus(); s2.setSelectionRange(pos, pos);
    });
  }

  /* ---------------- Detalle de un cobro ---------------- */
  function renderPayment(id) {
    const p = paymentById(id);
    if (!p) {
      return setView(`<a class="back" href="#cobros">${icons.arrowLeft} Cobros</a>` + emptyState("alert", "No encontramos ese cobro", "Puede que sea muy viejo o que el link esté mal.", "#cobros", "Ver mis cobros"), "Cobro");
    }
    const st = statusOf(p);
    const person = personName(p);
    const link = payLink(p.id);
    const statusCopy = {
      paid: paidByHand(p)
        ? `Lo marcaste como pagado ${relativeDays(p.paidAt || p.updatedAt)}, por ${methodLabel(p.paidMethod)}.${p.paidNote ? ` Nota: ${p.paidNote}` : ""}`
        : `Mercado Pago confirmó el pago ${relativeDays(p.paidAt || p.updatedAt || p.createdAt)}. La plata ya está en tu cuenta de Mercado Pago.`,
      pending: `Todavía no pagó. Vence ${relativeDays(p.dueAt)}.${p.provider === "MANUAL" ? " Cuando te pague, tocá «Ya me pagó»." : ""}`,
      late: `Venció ${relativeDays(p.dueAt)}. Mandale un recordatorio amable.`,
      processing: "Pagó con un medio que tarda en acreditarse (por ejemplo, efectivo). Se marca solo cuando Mercado Pago confirme.",
      failed: "Intentó pagar pero Mercado Pago lo rechazó. Puede volver a intentar con el mismo link."
    }[st.key];
    const html = `<a class="back" href="#cobros">${icons.arrowLeft} Cobros</a>
      <div class="card detail-hero">
        <span class="pill ${st.pill}">${esc(st.label)}</span>
        <span class="amount">${esc(money(p.amount, p.currency))}</span>
        <h1 style="font-size:1.4rem;margin:6px 0 2px">${esc(p.description)}</h1>
        <p class="muted" style="margin:0">${p.studentUserId ? `<a href="#persona/${p.studentUserId}">${esc(person)}</a>` : esc(person)} · ${esc(kindLabel(p))}</p>
        <p style="margin:14px 0 0">${esc(statusCopy)}</p>
        <div class="detail-actions">
          ${st.owes ? waButton(p, { cls: "btn btn-wa btn-lg btn-block", label: reminderKind(p) === "reminder" ? `Recordarle a ${firstName(person) || "tu cliente"} por WhatsApp` : `Mandárselo por WhatsApp` }) : ""}
          ${st.owes ? `<button type="button" class="btn btn-primary btn-lg btn-block" data-action="paid-open">${icons.check} Ya me pagó</button>
            <form id="paid-form" class="card card-flat" hidden novalidate style="text-align:left">
              <fieldset class="choice-group choice-grid-2" style="margin-bottom:12px"><legend>¿Cómo te pagó?</legend>
                ${PAID_METHODS.map(([k, l], i) => `<label class="choice"><input type="radio" name="method" value="${k}" ${i === 0 ? "checked" : ""} /><span class="choice-box center"><strong>${l}</strong></span></label>`).join("")}
              </fieldset>
              <div class="field"><label for="paid-note">Nota <span class="muted">(opcional)</span></label><input class="input" id="paid-note" maxlength="300" autocomplete="off" placeholder="Ej: número de transferencia" /></div>
              <button class="btn btn-primary btn-block" type="submit" id="paid-submit">Marcar como pagado</button>
              <p class="hint" style="margin-top:8px">Si te pagó por Mercado Pago no hace falta: se marca solo.</p>
            </form>` : ""}
          <div class="detail-actions-2">
            <button type="button" class="btn" data-action="copy" data-id="${p.id}">${icons.copy} Copiar link</button>
            <button type="button" class="btn" data-action="qr" data-id="${p.id}">${icons.qr} Mostrar QR</button>
          </div>
          <a class="btn btn-ghost" href="${esc(link)}" target="_blank" rel="noopener">${icons.eye} Ver lo que ve tu cliente</a>
          ${paidByHand(p) ? `<button type="button" class="btn btn-ghost" data-action="paid-undo" data-id="${p.id}">Deshacer: no me pagó</button>` : ""}
        </div>
      </div>
      <div class="card">
        <h2 class="card-title">Detalle</h2>
        <dl class="facts">
          <dt>Cliente</dt><dd>${esc(person)}</dd>
          <dt>Tipo</dt><dd>${esc(kindLabel(p))}</dd>
          ${recurringOf(p) ? `<dt>Se repite</dt><dd>Todos los meses, el día ${recurringOf(p).dayOfMonth}</dd>` : ""}
          ${st.key === "paid" && p.paidMethod ? `<dt>Pagó con</dt><dd>${esc(methodLabel(p.paidMethod).replace(/^./, (c) => c.toUpperCase()))}</dd>` : ""}
          <dt>Creado</dt><dd>${esc(shortDate(p.createdAt))}</dd>
          ${p.dueAt && st.owes ? `<dt>Vence</dt><dd>${esc(shortDate(p.dueAt))}</dd>` : ""}
          ${p.netAmountForTeacher != null && st.key === "paid" && !paidByHand(p) && Number(p.netAmountForTeacher) !== Number(p.amount) ? `<dt>Te queda</dt><dd>${esc(money(p.netAmountForTeacher, p.currency))}<br><span class="small muted" style="font-weight:400">después de la comisión de MiCuota</span></dd>` : ""}
          <dt>Número</dt><dd>#${p.id}</dd>
        </dl>
      </div>
      ${recurringOf(p) ? `<button type="button" class="btn btn-ghost btn-block" data-action="recurring-stop" data-recurring="${recurringOf(p).id}" style="margin-bottom:12px">Dejar de cobrar todos los meses</button>` : ""}
      <div class="card">
        <h2 class="card-title">Historial</h2>
        <div id="timeline"><div class="skeleton" style="height:1.2em;width:70%"></div><div class="skeleton" style="height:1.2em;width:50%;margin-top:10px"></div></div>
      </div>`;
    setView(html, `${person} · ${p.description}`);
    loadTimeline(p.id);
    const paidForm = document.getElementById("paid-form");
    if (paidForm) paidForm.addEventListener("submit", (e) => { e.preventDefault(); markPaid(p, paidForm); });
  }

  function replacePayment(updated) {
    state.payments = state.payments.map((x) => (String(x.id) === String(updated.id) ? updated : x));
  }

  async function markPaid(p, form) {
    const method = (form.querySelector('input[name="method"]:checked') || {}).value || "OTHER";
    const btn = document.getElementById("paid-submit");
    busy(btn, true, "Guardando…");
    try {
      const updated = await api(`/api/backoffice/payments/${encodeURIComponent(p.id)}/mark-paid`, {
        method: "POST",
        body: { method, note: document.getElementById("paid-note").value.trim() || null }
      });
      replacePayment(updated);
      toast(`Listo: ${firstName(personName(updated)) || "el cobro"} quedó como pagado`);
      renderPayment(updated.id);
    } catch (error) {
      toast(humanError(error), true);
      busy(btn, false);
    }
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
      case "PAYMENT_CREATED": return ["Creaste el cobro", ""];
      case "PAYMENT_LINK_SENT": return ["Se envió el link por email", ""];
      case "WEBHOOK_RECEIVED": return ["Mercado Pago avisó un movimiento", ""];
      case "PAYMENT_STATUS_CHANGED":
        if (/"undo":true/.test(e.rawPayload || "")) return ["Deshiciste el pago marcado", "bad"];
        if (/"bank":true/.test(e.rawPayload || "")) return ["Te pagó por transferencia desde su banco (confirmado)", "ok"];
        if (/"manual":true/.test(e.rawPayload || "")) {
          const m = (/"method":"(\w+)"/.exec(e.rawPayload) || [])[1];
          return [`Lo marcaste como pagado${m ? ` (${methodLabel(m)})` : ""}`, "ok"];
        }
        if (e.statusTo === "SUCCESS") return ["Mercado Pago confirmó el pago", "ok"];
        if (e.statusTo === "FAILURE") return ["Mercado Pago rechazó un intento de pago", "bad"];
        if (e.statusTo === "PENDING") return ["El pago quedó en proceso", ""];
        return ["Cambió el estado del cobro", ""];
      case "REMINDER_SENT": return ["Se envió un recordatorio", ""];
      case "RETRY_SCHEDULED": return ["Se programó un nuevo recordatorio", ""];
      case "DEBT_MARKED": return ["El cobro quedó vencido", "bad"];
      case "RECONCILIATION_MATCHED": return ["Pago verificado con Mercado Pago", "ok"];
      case "RECONCILIATION_MISMATCHED": return ["Hay una diferencia con Mercado Pago: la estamos revisando", "bad"];
      default: return ["", ""];
    }
  }

  /* ---------------- Nuevo cobro ---------------- */
  function renderNew(presetPersonId) {
    if (!canCharge()) {
      return setView(`<div class="view-head"><h1>Nuevo cobro</h1><p>Un paso antes de empezar.</p></div>
        <div class="card empty"><div class="empty-icon">${icons.wallet}</div>
        <h2>Primero elegí cómo te pagan</h2>
        <p class="muted">Mercado Pago, transferencia, giro o efectivo. Es una sola vez y tarda un minuto.</p>
        <a class="btn btn-primary btn-lg" href="#medios">Elegir cómo me pagan</a></div>`, "Nuevo cobro");
    }
    const last = store.getJson("micuota.lastCharge", {});
    const month = monthName(new Date());
    const suggestions = [`Cuota de ${month}`, "Sesión", "Consulta", "Servicio"];
    const hasPeople = state.students.length > 0;
    const preset = presetPersonId && studentById(presetPersonId) ? String(presetPersonId) : "";
    const peopleOptions = state.students.map((s) => `<label class="choice person-opt"><input type="radio" name="person" value="${s.id}" ${preset === String(s.id) ? "checked" : ""} /><span class="choice-box"><span class="avatar" aria-hidden="true">${esc(initials(s.fullName))}</span><span><strong>${esc(s.fullName)}</strong><span>${esc(s.email)}</span></span></span></label>`).join("");

    const html = `<a class="back" href="#inicio">${icons.arrowLeft} Inicio</a>
      <div class="view-head"><h1>Nuevo cobro</h1><p>Completá 3 cosas y te damos el link para mandar.</p></div>
      <form id="new-form" novalidate>
        <section class="form-section" aria-labelledby="q1">
          <h2 id="q1"><span class="n">1</span>¿A quién le cobrás?</h2>
          ${hasPeople ? `<fieldset class="choice-group" style="margin:0"><legend class="sr-only">Elegí un cliente</legend>
            ${state.students.length > 6 ? `<input class="input" type="search" id="person-filter" placeholder="Buscar cliente" aria-label="Buscar cliente" style="margin-bottom:8px" />` : ""}
            <div class="people-select" id="people-select">${peopleOptions}
              <label class="choice person-opt"><input type="radio" name="person" value="new" /><span class="choice-box"><span class="avatar" aria-hidden="true">${icons.plus}</span><span><strong>Alguien nuevo</strong><span>Lo agregamos a tu lista</span></span></span></label>
            </div></fieldset>` : `<input type="hidden" name="person" value="new" />`}
          <div id="new-person" ${hasPeople ? "hidden" : ""} style="margin-top:14px">
            <div class="field"><label for="np-name">Nombre</label><input class="input" id="np-name" autocomplete="off" placeholder="Ej: Laura Gómez" /></div>
            <div class="field"><label for="np-email">Email</label><input class="input" id="np-email" type="email" inputmode="email" autocomplete="off" placeholder="laura@gmail.com" /><p class="hint">Mercado Pago lo usa para mandarle el comprobante.</p></div>
            <div class="field" style="margin-bottom:0"><label for="np-phone">WhatsApp <span class="muted">(opcional)</span></label><input class="input" id="np-phone" type="tel" inputmode="tel" autocomplete="off" placeholder="099 123 456" /><p class="hint">Con el número, el botón de WhatsApp abre directo su chat.</p></div>
          </div>
        </section>

        <section class="form-section" aria-labelledby="q2">
          <h2 id="q2"><span class="n">2</span>¿Por qué y cuánto?</h2>
          <div class="field">
            <label for="concept">Concepto</label>
            <input class="input" id="concept" maxlength="120" value="${esc(last.description || "")}" placeholder="Ej: Cuota de ${esc(month)}" />
            <div class="chips suggestions" aria-label="Sugerencias">${suggestions.map((s) => `<button type="button" class="chip" data-action="suggest" data-value="${esc(s)}">${esc(s)}</button>`).join("")}</div>
          </div>
          <div class="field" style="margin-bottom:0">
            <label for="amount">Monto</label>
            <div class="row" style="flex-wrap:nowrap">
              <div class="prefix-group grow"><span class="prefix" id="cur-prefix">$</span><input class="input input-money" id="amount" inputmode="decimal" autocomplete="off" placeholder="0" value="${last.amount ? esc(String(last.amount).replace(".", ",")) : ""}" aria-describedby="amount-hint" /></div>
              <label class="sr-only" for="currency">Moneda</label>
              <select class="select" id="currency" style="width:auto;min-width:104px">
                ${["UYU", "USD", "ARS"].map((c) => `<option value="${c}" ${(last.currency || "UYU") === c ? "selected" : ""}>${c}</option>`).join("")}
              </select>
            </div>
            <p class="hint" id="amount-hint">Lo que paga tu cliente. Sin puntos, por ejemplo 1500.</p>
          </div>
        </section>

        <section class="form-section" aria-labelledby="q3">
          <h2 id="q3"><span class="n">3</span>¿Cada cuánto?</h2>
          <fieldset class="choice-group choice-grid-2" style="margin:0"><legend class="sr-only">Frecuencia</legend>
            <label class="choice"><input type="radio" name="flow" value="ONE_TIME" ${last.flow !== "SUBSCRIPTION" ? "checked" : ""} /><span class="choice-box"><strong>Una sola vez</strong><span>Paga y listo</span></span></label>
            <label class="choice"><input type="radio" name="flow" value="SUBSCRIPTION" ${last.flow === "SUBSCRIPTION" ? "checked" : ""} /><span class="choice-box"><strong>Todos los meses</strong><span>${mpConnected() ? "Autoriza una vez y se cobra solo" : "Cada mes le mandamos el cobro nuevo"}</span></span></label>
          </fieldset>
        </section>

        <p class="field-error" id="new-error" role="alert" hidden></p>
        <div class="submit-bar"><button class="btn btn-primary btn-lg btn-block" type="submit" id="new-submit">Crear cobro y obtener link</button></div>
      </form>`;
    setView(html, "Nuevo cobro");

    const form = document.getElementById("new-form");
    const newPerson = document.getElementById("new-person");
    form.addEventListener("change", (e) => {
      if (e.target.name === "person") newPerson.hidden = e.target.value !== "new";
      if (e.target.id === "currency") document.getElementById("cur-prefix").textContent = e.target.value === "USD" ? "US$" : "$";
    });
    document.getElementById("cur-prefix").textContent = (last.currency || "UYU") === "USD" ? "US$" : "$";
    const filter = document.getElementById("person-filter");
    if (filter) filter.addEventListener("input", () => {
      const q = filter.value.toLowerCase();
      document.querySelectorAll("#people-select .person-opt").forEach((opt) => {
        const v = opt.querySelector("input").value;
        opt.hidden = v !== "new" && !opt.textContent.toLowerCase().includes(q);
      });
    });
    form.addEventListener("submit", submitNew);
  }

  function parseAmount(raw) {
    let s = String(raw || "").trim().replace(/[^\d.,]/g, "");
    if (!s) return NaN;
    // "1.500" o "1.500,50" (formato local) vs "1500.50"
    if (s.includes(",")) s = s.replace(/\./g, "").replace(",", ".");
    else if (/^\d{1,3}(\.\d{3})+$/.test(s)) s = s.replace(/\./g, "");
    return Math.round(Number(s) * 100) / 100;
  }

  function randomPassword() {
    const bytes = new Uint8Array(18);
    crypto.getRandomValues(bytes);
    return Array.from(bytes, (b) => b.toString(36).padStart(2, "0")).join("").slice(0, 24) + "A1!";
  }

  async function submitNew(e) {
    e.preventDefault();
    const err = document.getElementById("new-error");
    const fail = (msg, el) => { err.textContent = msg; err.hidden = false; if (el) { el.setAttribute("aria-invalid", "true"); el.focus(); } };
    err.hidden = true;
    view.querySelectorAll("[aria-invalid]").forEach((n) => n.removeAttribute("aria-invalid"));

    const personChoice = (view.querySelector('input[name="person"]:checked') || view.querySelector('input[name="person"][type="hidden"]') || {}).value;
    if (!personChoice) return fail("Elegí a quién le cobrás.", view.querySelector('input[name="person"]'));
    let student = personChoice !== "new" ? studentById(personChoice) : null;
    const npName = document.getElementById("np-name");
    const npEmail = document.getElementById("np-email");
    const npPhone = document.getElementById("np-phone");
    if (personChoice === "new") {
      if (!npName.value.trim()) return fail("Escribí el nombre de tu cliente.", npName);
      if (!/^\S+@\S+\.\S+$/.test(npEmail.value.trim())) return fail("Revisá el email de tu cliente.", npEmail);
    }
    const concept = document.getElementById("concept");
    if (!concept.value.trim()) return fail("Escribí el concepto, por ejemplo “Cuota de " + monthName() + "”.", concept);
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
        student = existing || await api("/api/backoffice/users", {
          method: "POST",
          body: { fullName: npName.value.trim(), email, password: randomPassword(), role: "STUDENT" }
        });
        if (!existing) state.students.push(student);
        if (npPhone.value.trim()) { state.phones[student.id] = npPhone.value.trim(); saveLocal(); }
      }
      const path = flow === "SUBSCRIPTION" ? "/api/backoffice/payments/subscriptions" : "/api/backoffice/payments/one-time";
      const op = await api(path, {
        method: "POST",
        // Con Mercado Pago conectado el link cobra por ahi (y muestra las otras formas); si no, es un cobro manual.
        body: { provider: mpConnected() ? "MERCADOPAGO" : "MANUAL", description: concept.value.trim(), amount, currency, payerEmail: student.email, studentUserId: student.id }
      });
      store.setJson("micuota.lastCharge", { description: concept.value.trim(), amount, currency, flow });
      state.payments.unshift(op);
      state.lastCreated = op.id;
      if (op.recurringChargeId) state.recurring = await api("/api/backoffice/recurring-charges").catch(() => state.recurring);
      location.hash = `#listo/${op.id}`;
    } catch (error) {
      fail(humanError(error));
      if (/access token|conectar/i.test(error.message || "")) {
        state.settings = Object.assign({}, state.settings, { mercadoPagoConfigured: false });
      }
    } finally {
      busy(btn, false);
    }
  }

  /* ---------------- Cobro creado: compartir ---------------- */
  function renderCreated(id) {
    const p = paymentById(id);
    if (!p) { location.hash = "#cobros"; return; }
    const person = personName(p);
    const msg = messageFor(p, "first");
    const phone = personPhone(p);
    const html = `<div class="success-hero"><div class="icon">${icons.check}</div>
        <h1>¡Listo! Ahora mandáselo</h1>
        <p class="muted">${esc(p.description)} · <strong>${esc(money(p.amount, p.currency))}</strong> · ${esc(person)}</p></div>
      <div class="card">
        <a class="btn btn-wa btn-lg btn-block" href="${esc(waLink(msg, phone))}" target="_blank" rel="noopener" data-action="shared" data-id="${p.id}">${icons.whatsapp} Mandar por WhatsApp${p.studentUserId ? ` a ${esc(firstName(person))}` : ""}</a>
        <p class="hint center" style="margin-top:8px">${phone ? "Se abre su chat con este mensaje listo:" : "Se abre WhatsApp, elegís el chat y le das enviar. Este es el mensaje:"}</p>
        <div class="preview-msg">${esc(msg)}</div>
        <div class="detail-actions-2" style="margin-top:16px">
          <button type="button" class="btn" data-action="copy" data-id="${p.id}">${icons.copy} Copiar link</button>
          <button type="button" class="btn" data-action="qr" data-id="${p.id}">${icons.qr} Mostrar QR</button>
        </div>
        ${navigator.share ? `<button type="button" class="btn btn-ghost btn-block" style="margin-top:8px" data-action="native-share" data-id="${p.id}">${icons.share} Compartir por otra app</button>` : ""}
      </div>
      <div class="notice notice-info" style="margin-top:14px">${icons.bell}<p>${p.recurringChargeId
        ? `Este es el cobro de este mes. <strong>Cada mes, el día ${(recurringOf(p) || {}).dayOfMonth || new Date().getDate()}</strong>, le mandamos el nuevo por email. Cuando te pague, tocá «Ya me pagó».`
        : p.provider === "MANUAL"
        ? "Cuando te pague, entrá al cobro y tocá <strong>«Ya me pagó»</strong>. Mientras tanto le recordamos por email."
        : "Si paga con Mercado Pago, <strong>se marca solo</strong>. Si te paga de otra forma, entrá al cobro y tocá «Ya me pagó»."}</p></div>
      <div class="detail-actions-2" style="margin-top:16px">
        <a class="btn" href="#nuevo">${icons.plus} Otro cobro</a>
        <a class="btn" href="#inicio">${icons.home} Ir al inicio</a>
      </div>`;
    setView(html, "Cobro creado");
  }

  /* ---------------- Clientes ---------------- */
  function personSummary(s) {
    const mine = state.payments.filter((p) => String(p.studentUserId) === String(s.id));
    const owed = mine.filter((p) => statusOf(p).owes);
    const late = owed.some((p) => statusOf(p).key === "late");
    if (!mine.length) return { label: "Sin cobros", pill: "pill-info", owed, mine };
    if (!owed.length) return { label: "Al día", pill: "pill-ok", owed, mine };
    const [cur, val] = sumByCurrency(owed)[0];
    return { label: `Debe ${money(val, cur)}`, pill: late ? "pill-late" : "pill-warn", owed, mine };
  }

  function renderPeople() {
    let html = `<div class="view-head row-between"><div><h1>Clientes</h1><p>Las personas a las que les cobrás.</p></div><a class="btn btn-sm" href="#persona/nueva">${icons.plus} Agregar</a></div>`;
    if (!state.students.length) {
      html += emptyState("people", "Todavía no agregaste clientes", "Podés agregarlos acá o directamente cuando creás un cobro.", "#persona/nueva", "Agregar cliente");
      return setView(html, "Clientes");
    }
    const sorted = state.students.slice().sort((a, b) => personSummary(b).owed.length - personSummary(a).owed.length || a.fullName.localeCompare(b.fullName, "es"));
    html += `<ul class="rows">${sorted.map((s) => {
      const sum = personSummary(s);
      return `<li class="row-item"><a class="row-link" href="#persona/${s.id}"><span class="avatar" aria-hidden="true">${esc(initials(s.fullName))}</span><span class="row-text"><p class="row-name">${esc(s.fullName)}</p><p class="row-sub">${esc(state.phones[s.id] || s.email)}</p></span><span class="row-right"><span class="pill ${sum.pill}">${esc(sum.label)}</span></span></a></li>`;
    }).join("")}</ul>`;
    setView(html, "Clientes");
  }

  function renderPerson(id) {
    const s = studentById(id);
    if (!s) return setView(`<a class="back" href="#personas">${icons.arrowLeft} Clientes</a>` + emptyState("alert", "No encontramos a esa persona", "", "#personas", "Ver clientes"), "Cliente");
    const sum = personSummary(s);
    const html = `<a class="back" href="#personas">${icons.arrowLeft} Clientes</a>
      <div class="card detail-hero">
        <span class="avatar avatar-lg" style="margin:0 auto" aria-hidden="true">${esc(initials(s.fullName))}</span>
        <h1 style="font-size:1.6rem;margin:12px 0 4px">${esc(s.fullName)}</h1>
        <p class="muted" style="margin:0 0 10px">${esc(s.email)}</p>
        <span class="pill ${sum.pill}">${esc(sum.label)}</span>
        <div class="detail-actions"><a class="btn btn-primary btn-lg btn-block" href="#nuevo/persona/${s.id}">${icons.plus} Nuevo cobro para ${esc(firstName(s.fullName))}</a></div>
      </div>
      <form class="card" id="phone-form">
        <h2 class="card-title">WhatsApp</h2>
        <div class="field"><label for="p-phone">Número de WhatsApp</label>
          <div class="input-group"><input class="input" id="p-phone" type="tel" inputmode="tel" value="${esc(state.phones[s.id] || "")}" placeholder="099 123 456" /><button class="btn" type="submit">Guardar</button></div>
          <p class="hint">Se guarda en este dispositivo. Sirve para abrir su chat directo al recordarle.</p></div>
      </form>
      <h2 class="section-title">Sus cobros</h2>
      ${sum.mine.length ? `<ul class="rows">${sum.mine.map((p) => paymentRow(p)).join("")}</ul>` : `<div class="card empty"><p class="muted" style="margin:0">Todavía no le cobraste nada.</p></div>`}`;
    setView(html, s.fullName);
    document.getElementById("phone-form").addEventListener("submit", (e) => {
      e.preventDefault();
      const v = document.getElementById("p-phone").value.trim();
      if (v) state.phones[s.id] = v; else delete state.phones[s.id];
      saveLocal();
      toast("WhatsApp guardado");
    });
  }

  function renderNewPerson() {
    const html = `<a class="back" href="#personas">${icons.arrowLeft} Clientes</a>
      <div class="view-head"><h1>Agregar cliente</h1><p>Solo lo necesario para cobrarle.</p></div>
      <form class="card" id="person-form" novalidate>
        <div class="field"><label for="pf-name">Nombre</label><input class="input" id="pf-name" autocomplete="off" required /></div>
        <div class="field"><label for="pf-email">Email</label><input class="input" id="pf-email" type="email" inputmode="email" autocomplete="off" required /><p class="hint">Mercado Pago lo usa para mandarle el comprobante.</p></div>
        <div class="field"><label for="pf-phone">WhatsApp <span class="muted">(opcional)</span></label><input class="input" id="pf-phone" type="tel" inputmode="tel" autocomplete="off" placeholder="099 123 456" /></div>
        <p class="field-error" id="pf-error" role="alert" hidden></p>
        <button class="btn btn-primary btn-lg btn-block" type="submit" id="pf-submit">Guardar cliente</button>
      </form>`;
    setView(html, "Agregar cliente");
    document.getElementById("person-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const name = document.getElementById("pf-name");
      const email = document.getElementById("pf-email");
      const err = document.getElementById("pf-error");
      err.hidden = true;
      if (!name.value.trim()) { err.textContent = "Escribí el nombre."; err.hidden = false; name.focus(); return; }
      if (!/^\S+@\S+\.\S+$/.test(email.value.trim())) { err.textContent = "Revisá el email."; err.hidden = false; email.focus(); return; }
      const btn = document.getElementById("pf-submit");
      busy(btn, true, "Guardando…");
      try {
        const created = await api("/api/backoffice/users", { method: "POST", body: { fullName: name.value.trim(), email: email.value.trim().toLowerCase(), password: randomPassword(), role: "STUDENT" } });
        state.students.push(created);
        const phone = document.getElementById("pf-phone").value.trim();
        if (phone) { state.phones[created.id] = phone; saveLocal(); }
        toast(`${firstName(created.fullName)} quedó en tu lista`);
        location.hash = `#persona/${created.id}`;
      } catch (error) {
        err.textContent = humanError(error); err.hidden = false;
      } finally { busy(btn, false); }
    });
  }

  /* ---------------- Cómo te pagan ---------------- */
  async function renderMethods() {
    if (!state.settings) {
      setView(`<div class="card empty"><span class="spinner" aria-hidden="true"></span><p class="muted">Cargando…</p></div>`, "Cómo te pagan");
      try { state.settings = await api("/api/backoffice/payment-settings"); } catch (error) {
        return setView(`<div class="card empty"><div class="empty-icon">${icons.alert}</div><h1 style="font-size:1.5rem">No pudimos cargar tus formas de pago</h1><p class="muted">${esc(humanError(error))}</p><button class="btn btn-primary btn-lg" type="button" data-action="methods-retry">Intentar de nuevo</button></div>`, "Cómo te pagan");
      }
      if (parseHash().name !== "medios") return;
    }
    const s = state.settings;
    const mpUpToDate = mpConnected() && (s.mercadoPagoConnection === "OAUTH" || !s.mercadoPagoOAuthAvailable);
    const mpPill = mpUpToDate ? `<span class="pill pill-ok">Conectado</span>` : mpConnected() ? `<span class="pill pill-warn">Actualizá la conexión</span>` : `<span class="pill pill-info">Sin conectar</span>`;
    const html = `<a class="back" href="#inicio">${icons.arrowLeft} Inicio</a>
      <div class="view-head"><h1>Cómo te pagan</h1><p>Elegí una o varias. Tus clientes ven todas en la página de pago.</p></div>
      <div class="card">
        <div class="row-between"><h2 class="card-title" style="margin:0">Mercado Pago</h2>${mpPill}</div>
        <p class="muted" style="margin:8px 0 12px">Tarjeta, débito o saldo de Mercado Pago. <strong>El pago se marca solo</strong> y la plata va directo a tu cuenta. Permite cobros mensuales automáticos.</p>
        <a class="btn ${mpConnected() ? "" : "btn-mp"} btn-block" href="#mercadopago">${mpConnected() ? "Ver la conexión" : "Conectar Mercado Pago"}</a>
        ${s.platformFeeBps ? `<p class="hint" style="margin-top:10px">MiCuota cobra ${feePercent(s.platformFeeBps)} de cada pago por Mercado Pago. Los otros medios no tienen comisión.</p>` : ""}
      </div>
      <form class="card" id="methods-form" novalidate>
        <h2 class="card-title">Transferencia o depósito</h2>
        <div class="field"><label for="m-bank">Banco</label><input class="input" id="m-bank" list="uy-banks" value="${esc(s.transferBankName || "")}" autocomplete="off" placeholder="Ej: BROU" />
          <datalist id="uy-banks">${["BROU", "Itaú", "Santander", "BBVA", "Scotiabank", "HSBC", "Heritage", "Bandes", "BHU", "Citibank", "Prex", "Mi Dinero"].map((b) => `<option value="${b}"></option>`).join("")}</datalist></div>
        <div class="field"><label for="m-account">Número de cuenta</label><input class="input" id="m-account" value="${esc(s.transferAccountNumber || "")}" autocomplete="off" inputmode="text" /></div>
        <div class="field"><label for="m-holder">Titular</label><input class="input" id="m-holder" value="${esc(s.transferAccountHolder || "")}" autocomplete="off" placeholder="Como figura en el banco" /></div>
        ${s.bankPaymentsAvailable ? `<p class="hint" style="margin:-4px 0 12px">Con banco, cuenta y titular completos, tus clientes también pueden <strong>pagarte desde su banco</strong> en la página de pago, y se marca pagado solo.</p>` : ""}
        <div class="field"><label for="m-alias">Alias <span class="muted">(opcional)</span></label><input class="input" id="m-alias" value="${esc(s.transferAlias || "")}" autocomplete="off" /></div>
        <h2 class="card-title" style="margin-top:20px">Efectivo, giro u otras formas</h2>
        <div class="field"><label for="m-other">Cómo te pueden pagar</label>
          <textarea class="textarea input" id="m-other" rows="3" maxlength="500" placeholder="Ej: Efectivo en la consulta. Giro por Abitab o RedPagos a nombre de Ana Pérez, CI 1.234.567-8.">${esc(s.otherPaymentInstructions || "")}</textarea>
          <p class="hint">Escribilo como se lo dirías a un cliente.</p></div>
        <div class="notice" style="margin-bottom:14px">${icons.bell}<p class="small">Estos pagos no avisan solos: cuando te paguen, entrás al cobro y tocás <strong>«Ya me pagó»</strong>.</p></div>
        <button class="btn btn-primary btn-lg btn-block" type="submit" id="methods-submit">Guardar</button>
      </form>
      ${canCharge() ? `<a class="btn btn-ghost btn-block" href="#nuevo" style="margin-top:8px">${icons.plus} Crear un cobro</a>` : ""}`;
    setView(html, "Cómo te pagan");
    document.getElementById("methods-form").addEventListener("submit", async (e) => {
      e.preventDefault();
      const btn = document.getElementById("methods-submit");
      const val = (id) => document.getElementById(id).value.trim();
      busy(btn, true, "Guardando…");
      try {
        // Texto vacio borra el dato; los campos de Mercado Pago no se mandan y no se tocan.
        state.settings = await api("/api/backoffice/payment-settings", {
          method: "POST",
          body: { transferBankName: val("m-bank"), transferAccountNumber: val("m-account"), transferAccountHolder: val("m-holder"), transferAlias: val("m-alias"), otherPaymentInstructions: val("m-other") }
        });
        toast("Guardado");
        if (canCharge() && !state.payments.length) { location.hash = "#nuevo"; return; }
        renderMethods();
      } catch (error) { toast(humanError(error), true); } finally { busy(btn, false); }
    });
  }

  /* ---------------- Mercado Pago ---------------- */
  function feePercent(bps) {
    return `${(Number(bps || 0) / 100).toLocaleString("es-UY", { maximumFractionDigits: 2 })}%`;
  }

  // Al abrir la vista se vuelve a pedir la configuracion: la cargada al entrar puede ser vieja (por ejemplo,
  // de antes de que se activara el OAuth) y mostraria el formulario de token manual, que el backend rechaza.
  function refreshMercadoPagoSettings() {
    const before = state.settings;
    api("/api/backoffice/payment-settings", { slowAfter: 60000 })
      .then((settings) => {
        state.settings = settings;
        const changed = !before
          || before.mercadoPagoOAuthAvailable !== settings.mercadoPagoOAuthAvailable
          || before.mercadoPagoConnection !== settings.mercadoPagoConnection
          || before.platformFeeBps !== settings.platformFeeBps;
        if (changed && parseHash().name === "mercadopago") renderMercadoPago({ refresh: false });
      })
      .catch((error) => {
        if (state.settings || parseHash().name !== "mercadopago") return;
        setView(`<div class="card empty"><div class="empty-icon">${icons.alert}</div><h1 style="font-size:1.5rem">No pudimos cargar tu conexión con Mercado Pago</h1><p class="muted">${esc(humanError(error))}</p><button class="btn btn-primary btn-lg" type="button" data-action="mp-retry">Intentar de nuevo</button></div>`, "Mercado Pago");
      });
  }

  function renderMercadoPago({ refresh = true } = {}) {
    if (refresh) refreshMercadoPagoSettings();
    if (!state.settings) {
      return setView(`<div class="card empty"><span class="spinner" aria-hidden="true"></span><p class="muted">Cargando tu conexión con Mercado Pago…</p></div>`, "Mercado Pago");
    }
    const settings = state.settings;
    const connected = mpConnected();
    const oauthAvailable = Boolean(settings.mercadoPagoOAuthAvailable);
    const viaOAuth = settings.mercadoPagoConnection === "OAUTH";
    const feeNotice = `<div class="notice" style="margin-top:14px">${icons.lock}<p class="small">Cada pago va directo a tu cuenta de Mercado Pago. MiCuota cobra <strong>${feePercent(settings.platformFeeBps)} de cada pago</strong>: Mercado Pago lo descuenta solo, no tenés que transferirnos nada.</p></div>`;
    const country = store.get("micuota.mpCountry", "uy");
    const domain = { uy: "mercadopago.com.uy", ar: "mercadopago.com.ar" }[country] || "mercadopago.com.uy";
    let html = `<a class="back" href="#medios">${icons.arrowLeft} Cómo te pagan</a>
      <div class="view-head"><h1>Conectá tu Mercado Pago</h1><p>Así cada pago va directo a tu cuenta.</p></div>`;
    if (connected && (viaOAuth || !oauthAvailable)) {
      html += `<div class="notice notice-ok" style="margin-bottom:16px">${icons.checkCircle}<p><strong>Tu Mercado Pago está conectado.</strong> Ya podés cobrar.</p></div>
        <a class="btn btn-primary btn-lg btn-block" href="#nuevo" style="margin-bottom:24px">Crear un cobro</a>`;
      if (viaOAuth) {
        html += `${feeNotice}<button type="button" class="btn btn-ghost btn-block" style="margin-top:16px" data-action="mp-disconnect">Desconectar Mercado Pago</button>`;
        return setView(html, "Mercado Pago");
      }
      html += `<details class="card"><summary style="cursor:pointer;font-weight:700;min-height:44px;display:flex;align-items:center">Cambiar la clave de Mercado Pago</summary><div style="margin-top:14px">${tokenForm()}</div></details>`;
      setView(html, "Mercado Pago");
      bindTokenForm();
      return;
    }
    if (oauthAvailable) {
      if (connected) {
        html += `<div class="notice" style="margin-bottom:16px">${icons.alert}<p><strong>Actualizá tu conexión.</strong> Ahora te conectás con un clic, sin copiar claves. Tus cobros anteriores siguen igual.</p></div>`;
      }
      html += `<div class="card center"><p>Te llevamos a Mercado Pago, entrás con tu usuario de siempre y tocás <strong>Autorizar</strong>.</p><button type="button" class="btn btn-mp btn-lg btn-block" data-action="mp-oauth">Conectar con Mercado Pago</button></div>${feeNotice}`;
      return setView(html, "Mercado Pago");
    }
    html += `<div class="card">
        <div class="row-between" style="margin-bottom:14px"><h2 class="card-title" style="margin:0">Paso a paso</h2>
          <div class="chips" role="group" aria-label="Tu país">${[["uy", "Uruguay"], ["ar", "Argentina"]].map(([k, l]) => `<button type="button" class="chip" data-action="mp-country" data-country="${k}" aria-pressed="${country === k}">${l}</button>`).join("")}</div></div>
        <ol class="mp-steps">
          <li><strong>Abrí el panel de Mercado Pago</strong><p>Entrá con tu usuario y contraseña de Mercado Pago de siempre.</p>
            <a class="btn btn-mp btn-sm" style="margin-top:8px" href="https://www.${domain}/developers/panel/app" target="_blank" rel="noopener">Abrir Mercado Pago ${icons.arrowRight}</a></li>
          <li><strong>Tocá “Crear aplicación”</strong><p>Ponele de nombre <em>MiCuota</em>. Si te pregunta qué vas a integrar, elegí <em>Pagos online</em> y <em>Checkout Pro</em>. Si ya tenés una aplicación, abrila.</p></li>
          <li><strong>Entrá a “Credenciales de producción”</strong><p>Está en el menú de la izquierda. Puede que te pida completar datos de tu actividad: es un formulario corto.</p></li>
          <li><strong>Copiá el “Access Token”</strong><p>Es un texto largo que empieza con <code>APP_USR-</code>. Tocá el botoncito de copiar al lado.</p>
            <div class="mock" aria-hidden="true" style="margin-top:10px">
              <div class="mock-bar">Mercado Pago Developers</div>
              <div class="mock-crumbs">Tus integraciones › MiCuota › Credenciales de producción</div>
              <div class="mock-body">
                <div class="mock-field"><small>Public Key</small><code>APP_USR-1a2b3c4d-…</code></div>
                <div class="mock-field is-target"><small>Access Token</small><code>APP_USR-12345678901234-…</code> 📋<span class="mock-arrow">▲ Copiá este</span></div>
              </div>
            </div></li>
          <li><strong>Pegalo acá abajo y tocá “Conectar”</strong></li>
        </ol>
      </div>
      <div class="card">${tokenForm()}</div>
      <div class="notice" style="margin-top:14px">${icons.lock}<p class="small">Esta clave le permite a MiCuota crear links de cobro a tu nombre. <strong>La plata va siempre a tu cuenta de Mercado Pago.</strong> La guardamos protegida y podés desconectarla cuando quieras desde Mercado Pago.</p></div>`;
    setView(html, "Mercado Pago");
    bindTokenForm();
  }

  function tokenForm() {
    return `<form id="token-form" novalidate>
      <div class="field"><label for="mp-token">Access Token de Mercado Pago</label>
        <textarea class="textarea input" id="mp-token" rows="3" autocomplete="off" autocapitalize="none" spellcheck="false" placeholder="APP_USR-…" style="font-family:ui-monospace,monospace;font-size:1rem"></textarea>
        <p class="hint" id="mp-token-hint">Pegalo tal cual, completo.</p></div>
      <details style="margin-bottom:14px"><summary class="small" style="cursor:pointer;min-height:44px;display:flex;align-items:center">Opciones avanzadas</summary>
        <div class="field" style="margin-top:8px"><label for="mp-public">Public Key <span class="muted">(opcional)</span></label><input class="input" id="mp-public" autocomplete="off" spellcheck="false" value="${esc((state.settings && state.settings.mercadoPagoPublicKey) || "")}" /></div>
      </details>
      <p class="field-error" id="token-error" role="alert" hidden></p>
      <button class="btn btn-primary btn-lg btn-block" type="submit" id="token-submit">Conectar</button>
    </form>`;
  }

  function bindTokenForm() {
    const form = document.getElementById("token-form");
    if (!form) return;
    const input = document.getElementById("mp-token");
    const hint = document.getElementById("mp-token-hint");
    input.addEventListener("input", () => {
      const v = input.value.trim();
      if (!v) { hint.textContent = "Pegalo tal cual, completo."; hint.className = "hint"; return; }
      if (/^APP_USR-[\w-]{20,}$/.test(v)) { hint.textContent = "✓ Parece correcto."; hint.className = "hint token-ok"; }
      else if (/^TEST-/.test(v)) { hint.textContent = "Esta es una clave de prueba (TEST-): sirve para probar, pero no cobra de verdad."; hint.className = "hint"; }
      else { hint.textContent = "Debería empezar con APP_USR-. Revisá que copiaste el “Access Token” y no la “Public Key”."; hint.className = "hint"; }
    });
    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      const err = document.getElementById("token-error");
      const token = input.value.trim().replace(/\s+/g, "");
      err.hidden = true;
      if (!/^(APP_USR|TEST)-/.test(token)) { err.textContent = "Esa clave no parece un Access Token de Mercado Pago. Tiene que empezar con APP_USR-."; err.hidden = false; input.focus(); return; }
      const btn = document.getElementById("token-submit");
      busy(btn, true, "Conectando…");
      try {
        const cur = state.settings || {};
        state.settings = await api("/api/backoffice/payment-settings", {
          method: "POST",
          body: {
            mercadoPagoAccessToken: token,
            mercadoPagoPublicKey: document.getElementById("mp-public").value.trim() || null
          }
        });
        if (state.settings.mercadoPagoConfigured) {
          toast("¡Mercado Pago conectado!");
          location.hash = state.payments.length ? "#inicio" : "#nuevo";
        } else {
          err.textContent = "No se pudo guardar la clave. Probá de nuevo."; err.hidden = false;
        }
      } catch (error) {
        // Con el OAuth activo el backend ya no acepta tokens pegados: mostramos el boton de conectar.
        if (/Conectar con Mercado Pago/i.test(error.message || "")) { state.settings = null; renderMercadoPago(); return; }
        err.textContent = humanError(error); err.hidden = false;
      } finally { busy(btn, false); }
    });
  }

  /* ---------------- Mi cuenta ---------------- */
  function renderAccount() {
    const s = state.settings || {};
    const theme = store.get("micuota.theme", "auto");
    const big = store.get("micuota.bigText") === "1";
    const html = `<div class="view-head"><h1>Mi cuenta</h1><p>${esc(state.me.fullName)} · ${esc(state.me.email)}</p></div>
      <a class="card row-between" href="#medios" style="text-decoration:none;color:inherit">
        <span><strong class="card-title" style="display:block;margin:0">Cómo te pagan</strong>
        <span class="muted small">${esc([mpConnected() ? "Mercado Pago" : "", s.transferAccountNumber || s.transferAlias ? "Transferencia" : "", s.otherPaymentInstructions ? "Efectivo u otras" : ""].filter(Boolean).join(" · ") || "Todavía sin elegir")}</span></span>
        ${icons.arrowRight}</a>
      <div class="card">
        <h2 class="card-title">Para entrar desde otro celular</h2>
        <p class="muted small" style="margin:0">Entrás con tu email (${esc(state.me.email)}): te mandamos un código, sin contraseña.</p>
      </div>
      <div class="card">
        <h2 class="card-title">Pantalla</h2>
        <fieldset class="choice-group choice-grid-2" style="grid-template-columns:repeat(3,1fr)"><legend>Colores</legend>
          ${[["auto", "Automático"], ["light", "Claro"], ["dark", "Oscuro"]].map(([k, l]) => `<label class="choice"><input type="radio" name="theme" value="${k}" ${theme === k ? "checked" : ""} /><span class="choice-box center"><strong>${l}</strong></span></label>`).join("")}
        </fieldset>
        <label class="choice"><input type="checkbox" id="big-text" ${big ? "checked" : ""} /><span class="choice-box"><strong>Letra más grande</strong><span>Agranda todo el texto de la app</span></span></label>
      </div>
      <button type="button" class="btn btn-block btn-danger" data-action="logout" style="margin-top:16px">${icons.logout} Cerrar sesión</button>
      <p class="hint center" style="margin-top:16px">¿Dudas? Escribinos a <a href="mailto:hola@micuota.online">hola@micuota.online</a></p>`;
    setView(html, "Mi cuenta");
    view.querySelectorAll('input[name="theme"]').forEach((r) => r.addEventListener("change", () => {
      if (r.value === "auto") { store.remove("micuota.theme"); delete document.documentElement.dataset.theme; }
      else { store.set("micuota.theme", r.value); document.documentElement.dataset.theme = r.value; }
    }));
    document.getElementById("big-text").addEventListener("change", (e) => {
      document.documentElement.classList.toggle("big-text", e.target.checked);
      store.set("micuota.bigText", e.target.checked ? "1" : "0");
    });
  }

  /* ---------------- QR ---------------- */
  const qrDialog = document.getElementById("qr-dialog");
  function openQr(p) {
    const link = payLink(p.id);
    document.getElementById("qr-box").innerHTML = qrSvg(link, `Código QR para pagar ${p.description}`) || `<p class="muted">No se pudo generar el QR.</p>`;
    document.getElementById("qr-link").textContent = link;
    document.getElementById("qr-copy").textContent = `${p.description} · ${money(p.amount, p.currency)}. Tu cliente lo escanea con la cámara del celular y paga.`;
    if (typeof qrDialog.showModal === "function") qrDialog.showModal(); else qrDialog.setAttribute("open", "");
    markShared(p.id);
  }
  document.getElementById("qr-close").addEventListener("click", () => qrDialog.close ? qrDialog.close() : qrDialog.removeAttribute("open"));
  qrDialog.addEventListener("click", (e) => { if (e.target === qrDialog && qrDialog.close) qrDialog.close(); });

  /* ---------------- Acciones (delegacion) ---------------- */
  document.addEventListener("click", async (e) => {
    const el = e.target.closest("[data-action]");
    if (!el) return;
    const action = el.dataset.action;
    const p = el.dataset.id ? paymentById(el.dataset.id) : null;
    switch (action) {
      case "shared": if (p) markShared(p.id); break; // el link abre WhatsApp normalmente
      case "copy": {
        const ok = await copy(payLink(el.dataset.id));
        if (ok) markShared(el.dataset.id);
        toast(ok ? "Link copiado. Pegalo donde quieras." : "No se pudo copiar el link.", !ok);
        break;
      }
      case "copy-text": { const ok = await copy(el.dataset.text || ""); toast(ok ? "Copiado" : "No se pudo copiar", !ok); break; }
      case "qr": if (p) openQr(p); break;
      case "native-share":
        if (p && navigator.share) {
          try { await navigator.share({ title: p.description, text: messageFor(p, "first") }); markShared(p.id); } catch (_e) { /* cancelado */ }
        }
        break;
      case "filter":
        state.filter = el.dataset.filter;
        if (parseHash().name === "cobros") { e.preventDefault(); renderPayments(); }
        break;
      case "suggest": { const c = document.getElementById("concept"); c.value = el.dataset.value; c.focus(); break; }
      case "refresh":
        busy(el, true, "Actualizando…");
        await refreshPayments({ silent: false });
        if (document.body.contains(el)) busy(el, false);
        toast("Listo, está actualizado");
        break;
      case "hide-onboarding": store.set(`micuota.onboardingHidden.${slugKey()}`, "1"); renderHome(); break;
      case "mp-country": store.set("micuota.mpCountry", el.dataset.country); renderMercadoPago({ refresh: false }); break;
      case "mp-retry": renderMercadoPago(); break;
      case "methods-retry": renderMethods(); break;
      case "paid-open": {
        const form = document.getElementById("paid-form");
        if (form) { form.hidden = false; el.hidden = true; form.querySelector("input").focus(); }
        break;
      }
      case "recurring-stop":
        if (!window.confirm("¿Dejamos de mandarle este cobro todos los meses? Los cobros que ya se crearon quedan como están.")) break;
        busy(el, true, "Guardando…");
        try {
          await api(`/api/backoffice/recurring-charges/${encodeURIComponent(el.dataset.recurring)}/stop`, { method: "POST" });
          state.recurring = (state.recurring || []).filter((r) => String(r.id) !== String(el.dataset.recurring));
          toast("Listo, no se repite más");
          route();
        } catch (error) { toast(humanError(error), true); busy(el, false); }
        break;
      case "paid-undo":
        if (!p || !window.confirm("¿Lo volvemos a dejar como sin pagar?")) break;
        busy(el, true, "Deshaciendo…");
        try {
          const updated = await api(`/api/backoffice/payments/${encodeURIComponent(p.id)}/mark-unpaid`, { method: "POST" });
          replacePayment(updated);
          toast("Listo, quedó como sin pagar");
          renderPayment(updated.id);
        } catch (error) { toast(humanError(error), true); busy(el, false); }
        break;
      case "mp-oauth":
        busy(el, true, "Abriendo Mercado Pago…");
        try {
          const { url } = await api("/api/mercadopago/oauth/authorize");
          location.href = url;
        } catch (error) { toast(humanError(error), true); busy(el, false); }
        break;
      case "mp-disconnect":
        if (!window.confirm("Si desconectás Mercado Pago no vas a poder crear cobros hasta volver a conectarlo. ¿Seguimos?")) break;
        busy(el, true, "Desconectando…");
        try {
          await api("/api/mercadopago/oauth/disconnect", { method: "POST" });
          state.settings = await api("/api/backoffice/payment-settings");
          toast("Mercado Pago desconectado");
          renderMercadoPago({ refresh: false });
        } catch (error) { toast(humanError(error), true); busy(el, false); }
        break;
      case "logout": logout(); break;
      default: break;
    }
  });

  async function logout() {
    try { if (window.MicuotaSessionTelemetry) await window.MicuotaSessionTelemetry.logout(); } catch (_e) { /* no bloquea */ }
    session.clear();
    location.href = "/cuenta.html#entrar";
  }

  window.addEventListener("hashchange", route);
  // Al volver a la app (por ejemplo, despues de mandar un WhatsApp) refrescamos estados.
  document.addEventListener("visibilitychange", () => { if (document.visibilityState === "visible" && state.loaded) refreshPayments(); });
  setInterval(() => { if (document.visibilityState === "visible" && state.loaded) refreshPayments(); }, 60000);

  /* ---------------- Arranque ---------------- */
  (async function boot() {
    try {
      const ok = await loadAll();
      if (ok) route();
      // Vuelta del OAuth de Mercado Pago (?mp=conectado|cancelado|error).
      const mpResult = new URLSearchParams(location.search).get("mp");
      if (ok && mpResult) {
        history.replaceState(null, "", location.pathname + location.hash);
        if (mpResult === "conectado") toast("¡Mercado Pago conectado!");
        else if (mpResult === "cancelado") toast("No se conectó Mercado Pago: cancelaste la autorización.", true);
        else toast("No pudimos conectar Mercado Pago. Probá de nuevo.", true);
      }
    } catch (error) {
      if (MC.isSessionError(error) || (error.fromSession && (error.status === 400 || error.status === 401))) {
        session.clear();
        location.replace("/cuenta.html#entrar");
        return;
      }
      view.innerHTML = `<div class="card empty"><div class="empty-icon">${icons.alert}</div><h1 style="font-size:1.5rem">No pudimos cargar tus cobros</h1><p class="muted">${esc(humanError(error))}</p><button class="btn btn-primary btn-lg" type="button" id="boot-retry">Intentar de nuevo</button></div>`;
      document.getElementById("boot-retry").addEventListener("click", () => location.reload());
    }
  })();
})();
