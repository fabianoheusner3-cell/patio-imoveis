import { test, expect } from "@playwright/test";
test("vitrine, login incorreto e rota privada sem sessão", async ({ page }) => {
  const erros = [];
  page.on("pageerror", (e) => erros.push(e.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Explore os imóveis" }),
  ).toBeVisible();
  await expect(page.getByText("Carregando…", { exact: true })).toHaveCount(0);
  await page.goto("/admin/usuarios");
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel("Email", { exact: true }).fill("invalido@example.com");
  await page.getByLabel("Senha", { exact: true }).fill("senhaerrada");
  await page.getByRole("button", { name: /Entrar na minha conta/ }).click();
  await expect(page.getByRole("alert")).toContainText(
    "Email ou senha inválidos",
  );
  expect(erros).toEqual([]);
});
test("admin: painel, criação e edição de bairro, confirmação de exclusão", async ({
  page,
}) => {
  const erros = [];
  page.on("pageerror", (e) => erros.push(e.message));
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("admin@example.com");
  await page.getByLabel("Senha", { exact: true }).fill("AdminTeste123!");
  await page.getByRole("button", { name: /Entrar na minha conta/ }).click();
  await expect(page).toHaveURL(/\/admin$/);
  await expect(
    page.getByRole("heading", { name: "Bem-vindo à sua gestão" }),
  ).toBeVisible();
  await page.getByRole("link", { name: "Bairros", exact: true }).click();
  await page.getByRole("button", { name: "Novo cadastro" }).click();
  await page.getByLabel("Nome", { exact: true }).fill("Bairro teste navegador");
  await page.getByRole("button", { name: "Salvar", exact: true }).click();
  let row = page.getByRole("row").filter({ hasText: "Bairro teste navegador" });
  await expect(row).toBeVisible();
  await row.getByRole("button", { name: "Editar", exact: true }).click();
  await page
    .getByLabel("Nome", { exact: true })
    .fill("Bairro teste atualizado");
  await page.getByRole("button", { name: "Salvar", exact: true }).click();
  row = page.getByRole("row").filter({ hasText: "Bairro teste atualizado" });
  await expect(row).toBeVisible();
  page.once("dialog", (d) => d.dismiss());
  await row.getByRole("button", { name: "Excluir", exact: true }).click();
  await expect(row).toBeVisible();
  page.once("dialog", (d) => d.accept());
  await row.getByRole("button", { name: "Excluir", exact: true }).click();
  await expect(row).toHaveCount(0);
  await page.goto("/cliente");
  await expect(
    page.getByRole("heading", { name: "Acesso negado" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Sair", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(erros).toEqual([]);
});
test("layout de celular sem rolagem horizontal e menu funcional", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Explore os imóveis" }),
  ).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  await page.getByRole("button", { name: "Abrir menu" }).click();
  await page.getByRole("link", { name: "Minha conta" }).click();
  await expect(page).toHaveURL(/\/login$/);
});
test("cliente: favoritos, filtros, paginação, detalhes e proteção de perfil", async ({
  page,
}) => {
  await page.goto("/");
  await expect(page.locator(".property-card")).toHaveCount(6);
  await page.getByRole("button", { name: "2", exact: true }).click();
  await expect(page.locator(".property-card")).toHaveCount(2);
  await page
    .getByRole("combobox", { name: "Finalidade", exact: true })
    .selectOption("ALUGUEL");
  await expect(page.locator(".property-card")).toHaveCount(4);
  await page
    .getByRole("textbox", { name: "Buscar imóvel" })
    .fill("sem-imovel-com-este-nome");
  await expect(
    page.getByRole("heading", { name: "Sem resultados por aqui" }),
  ).toBeVisible();
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill("cliente@example.com");
  await page.getByLabel("Senha", { exact: true }).fill("Cliente123!");
  await page.getByRole("button", { name: /Entrar na minha conta/ }).click();
  await expect(page).toHaveURL(/\/cliente$/);
  await expect(
    page.getByRole("link", { name: "Gestão de usuários", exact: true }),
  ).toHaveCount(0);
  await page.goto("/");
  await page
    .getByRole("button", { name: "Adicionar aos favoritos" })
    .first()
    .click();
  await page.goto("/cliente/favoritos");
  await expect(page.locator(".property-card")).toHaveCount(1);
  await page.getByRole("button", { name: "Remover dos favoritos" }).click();
  await expect(page.locator(".property-card")).toHaveCount(0);
  await page.goto("/");
  await page.locator(".property-card h3").first().click();
  await expect(
    page.getByRole("button", { name: "Fazer oferta" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "Tenho interesse" }).click();
  await expect(page.getByLabel("Assunto")).toHaveValue(/Interesse no imóvel/);
  await expect(page.getByLabel("Mensagem", { exact: true })).toHaveValue(
    /bairro/,
  );
  await page.getByRole("button", { name: "Fechar", exact: true }).click();
  await page.goto("/admin/usuarios");
  await expect(
    page.getByRole("heading", { name: "Acesso negado" }),
  ).toBeVisible();
});
