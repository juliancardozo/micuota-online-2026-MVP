(function () {
  const API_BASE =
    window.__MICUOTA_API_BASE__ ||
    ((window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1")
      ? "http://localhost:8080"
      : window.location.origin);
  const QUICK_CHAT = true;
  const firstMessage =
    "Listo, vamos rapido. Si eres nuevo, escribe 'onboarding' y te guio paso a paso.";

  const onboardingState = {
    active: false,
    step: null,
    answers: {
      role: "",
      channel: "",
      frequency: "",
      objection: ""
    }
  };

  function buildResponse(parts) {
    if (QUICK_CHAT) {
      return [
        "Te conviene: " + parts.conviene,
        "Ahora: " + parts.ahora,
        "Texto para enviar: " + parts.texto
      ].join("\n\n");
    }
    return [
      "1. Que esta pasando\n" + parts.situacion,
      "2. Que te conviene mas\n" + parts.conviene,
      "3. Por que\n" + parts.porque,
      "4. Que puedes hacer ahora\n" + parts.ahora,
      "5. Texto sugerido\n" + parts.texto
    ].join("\n\n");
  }

  function startsOnboarding(raw) {
    const text = (raw || "").toLowerCase();
    return (
      text.includes("onboarding") ||
      text.includes("empezar") ||
      text.includes("arrancar") ||
      text.includes("soy nuevo") ||
      text.includes("nuevo usuario")
    );
  }

  function resetOnboarding() {
    onboardingState.active = false;
    onboardingState.step = null;
    onboardingState.answers = {
      role: "",
      channel: "",
      frequency: "",
      objection: ""
    };
  }

  function startOnboarding(roleByPath) {
    onboardingState.active = true;
    onboardingState.step = "role";
    onboardingState.answers = {
      role: roleByPath === "usuario" ? "" : roleByPath,
      channel: "",
      frequency: "",
      objection: ""
    };

    if (onboardingState.answers.role) {
      onboardingState.step = "channel";
      return "Perfecto. Para arrancar: como quieres cobrar primero? (link, qr o suscripcion)";
    }

    return "Buenisimo. Primer paso: cual es tu perfil? (profesor, administrador o alumno/paciente)";
  }

  function getOnboardingSummary() {
    const role = onboardingState.answers.role || "usuario";
    const channel = onboardingState.answers.channel || "link";
    const frequency = onboardingState.answers.frequency || "puntual";
    const objection = onboardingState.answers.objection || "sin objeciones fuertes";

    const isMonthly = frequency === "mensual";
    const conviene =
      channel === "suscripcion" || isMonthly
        ? "Suscripcion mensual con recordatorio automatico"
        : channel === "qr"
        ? "QR para cobro inmediato"
        : "Link de pago por WhatsApp";

    const ahora =
      "Crea tu primer cobro de prueba, compartelo con 3 personas y valida confirmaciones en el dashboard.";

    const texto =
      "Hola! Estamos empezando a usar MiCuota para ordenar los pagos. Te comparto este " +
      (channel === "qr" ? "QR" : "link") +
      " para que sea rapido y claro. Si te resulta comodo, lo dejamos como modalidad fija.";

    return buildResponse({
      situacion:
        "Onboarding " +
        role +
        " con frecuencia " +
        frequency +
        " y principal objecion: " +
        objection +
        ".",
      conviene,
      porque:
        "Empiezas simple, reduces friccion de adopcion y puedes evolucionar a un esquema mas automatico sin romper el habito actual.",
      ahora,
      texto
    });
  }

  function handleOnboardingAnswer(raw) {
    const text = (raw || "").toLowerCase();

    if (onboardingState.step === "role") {
      if (text.includes("prof")) onboardingState.answers.role = "profesor";
      else if (text.includes("admin") || text.includes("backoffice")) onboardingState.answers.role = "administrador";
      else if (text.includes("alumno") || text.includes("paciente")) onboardingState.answers.role = "alumno/paciente";
      else return "Te leo. Para perfilarte rapido: escribe profesor, administrador o alumno/paciente.";

      onboardingState.step = "channel";
      return "Genial. Como prefieres cobrar primero? (link, qr o suscripcion)";
    }

    if (onboardingState.step === "channel") {
      if (text.includes("qr")) onboardingState.answers.channel = "qr";
      else if (text.includes("suscrip") || text.includes("mensual")) onboardingState.answers.channel = "suscripcion";
      else if (text.includes("link") || text.includes("whatsapp")) onboardingState.answers.channel = "link";
      else return "Perfecto. Dime una opcion: link, qr o suscripcion.";

      onboardingState.step = "frequency";
      return "Bien. Tus cobros son mensuales o puntuales?";
    }

    if (onboardingState.step === "frequency") {
      if (text.includes("mens")) onboardingState.answers.frequency = "mensual";
      else if (text.includes("punt") || text.includes("unico") || text.includes("ocasional")) onboardingState.answers.frequency = "puntual";
      else return "Para cerrar esta parte: responde mensual o puntual.";

      onboardingState.step = "objection";
      return "Ultimo paso: cual es tu mayor objecion hoy? (desconfianza, tiempo, precio o 'ninguna')";
    }

    if (onboardingState.step === "objection") {
      onboardingState.answers.objection = text || "ninguna";
      const summary = getOnboardingSummary();
      resetOnboarding();
      return summary;
    }

    return "Si quieres, escribeme 'onboarding' y lo hacemos guiado en 4 pasos.";
  }

  function recommendByMessage(raw, contextRole) {
    const text = (raw || "").toLowerCase();
    const role =
      contextRole ||
      (text.match(/profesor|docente|entrenador|terapeuta|psicolog|musico|profesional|admin|alumno|paciente/) || [])[0] ||
      "usuario";

    if (text.includes("suscripcion") || text.includes("mensual") || text.includes("cuota")) {
      return buildResponse({
        situacion: "Tienes un servicio con frecuencia estable y quieres reducir seguimiento manual de pagos.",
        conviene: "Suscripcion para quienes pagan periodicamente. Si estas arrancando, puedes combinar con pago unico durante 2 semanas.",
        porque: "La suscripcion ordena mensualidades, mejora previsibilidad y evita perseguir comprobantes uno por uno.",
        ahora: "Define monto fijo, comunica fecha de cobro y crea el primer plan para un grupo pequeno.",
        texto:
          "Hola, desde este mes vamos a ordenar los pagos con MiCuota.online para que el proceso sea mas simple y claro. Te compartire un enlace de suscripcion para la cuota mensual. Si prefieres, en esta primera etapa tambien podemos usar pago unico mientras te adaptas."
      });
    }

    if (text.includes("qr") || text.includes("presencial") || text.includes("cara a cara")) {
      return buildResponse({
        situacion: "Necesitas cobrar en el momento, con la persona presente y sin pasos complejos.",
        conviene: "QR para cobro presencial rapido.",
        porque: "Reduce friccion en el momento de pago y evita pasar datos manualmente.",
        ahora: "Genera un cobro unico, muestra el QR y valida que el alumno/paciente vea la confirmacion.",
        texto:
          "Para que sea mas simple, hoy te comparto un QR y puedes pagar en el momento desde tu celular. Asi evitamos transferencias manuales y queda todo ordenado."
      });
    }

    if (text.includes("link") || text.includes("whatsapp") || text.includes("remoto")) {
      return buildResponse({
        situacion: "Cobras en forma remota y quieres enviar una opcion clara por mensaje.",
        conviene: "Link de pago para envio por WhatsApp o email.",
        porque: "Es directo, facil de entender y no obliga a una llamada o paso presencial.",
        ahora: "Crea el cobro, copia el link y enviarlo con una explicacion corta del concepto y fecha.",
        texto:
          "Te comparto este link de pago para que puedas abonarlo de forma simple cuando te quede comodo. Cualquier duda, te acompano paso a paso."
      });
    }

    if (text.includes("miedo") || text.includes("desconf") || text.includes("seguro") || text.includes("obligatorio")) {
      return buildResponse({
        situacion: "Hay una barrera emocional normal: temor al cambio o dudas de confianza.",
        conviene: "Adopcion gradual: iniciar con pago unico y luego ofrecer suscripcion opcional.",
        porque: "La adopcion mejora cuando primero se prueba una experiencia simple sin imponer cambios bruscos.",
        ahora: "Haz un piloto con 3 a 5 personas y recoge dudas para ajustar el mensaje.",
        texto:
          "Entiendo la duda, es totalmente normal. Podemos empezar de forma gradual con un pago puntual por link, y cuando te sientas comodo evaluamos modalidad mensual. La idea es simplificarte, no complicarte."
      });
    }

    if (role.includes("alumno") || role.includes("paciente")) {
      return buildResponse({
        situacion: "Quieres pagar facil y entender claramente que estas pagando.",
        conviene: "Link para pagos puntuales o suscripcion si tu servicio es mensual.",
        porque: "Ambas opciones te muestran concepto y estado para evitar confusiones.",
        ahora: "Pide el enlace de pago y verifica concepto, monto y frecuencia antes de confirmar.",
        texto:
          "Para ti sera simple: recibes un link, revisas el detalle y pagas en pocos pasos. Si es mensual, te explico antes de activar cualquier suscripcion."
      });
    }

    return buildResponse({
      situacion: "Estas buscando ordenar los cobros, pero sin que se sienta frio ni complicado.",
      conviene: "Anda tranqui: arranca con link de pago (pago unico) y despues, si fluye, pasas a suscripcion.",
      porque: "Porque asi nadie se abruma, vos pruebas rapido y el cambio entra natural.",
      ahora: "Elegi un solo caso esta semana, mandalo por WhatsApp con mensaje corto y fijate como responde.",
      texto:
        "Hola! Te paso un link de pago super simple para esta vez. Asi lo hacemos facil para todos. Si vemos que funciona bien, mas adelante lo pasamos a mensual y listo."
    });
  }

  function detectRoleByPath() {
    const path = (window.location.pathname || "").toLowerCase();
    if (path.includes("profesor")) return "profesor";
    if (path.includes("alumno") || path.includes("pago")) return "alumno";
    if (path.includes("backoffice")) return "administrador";
    return "usuario";
  }

  function addMessage(container, text, type) {
    const msg = document.createElement("div");
    msg.className = "mc-chat-msg " + type;
    msg.textContent = text;
    container.appendChild(msg);
    container.scrollTop = container.scrollHeight;
  }

  async function askBackend(question, roleByPath) {
    const token = localStorage.getItem("micuota.authToken");
    const headers = { "Content-Type": "application/json" };
    if (token) {
      headers["X-Auth-Token"] = token;
    }

    const response = await fetch(`${API_BASE}/api/chatbot/adoption`, {
      method: "POST",
      headers,
      body: JSON.stringify({
        message: question,
        page: window.location.pathname,
        roleHint: roleByPath,
        quickMode: QUICK_CHAT
      })
    });

    const data = await response.json();
    if (!response.ok) {
      throw new Error((data && data.error) || "No se pudo consultar al asistente");
    }
    return data.answer;
  }

  function init() {
    const roleByPath = detectRoleByPath();

    const toggle = document.createElement("button");
    toggle.className = "mc-chatbot-toggle";
    toggle.type = "button";
    toggle.title = "Abrir asistente MiCuota";
    toggle.textContent = "MC";

    const panel = document.createElement("section");
    panel.className = "mc-chatbot";
    panel.hidden = true;
    panel.innerHTML =
      '<header class="mc-chatbot-head"><strong>Asistente MiCuota</strong><button type="button" id="mc-chat-clear">Limpiar</button></header>' +
      '<div class="mc-chatbot-body" id="mc-chat-body"></div>' +
      '<form class="mc-chatbot-form" id="mc-chat-form"><input id="mc-chat-input" placeholder="Escribe tu duda..." autocomplete="off" /><button type="submit">Enviar</button></form>';

    document.body.appendChild(toggle);
    document.body.appendChild(panel);

    const body = panel.querySelector("#mc-chat-body");
    const form = panel.querySelector("#mc-chat-form");
    const input = panel.querySelector("#mc-chat-input");
    const clearBtn = panel.querySelector("#mc-chat-clear");

    const chips = document.createElement("div");
    chips.className = "mc-chatbot-chips";
    const chipItems = [
      "Quiero onboarding",
      "Soy profesor",
      "Soy alumno",
      "Quiero cobrar mensual",
      "Tengo objeciones",
      "Necesito mensaje para WhatsApp"
    ];
    chipItems.forEach((label) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.textContent = label;
      btn.addEventListener("click", async function () {
        addMessage(body, label, "user");
        if (startsOnboarding(label)) {
          addMessage(body, startOnboarding(roleByPath), "bot");
          return;
        }
        if (onboardingState.active) {
          addMessage(body, handleOnboardingAnswer(label), "bot");
          return;
        }
        try {
          const backendAnswer = await askBackend(label, roleByPath);
          addMessage(body, backendAnswer, "bot");
        } catch (_error) {
          addMessage(body, recommendByMessage(label, roleByPath), "bot");
        }
      });
      chips.appendChild(btn);
    });

    body.appendChild(chips);
    addMessage(body, firstMessage, "bot");

    toggle.addEventListener("click", function () {
      panel.hidden = !panel.hidden;
      if (!panel.hidden) input.focus();
    });

    clearBtn.addEventListener("click", function () {
      body.innerHTML = "";
      resetOnboarding();
      body.appendChild(chips);
      addMessage(body, firstMessage, "bot");
    });

    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      const question = input.value.trim();
      if (!question) return;
      addMessage(body, question, "user");
      if (startsOnboarding(question)) {
        addMessage(body, startOnboarding(roleByPath), "bot");
        input.value = "";
        return;
      }
      if (onboardingState.active) {
        addMessage(body, handleOnboardingAnswer(question), "bot");
        input.value = "";
        return;
      }
      try {
        const backendAnswer = await askBackend(question, roleByPath);
        addMessage(body, backendAnswer, "bot");
      } catch (_error) {
        addMessage(body, recommendByMessage(question, roleByPath), "bot");
      }
      input.value = "";
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
