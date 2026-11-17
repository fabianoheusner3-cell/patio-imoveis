import { test, expect } from "@playwright/test";

// Estados de carregamento, erro, vazio e sessão expirada.
// As falhas são simuladas interceptando as chamadas da API (page.route).
async function entrar(page, email, senha) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(email);
  await page.getByLabel("Senha", { exact: true }).fill(senha);
  await page.getByRole("button", { name: /Entrar na minha conta/ }).click();
}

test("painel do corretor: erro do servidor, carregando e nova tentativa", async ({
  page,
}) => {
  await entrar(page, "corretor@example.com", "Corretor123!");
  await expect(page).toHaveURL(/\/corretor$/);
  await expect(page.getByText("Ofertas na fila")).toBeVisible();

  // Perfil errado: o corretor não entra nas áreas do administrador e do cliente
  await page.goto("/admin/usuarios");
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toBeVisible();
  await page.goto("/cliente");
  await expect(page.getByRole("heading", { name: "Acesso negado" })).toBeVisible();

  // Erro 500: mensagem clara e botão para tentar de novo
  await page.route("**/api/painel/corretor", (route) =>
    route.fulfill({ status: 500, contentType: "application/json", body: "{}" }),
  );
  await page.goto("/corretor");
  await expect(page.getByRole("alert")).toContainText("Erro no servidor");

  // Carregando: a resposta demora e a tela avisa
  await page.unroute("**/api/painel/corretor");
  let liberar;
  const espera = new Promise((r) => (liberar = r));
  await page.route("**/api/painel/corretor", async (route) => {
    await espera;
    await route.continue();
  });
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByText("Carregando…", { exact: true })).toBeVisible();
  liberar();
  await expect(page.getByText("Ofertas na fila")).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("lista do cliente: sem conexão, nova tentativa e busca sem resultados", async ({
  page,
}) => {
  await entrar(page, "cliente@example.com", "Cliente123!");
  await expect(page).toHaveURL(/\/cliente$/);
  await page.route("**/api/ofertas/ofertas-page**", (route) => route.abort());
  await page.getByRole("link", { name: "Minhas ofertas", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Não foi possível conectar ao servidor",
  );
  await page.unroute("**/api/ofertas/ofertas-page**");
  await page.getByRole("button", { name: "Tentar novamente" }).click();
  await expect(page.getByRole("alert")).toHaveCount(0);
  await page
    .getByRole("textbox", { name: "Buscar", exact: true })
    .fill("nenhum-imovel-com-este-nome");
  await expect(
    page.getByRole("heading", { name: "Nenhuma oferta por aqui" }),
  ).toBeVisible();
});

test("cadastro: sessão antiga no navegador não atrapalha e email incompleto é barrado", async ({
  page,
}) => {
  await page.goto("/");
  // Token de uma execução anterior do backend (a chave do JWT muda a cada execução)
  await page.evaluate(() =>
    sessionStorage.setItem(
      "patio-token",
      "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIn0.token-antigo",
    ),
  );
  await page.goto("/cadastro");
  await expect(page.getByRole("heading", { name: "Vamos começar?" })).toBeVisible();
  await expect(page).toHaveURL(/\/cadastro$/);
  const email = page.getByLabel("Email", { exact: true });
  await page.getByLabel("Nome", { exact: true }).fill("Visitante Teste");
  await email.fill("visitante@teste");
  await page.getByLabel("Senha", { exact: true }).fill("Visitante123!");
  await page.getByLabel("Confirmar senha", { exact: true }).fill("Visitante123!");
  await page.getByRole("button", { name: /Criar minha conta/ }).click();
  // O navegador barra o email sem ponto no domínio, como o servidor faria
  expect(await email.evaluate((campo) => campo.validity.patternMismatch)).toBe(true);
  await expect(page).toHaveURL(/\/cadastro$/);
  await email.fill(`visitante${Date.now()}@example.com`);
  await page.getByRole("button", { name: /Criar minha conta/ }).click();
  await expect(page).toHaveURL(/\/cliente$/);
  await expect(page.getByRole("status")).toContainText("Conta criada");
});

test("sessão expirada (401): apaga o token e volta para o login", async ({
  page,
}) => {
  await entrar(page, "admin@example.com", "AdminTeste123!");
  await expect(page).toHaveURL(/\/admin$/);
  await page.route("**/api/contratos/contratos-page**", (route) =>
    route.fulfill({ status: 401, contentType: "application/json", body: "{}" }),
  );
  await page.getByRole("link", { name: "Contratos", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByRole("status")).toContainText("Sua sessão expirou");
  expect(
    await page.evaluate(() => sessionStorage.getItem("patio-token")),
  ).toBeNull();
});
