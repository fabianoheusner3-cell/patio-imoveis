export const API = import.meta.env.VITE_API_URL || "/api";
export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.status = status;
  }
}
// silencioso: true → um 401 só apaga o token, sem mandar para o login (usado ao abrir o site).
export async function api(path, options = {}) {
  const { body, silencioso = false, ...rest } = options;
  const headers = new Headers(rest.headers);
  const token = sessionStorage.getItem("patio-token");
  // Login e cadastro são públicos: um token antigo guardado no navegador não pode atrapalhar.
  const publica = path === "/auth/login" || path.startsWith("/public/");
  if (token && !publica) headers.set("Authorization", `Bearer ${token}`);
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
    if (res.status === 401 && !publica) {
      sessionStorage.removeItem("patio-token");
      if (!silencioso) window.dispatchEvent(new Event("sessao-expirada"));
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
// Valor resumido para os cartões dos painéis: R$ 550 mil, R$ 1,2 mi.
export const moneyShort = (value) =>
  new Intl.NumberFormat("pt-BR", {
    style: "currency",
    currency: "BRL",
    notation: "compact",
    maximumFractionDigits: 1,
  }).format(Number(value || 0));
export const date = (value) =>
  value
    ? new Intl.DateTimeFormat("pt-BR", {
        dateStyle: "short",
        timeStyle: "short",
      }).format(new Date(value))
    : "—";
// Datas sem horário (LocalDate do Java, "2026-11-24"): meio-dia evita trocar o dia pelo fuso.
export const day = (value) =>
  value
    ? new Intl.DateTimeFormat("pt-BR").format(new Date(value + "T12:00:00"))
    : "—";
export const label = (value) =>
  ({
    PENDENTE: "Pendente",
    ACEITA: "Aceita",
    EM_ESPERA: "Em espera",
    RECUSADA: "Recusada",
    CANCELADA: "Cancelada",
    EM_PREPARACAO: "Em preparação",
    AGUARDANDO_APROVACAO: "Aguardando aprovação",
    AGUARDANDO_ASSINATURA: "Aguardando assinatura",
    ASSINADO: "Assinado",
    CANCELADO: "Cancelado",
    USER: "Cliente",
    CORRETOR: "Corretor",
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
