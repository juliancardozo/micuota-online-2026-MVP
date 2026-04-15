(function () {
  const firstMessage =
    "Estoy listo para ayudarte a introducir MiCuota.online de forma clara, humana y practica. Cuentame si estas pensando como profesor, profesional, alumno, paciente o administrador, y te ayudo a elegir la mejor forma de usarlo.";

  function buildResponse(parts) {
    return [
      "1. Que esta pasando\n" + parts.situacion,
      "2. Que te conviene mas\n" + parts.conviene,
      "3. Por que\n" + parts.porque,
      "4. Que puedes hacer ahora\n" + parts.ahora,
      "5. Texto sugerido\n" + parts.texto
    ].join("\n\n");
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
      situacion: "Estas evaluando como adoptar MiCuota.online sin perder cercania con tus alumnos o pacientes.",
      conviene: "Comenzar con pago unico por link y pasar a suscripcion solo en servicios estables.",
      porque: "Ese enfoque reduce resistencia, permite aprender rapido y mejora orden de cobros.",
      ahora: "Define un caso piloto de esta semana: un servicio, un mensaje claro y un primer cobro medible.",
      texto:
        "Hola, para simplificar pagos vamos a empezar con MiCuota.online en un formato muy simple. Te compartire un link claro para cada cobro, y segun como te resulte evaluamos modalidad mensual."
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
      btn.addEventListener("click", function () {
        addMessage(body, label, "user");
        addMessage(body, recommendByMessage(label, roleByPath), "bot");
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
      body.appendChild(chips);
      addMessage(body, firstMessage, "bot");
    });

    form.addEventListener("submit", function (event) {
      event.preventDefault();
      const question = input.value.trim();
      if (!question) return;
      addMessage(body, question, "user");
      addMessage(body, recommendByMessage(question, roleByPath), "bot");
      input.value = "";
    });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
