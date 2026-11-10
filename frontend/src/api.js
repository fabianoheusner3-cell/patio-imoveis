export const API = import.meta.env.VITE_API_URL || "/api";
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}
export async function api(path, options = {}) {
  const { body, ...rest } = options;
  const headers = new Headers(rest.headers);
  const token = sessionStorage.getItem("patio-token");
  if (token && path !== "/auth/login")
    headers.set("Authorization", `Bearer ${token}`);
  if (body && !(body instanceof FormData))
    headers.set("Content-Type", "application/json");
  let res;
  try {
    res = await fetch(API + path, {
      ...rest,
      headers,
      body:
        body instanceof FormData
          ? body
          : body
            ? JSON.stringify(body)
            : undefined,
    });
  } catch {
    throw new ApiError(
      "Não foi possível conectar ao servidor. Confira se a API está ligada.",
      0,
    );
  }
  if (!res.ok) {
    let dados = {};
    try {
      dados = await res.json();
    } catch {}
    if (res.status === 401 && path != "/auth/login") {
      sessionStorage.removeItem("patio-token");
      window.dispatchEvent(new Event("sessao-expirada"));
    }
    const mensagens = {
      401:
        path === "/auth/login"
          ? "Email ou senha inválidos."
          : "Sua sessão expirou. Entre novamente.",
      403: "Acesso negado. Seu perfil não tem permissão para esta ação.",
      404: "O item solicitado não foi encontrado.",
      409: dados.mensagem || "Esta operação não pode ser realizada agora.",
      400: dados.mensagem || "Confira os campos informados.",
      413: "A imagem ultrapassa o limite de 10 MB.",
      503: dados.mensagem || "Serviço temporariamente indisponível.",
      502: dados.mensagem || "O serviço de email está indisponível.",
    };
    const erroServidor =
      path === "/auth/login"
        ? "Erro no servidor ao tentar entrar. Seus dados podem estar certos; tente novamente em instantes."
        : "Erro no servidor. Tente novamente em instantes.";
    throw new ApiError(
      mensagens[res.status] ||
        (res.status >= 500
          ? erroServidor
          : "Não foi possível concluir a operação. Tente novamente."),
      res.status,
    );
  }
  if (options.download) return res.blob();
  if (res.status === 204) return null;
  const text = await res.text();
  return text ? JSON.parse(text) : null;
}
export const money = (value) =>
  new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" }).format(
    Number(value || 0),
  );
export const date = (value) =>
  value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
export const label = (value) =>
  ({
    PENDENTE: "Pendente",
    ACEITA: "Aceita",
    EM_ESPERA: "Em espera",
    RECUSADA: "Recusada",
    CANCELADA: "Cancelada",
    AGUARDANDO_ASSINATURA: "Aguardando assinatura",
    ASSINADO: "Assinado",
    CANCELADO: "Cancelado",
    USER: "Cliente",
    ADMIN: "Administrador",
    VENDA: "Venda",
    ALUGUEL: "Aluguel",
  })[value] || value;
export const normalize = (value) =>
  String(value || "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase();
export const available = (item) => normalize(item.status) === "disponivel";
