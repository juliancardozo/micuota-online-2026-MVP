const API_BASE = "http://localhost:8080";
const TEACHER_ID = 1;

function createQrMarkup(url) {
  const qr = `https://api.qrserver.com/v1/create-qr-code/?size=180x180&data=${encodeURIComponent(url)}`;
  return `
    <p><strong>Link:</strong> <a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a></p>
    <p><strong>QR generado:</strong></p>
    <img src="${qr}" alt="QR de pago" width="180" height="180" loading="lazy" />
  `;
}

function renderCallbackLinks(operationId) {
  return `
    <p><strong>Callbacks:</strong></p>
    <p>
      <a href="${API_BASE}/api/callbacks/success?operationId=${operationId}" target="_blank" rel="noopener noreferrer">success</a>
      |
      <a href="${API_BASE}/api/callbacks/pending?operationId=${operationId}" target="_blank" rel="noopener noreferrer">pending</a>
      |
      <a href="${API_BASE}/api/callbacks/failure?operationId=${operationId}" target="_blank" rel="noopener noreferrer">failure</a>
    </p>
  `;
}

async function postJson(path, payload) {
  const response = await fetch(`${API_BASE}${path}`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json"
    },
    body: JSON.stringify(payload)
  });

  const data = await response.json();
  if (!response.ok) {
    throw new Error(data.error || "Error al procesar la solicitud");
  }
  return data;
}

function setupSinglePaymentForm() {
  const form = document.getElementById("single-payment-form");
  const result = document.getElementById("single-result");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const payload = {
      teacherId: TEACHER_ID,
      provider: String(data.get("provider") || "PROMETEO"),
      description: String(data.get("title") || ""),
      amount: Number(data.get("amount") || "0"),
      currency: String(data.get("currency") || "UYU"),
      payerEmail: "student+demo@micuota.online"
    };

    result.innerHTML = "<p>Creando pago unico...</p>";
    try {
      const operation = await postJson("/api/payments/one-time", payload);
      result.innerHTML = `
        <p><strong>Pago unico creado:</strong> #${operation.id} - ${operation.provider} - ${operation.currency} ${operation.amount}</p>
        ${createQrMarkup(operation.checkoutUrl)}
        ${renderCallbackLinks(operation.id)}
      `;
    } catch (error) {
      result.innerHTML = `<p><strong>Error:</strong> ${error.message}</p>`;
    }
  });
}

function setupSubscriptionForm() {
  const form = document.getElementById("subscription-form");
  const result = document.getElementById("subscription-result");

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const data = new FormData(form);
    const payload = {
      teacherId: TEACHER_ID,
      provider: String(data.get("provider") || "PROMETEO"),
      description: String(data.get("reason") || ""),
      amount: Number(data.get("amount") || "0"),
      currency: "UYU",
      payerEmail: "student+demo@micuota.online"
    };

    const frequency = String(data.get("frequency") || "1");
    result.innerHTML = "<p>Creando suscripcion...</p>";
    try {
      const operation = await postJson("/api/payments/subscriptions", payload);
      result.innerHTML = `
        <p><strong>Suscripcion creada:</strong> #${operation.id} - ${operation.provider} - cada ${frequency} mes(es) - ${operation.currency} ${operation.amount}</p>
        ${createQrMarkup(operation.checkoutUrl)}
        ${renderCallbackLinks(operation.id)}
      `;
    } catch (error) {
      result.innerHTML = `<p><strong>Error:</strong> ${error.message}</p>`;
    }
  });
}

async function refreshOperations() {
  const result = document.getElementById("ops-result");
  result.innerHTML = "<p>Cargando operaciones...</p>";

  try {
    const response = await fetch(`${API_BASE}/api/payments/teacher/${TEACHER_ID}`);
    const data = await response.json();
    if (!response.ok) {
      throw new Error(data.error || "No se pudieron cargar operaciones");
    }

    if (!data.length) {
      result.innerHTML = "<p>No hay operaciones creadas todavia.</p>";
      return;
    }

    result.innerHTML = data
      .map((op) => {
        return `<p><strong>#${op.id}</strong> ${op.flowType} | ${op.provider} | ${op.currency} ${op.amount} | estado: ${op.status}</p>`;
      })
      .join("");
  } catch (error) {
    result.innerHTML = `<p><strong>Error:</strong> ${error.message}</p>`;
  }
}

function setupOperationsRefresh() {
  const button = document.getElementById("refresh-ops");
  button.addEventListener("click", () => {
    refreshOperations();
  });
}

setupSinglePaymentForm();
setupSubscriptionForm();
setupOperationsRefresh();
