/* MiCuota - utilidades compartidas del frontend (sin dependencias).
   Requiere micuota-config.js antes. Expone window.MC. */
(function () {
  "use strict";

  const TOKEN_KEY = "micuota.authToken";
  const SLUG_KEY = "micuota.tenantSlug";
  const PREF_SLUG_KEY = "micuota.preferredTenantSlug";

  /* ---------------- Almacenamiento seguro ---------------- */
  const store = {
    get(key, fallback = null) {
      try { const v = localStorage.getItem(key); return v === null ? fallback : v; } catch (_e) { return fallback; }
    },
    set(key, value) { try { localStorage.setItem(key, value); } catch (_e) { /* modo privado */ } },
    remove(key) { try { localStorage.removeItem(key); } catch (_e) { /* modo privado */ } },
    getJson(key, fallback) {
      try { return JSON.parse(localStorage.getItem(key) || "null") ?? fallback; } catch (_e) { return fallback; }
    },
    setJson(key, value) { store.set(key, JSON.stringify(value)); }
  };

  /* ---------------- Sesion ---------------- */
  const session = {
    token() { return store.get(TOKEN_KEY); },
    slug() { return store.get(PREF_SLUG_KEY) || store.get(SLUG_KEY) || ""; },
    save(result) {
      store.set(TOKEN_KEY, result.token);
      if (result.tenantSlug) {
        store.set(SLUG_KEY, result.tenantSlug);
        store.set(PREF_SLUG_KEY, result.tenantSlug);
      }
    },
    clear() { store.remove(TOKEN_KEY); },
    /** Toma ?token= de la URL (compatibilidad con links viejos) y lo saca de la barra de direcciones. */
    adoptTokenFromUrl() {
      const params = new URLSearchParams(location.search);
      const t = params.get("token");
      if (t) {
        store.set(TOKEN_KEY, t);
        params.delete("token");
        const q = params.toString();
        history.replaceState(null, "", location.pathname + (q ? "?" + q : "") + location.hash);
      }
    },
    homeFor(role) {
      switch (role) {
        case "TEACHER": return "/profesor.html";
        case "TENANT_ADMIN": return "/backoffice.html";
        case "STUDENT": return "/alumno.html";
        case "ADMIN": return "/admin.html";
        default: return "/profesor.html";
      }
    }
  };

  /* ---------------- API con aviso de "servidor despertando" ---------------- */
  let pendingSlow = 0;
  let wakeEl = null;
  function showWake() {
    pendingSlow += 1;
    if (wakeEl) return;
    wakeEl = document.createElement("div");
    wakeEl.className = "wake-banner";
    wakeEl.setAttribute("role", "status");
    wakeEl.innerHTML = '<span class="spinner" aria-hidden="true"></span><div><strong>Un momento, estamos encendiendo MiCuota</strong>La primera carga del día puede tardar hasta tres minutos. No cierres la página.</div>';
    document.body.appendChild(wakeEl);
  }
  function hideWake() {
    pendingSlow = Math.max(0, pendingSlow - 1);
    if (pendingSlow === 0 && wakeEl) { wakeEl.remove(); wakeEl = null; }
  }

  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

  // Render free tier tarda ~2,5 min en arrancar en frio: lo despertamos apenas abre la pagina,
  // asi arranca mientras la persona completa el formulario.
  if (window.MicuotaConfig && window.MicuotaConfig.apiBase) {
    fetch(`${window.MicuotaConfig.apiBase}/actuator/health`, { mode: "no-cors" }).catch(() => {});
  }

  async function api(path, opts = {}) {
    const { method = "GET", body, auth = true, slowAfter = 3500, retries = 1 } = opts;
    const headers = {};
    if (body !== undefined) headers["Content-Type"] = "application/json";
    if (auth) {
      const t = session.token();
      if (t) headers["X-Auth-Token"] = t;
    }
    let slowShown = false;
    const timer = setTimeout(() => { slowShown = true; showWake(); }, slowAfter);
    try {
      for (let attempt = 0; ; attempt += 1) {
        const controller = typeof AbortController !== "undefined" ? new AbortController() : null;
        const abortTimer = controller ? setTimeout(() => controller.abort(), 240000) : null;
        try {
          return await window.MicuotaApi.request(path, {
            method,
            headers,
            body: body !== undefined ? JSON.stringify(body) : undefined,
            signal: controller ? controller.signal : undefined
          });
        } catch (error) {
          // Render free tier: mientras despierta puede cortar la conexion o devolver 502/503.
          const retryable = error.network || error.status === 502 || error.status === 503 || error.status === 504;
          if (retryable && attempt < retries && method === "GET") { await sleep(2500); continue; }
          throw error;
        } finally {
          if (abortTimer) clearTimeout(abortTimer);
        }
      }
    } finally {
      clearTimeout(timer);
      if (slowShown) hideWake();
    }
  }

  function isSessionError(error) {
    return /sesion invalida|sesion expirada|token de sesion|token no encontrado|token invalido|X-Auth-Token/i.test(error && error.message || "");
  }

  /** Traduce mensajes tecnicos del backend a lenguaje humano. */
  function humanError(error) {
    const msg = (error && error.message) || "";
    const rules = [
      [/credenciales invalidas/i, "El email o la contraseña no coinciden. Revisalos e intentá de nuevo."],
      [/tenant no encontrado|slug de tenant invalido/i, "No encontramos una cuenta con ese código. Lo tenés en el email de bienvenida."],
      [/alta de tenants esta restringida/i, "Por ahora activamos las cuentas nuevas a mano."],
      [/access token de mercado pago|conectar su access token/i, "Primero conectá tu cuenta de Mercado Pago para poder cobrar."],
      [/ya existe un usuario con ese email/i, "Ya tenés a alguien con ese email. Elegilo de la lista."],
      [/payload invalido/i, "Revisá los datos: falta algo o hay un campo con formato incorrecto."],
      [/operacion no encontrada/i, "No encontramos este cobro."],
      [/no se pudo generar el link de cobro/i, "Mercado Pago no pudo crear el link. Revisá que tu cuenta de Mercado Pago esté bien conectada."],
      [/access token|unauthorized|invalid_token|401/i, "Mercado Pago rechazó la conexión. Revisá que hayas copiado bien la clave."],
      [/no pudimos conectar|failed to fetch|networkerror|aborted/i, "No pudimos conectarnos. Revisá tu internet e intentá de nuevo."],
      [/permitida solo para|permitida para/i, "Tu usuario no tiene permiso para esto."],
      [/sesion|token/i, "Tu sesión se cerró. Volvé a entrar."]
    ];
    for (const [re, text] of rules) if (re.test(msg)) return text;
    if (error && error.status >= 500) return "Algo falló de nuestro lado. Intentá de nuevo en un minuto.";
    return msg || "Algo salió mal. Intentá de nuevo.";
  }

  /* ---------------- Formato ---------------- */
  function money(amount, currency = "UYU") {
    const n = Number(amount || 0);
    const cur = (currency || "UYU").toUpperCase();
    const decimals = Number.isInteger(n) ? 0 : 2;
    const symbols = { UYU: "$", ARS: "$", USD: "US$" };
    const formatted = new Intl.NumberFormat("es-UY", { minimumFractionDigits: decimals, maximumFractionDigits: 2 }).format(n);
    return `${symbols[cur] || cur} ${formatted}`;
  }

  function esc(value) {
    return String(value ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  }

  function initials(name) {
    const parts = String(name || "?").trim().split(/\s+/).filter(Boolean);
    return ((parts[0] || "?")[0] + (parts.length > 1 ? parts[parts.length - 1][0] : "")).toUpperCase();
  }

  function firstName(name) {
    return String(name || "").trim().split(/\s+/)[0] || "";
  }

  const DAY = 86400000;
  function relativeDays(date) {
    if (!date) return "";
    const d = new Date(date);
    const today = new Date(); today.setHours(0, 0, 0, 0);
    const that = new Date(d); that.setHours(0, 0, 0, 0);
    const diff = Math.round((that - today) / DAY);
    if (diff === 0) return "hoy";
    if (diff === -1) return "ayer";
    if (diff === 1) return "mañana";
    if (diff < 0) return `hace ${-diff} días`;
    return `en ${diff} días`;
  }

  function shortDate(date) {
    if (!date) return "";
    return new Intl.DateTimeFormat("es-UY", { day: "numeric", month: "short" }).format(new Date(date));
  }

  function monthName(date = new Date()) {
    return new Intl.DateTimeFormat("es-UY", { month: "long" }).format(date);
  }

  /* ---------------- Estado de un cobro, en humano ----------------
     key: paid | pending | processing | late | failed */
  function statusOf(op) {
    const status = op && op.status;
    if (status === "SUCCESS") return { key: "paid", label: "Pagado", pill: "pill-ok", owes: false };
    const overdue = op && op.dueAt && new Date(op.dueAt).getTime() < Date.now();
    if (status === "FAILURE") return { key: "failed", label: "No se pudo pagar", pill: "pill-late", owes: true };
    if (overdue) return { key: "late", label: "Vencido", pill: "pill-late", owes: true };
    if (status === "PENDING") return { key: "processing", label: "Pago en proceso", pill: "pill-info", owes: true };
    return { key: "pending", label: "Sin pagar", pill: "pill-warn", owes: true };
  }

  function flowLabel(flowType) {
    return flowType === "SUBSCRIPTION" ? "Cuota mensual" : "Pago único";
  }

  /* ---------------- Links, WhatsApp, portapapeles ---------------- */
  function payLink(operationId) {
    return `${location.origin}/pago.html?operationId=${encodeURIComponent(operationId)}`;
  }

  /** Normaliza telefonos de Uruguay/Argentina al formato internacional que pide wa.me. */
  function normalizePhone(raw) {
    let d = String(raw || "").replace(/\D/g, "");
    if (!d) return "";
    if (d.startsWith("00")) d = d.slice(2);
    if (/^09\d{7}$/.test(d)) return "598" + d.slice(1);   // 09X XXX XXX (UY celular)
    if (/^9\d{7}$/.test(d)) return "598" + d;             // 9X XXX XXX
    return d;                                             // ya viene con codigo de pais
  }

  function waLink(text, phone) {
    const p = normalizePhone(phone);
    return `https://wa.me/${p}?text=${encodeURIComponent(text)}`;
  }

  async function copy(text) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch (_e) {
      const ta = document.createElement("textarea");
      ta.value = text; ta.setAttribute("readonly", ""); ta.style.position = "fixed"; ta.style.opacity = "0";
      document.body.appendChild(ta); ta.select();
      let ok = false;
      try { ok = document.execCommand("copy"); } catch (_e2) { ok = false; }
      ta.remove();
      return ok;
    }
  }

  async function share({ title, text, url }) {
    if (navigator.share) {
      try { await navigator.share({ title, text, url }); return true; } catch (_e) { return false; }
    }
    return false;
  }

  /* ---------------- QR (libreria local vendor/qrcode.js) ---------------- */
  function qrSvg(text, label = "Código QR") {
    if (typeof window.qrcode !== "function") return "";
    const qr = window.qrcode(0, "M");
    qr.addData(text);
    qr.make();
    const count = qr.getModuleCount();
    const margin = 2;
    const size = count + margin * 2;
    let path = "";
    for (let r = 0; r < count; r += 1) {
      for (let c = 0; c < count; c += 1) {
        if (qr.isDark(r, c)) path += `M${c + margin} ${r + margin}h1v1h-1z`;
      }
    }
    return `<svg viewBox="0 0 ${size} ${size}" role="img" aria-label="${esc(label)}" shape-rendering="crispEdges"><rect width="${size}" height="${size}" fill="#fff"/><path d="${path}" fill="#000"/></svg>`;
  }

  /* ---------------- Toast ---------------- */
  let toastRegion = null;
  function toast(message, isError = false) {
    if (!toastRegion) {
      toastRegion = document.createElement("div");
      toastRegion.className = "toast-region";
      toastRegion.setAttribute("aria-live", "polite");
      document.body.appendChild(toastRegion);
    }
    const t = document.createElement("div");
    t.className = "toast" + (isError ? " is-error" : "");
    t.setAttribute("role", isError ? "alert" : "status");
    t.textContent = message;
    toastRegion.appendChild(t);
    setTimeout(() => t.remove(), isError ? 6000 : 3200);
  }

  /** Pone un boton en estado "cargando" y lo restaura. */
  function busy(button, isBusy, busyLabel) {
    if (!button) return;
    if (isBusy) {
      button.dataset.label = button.innerHTML;
      button.disabled = true;
      button.setAttribute("aria-busy", "true");
      button.innerHTML = `<span class="spinner" aria-hidden="true"></span> ${esc(busyLabel || "Un momento...")}`;
    } else {
      button.disabled = false;
      button.removeAttribute("aria-busy");
      if (button.dataset.label) button.innerHTML = button.dataset.label;
    }
  }

  /* ---------------- Iconos (SVG en linea, heredan currentColor) ---------------- */
  const s = (d, extra = "") => `<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${extra}>${d}</svg>`;
  const icons = {
    whatsapp: `<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M12.04 2C6.58 2 2.13 6.45 2.13 11.91c0 1.75.46 3.45 1.32 4.95L2.05 22l5.25-1.38a9.9 9.9 0 0 0 4.74 1.21h.01c5.46 0 9.9-4.45 9.9-9.91 0-2.65-1.03-5.14-2.9-7.01A9.82 9.82 0 0 0 12.04 2Zm0 18.15h-.01a8.2 8.2 0 0 1-4.19-1.15l-.3-.18-3.12.82.83-3.04-.2-.31a8.2 8.2 0 0 1-1.26-4.38c0-4.54 3.7-8.23 8.25-8.23 2.2 0 4.27.86 5.83 2.42a8.18 8.18 0 0 1 2.41 5.83c0 4.54-3.7 8.22-8.24 8.22Zm4.52-6.16c-.25-.12-1.47-.72-1.69-.81-.23-.08-.39-.12-.56.13-.16.24-.64.8-.78.97-.15.17-.29.19-.54.06-.25-.12-1.05-.39-1.99-1.23-.74-.66-1.23-1.47-1.38-1.72-.14-.25-.01-.38.11-.5.11-.11.25-.29.37-.43.12-.15.16-.25.25-.41.08-.17.04-.31-.02-.43-.06-.13-.56-1.35-.77-1.84-.2-.48-.41-.42-.56-.43h-.48c-.17 0-.43.06-.66.31-.22.25-.86.85-.86 2.07 0 1.22.89 2.4 1.01 2.56.12.17 1.75 2.67 4.23 3.74.59.26 1.05.41 1.41.52.59.19 1.13.16 1.56.1.48-.07 1.47-.6 1.67-1.18.21-.58.21-1.07.15-1.18-.06-.1-.23-.16-.48-.29Z"/></svg>`,
    copy: s('<rect x="9" y="9" width="12" height="12" rx="2"/><path d="M5 15V5a2 2 0 0 1 2-2h10"/>'),
    qr: s('<rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="7" rx="1"/><rect x="3" y="14" width="7" height="7" rx="1"/><path d="M14 14h3v3h-3zM20 14v.01M14 20h.01M17 20h4v-3"/>'),
    plus: s('<path d="M12 5v14M5 12h14"/>'),
    home: s('<path d="M3 11l9-8 9 8"/><path d="M5 10v10h14V10"/>'),
    list: s('<path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/>'),
    people: s('<circle cx="9" cy="8" r="4"/><path d="M2 21a7 7 0 0 1 14 0"/><path d="M17 4a4 4 0 0 1 0 8M22 21a6 6 0 0 0-4-5.6"/>'),
    user: s('<circle cx="12" cy="8" r="4"/><path d="M4 21a8 8 0 0 1 16 0"/>'),
    check: s('<path d="M20 6 9 17l-5-5"/>'),
    checkCircle: s('<circle cx="12" cy="12" r="10"/><path d="m8 12 3 3 5-6"/>'),
    clock: s('<circle cx="12" cy="12" r="10"/><path d="M12 6v6l4 2"/>'),
    alert: s('<circle cx="12" cy="12" r="10"/><path d="M12 8v4M12 16h.01"/>'),
    x: s('<path d="M18 6 6 18M6 6l12 12"/>'),
    lock: s('<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>'),
    link: s('<path d="M10 13a5 5 0 0 0 7.07 0l3-3a5 5 0 0 0-7.07-7.07l-1.5 1.5"/><path d="M14 11a5 5 0 0 0-7.07 0l-3 3a5 5 0 0 0 7.07 7.07l1.5-1.5"/>'),
    arrowLeft: s('<path d="M19 12H5M12 19l-7-7 7-7"/>'),
    arrowRight: s('<path d="M5 12h14M12 5l7 7-7 7"/>'),
    bell: s('<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>'),
    wallet: s('<path d="M20 7H5a2 2 0 0 1 0-4h13v4"/><path d="M3 5v14a2 2 0 0 0 2 2h15V7"/><path d="M16 14h.01"/>'),
    refresh: s('<path d="M21 12a9 9 0 1 1-3-6.7L21 8"/><path d="M21 3v5h-5"/>'),
    share: s('<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>'),
    printer: s('<path d="M6 9V2h12v7"/><rect x="6" y="14" width="12" height="8"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/>'),
    eye: s('<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>'),
    logout: s('<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>'),
    calendar: s('<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>'),
    spark: s('<path d="M12 3v3M12 18v3M3 12h3M18 12h3M5.6 5.6l2.1 2.1M16.3 16.3l2.1 2.1M5.6 18.4l2.1-2.1M16.3 7.7l2.1-2.1"/>')
  };

  window.MC = {
    store, session, api, isSessionError, humanError,
    money, esc, initials, firstName, relativeDays, shortDate, monthName,
    statusOf, flowLabel, payLink, normalizePhone, waLink, copy, share, qrSvg,
    toast, busy, icons, sleep
  };
})();
