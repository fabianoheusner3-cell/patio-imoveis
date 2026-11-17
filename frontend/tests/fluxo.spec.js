import { test, expect } from "@playwright/test";

// Checkpoint da Aula 3, pela interface:
// cliente envia oferta → corretor aceita e prepara o contrato → administrador aprova → cliente assina.
// Usa os dados de demonstração (node scripts/dados-demo.mjs).
const CONTAS = {
  cliente: {
    email: "cliente@example.com",
    senha: "Cliente123!",
    nome: "Cliente Demonstração",
  },
  corretor: { email: "corretor@example.com", senha: "Corretor123!" },
  admin: { email: "admin@example.com", senha: "AdminTeste123!" },
};
const IMOVEL = "Casa térrea com amplo pátio";

async function entrar(page, quem) {
  await page.goto("/login");
  await page.getByLabel("Email", { exact: true }).fill(CONTAS[quem].email);
  await page.getByLabel("Senha", { exact: true }).fill(CONTAS[quem].senha);
  await page.getByRole("button", { name: /Entrar na minha conta/ }).click();
}
async function sair(page) {
  await page.getByRole("button", { name: "Sair", exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
}

// Devolve o imóvel do teste à vitrine e cancela ofertas abertas do cliente,
// para o teste poder ser repetido e não mudar a contagem da vitrine nos outros testes.
async function limpar(request) {
  const cabecalho = async (quem) => {
    const r = await request.post("/api/auth/login", {
      data: { email: CONTAS[quem].email, senha: CONTAS[quem].senha },
    });
    return { Authorization: "Bearer " + (await r.json()).token };
  };
  const admin = await cabecalho("admin");
  for (const c of await (await request.get("/api/contratos", { headers: admin })).json())
    if (c.oferta.imovel.titulo === IMOVEL && c.status !== "CANCELADO")
      await request.post(`/api/contratos/${c.id}/cancelar`, { headers: admin });
  const cliente = await cabecalho("cliente");
  for (const o of await (await request.get("/api/ofertas", { headers: cliente })).json())
    if (o.imovel.titulo === IMOVEL && ["PENDENTE", "EM_ESPERA"].includes(o.status))
      await request.post(`/api/ofertas/${o.id}/cancelar`, { headers: cliente });
}
test.beforeEach(async ({ request }) => limpar(request));
test.afterEach(async ({ request }) => limpar(request));

test("checkpoint: cliente oferta, corretor prepara, administrador aprova, cliente assina", async ({
  page,
}) => {
  test.setTimeout(120_000);
  const erros = [];
  page.on("pageerror", (e) => erros.push(e.message));
  page.on("dialog", (d) => d.accept());
  const mensagem = "Proposta do teste de fluxo " + Date.now();

  // 1. Cliente envia a oferta pelo detalhe do imóvel
  await entrar(page, "cliente");
  await expect(page).toHaveURL(/\/cliente$/);
  await page.goto("/");
  await page.getByRole("textbox", { name: "Buscar imóvel" }).fill(IMOVEL);
  await page.locator(".property-card h3", { hasText: IMOVEL }).click();
  await page.getByRole("button", { name: "Fazer oferta" }).click();
  await page.getByLabel("Valor proposto (R$)").fill("550000");
  await page.getByLabel("Mensagem", { exact: true }).fill(mensagem);
  await page.getByRole("button", { name: "Enviar oferta" }).click();
  await expect(page.getByRole("status")).toContainText("Oferta enviada");
  // Regra de negócio: só uma oferta em aberto por imóvel
  await page.getByRole("button", { name: "Fazer oferta" }).click();
  await page.getByLabel("Valor proposto (R$)").fill("560000");
  await page.getByLabel("Mensagem", { exact: true }).fill("Segunda proposta");
  await page.getByRole("button", { name: "Enviar oferta" }).click();
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText(
    "já tem uma oferta em aberto",
  );
  await page.getByRole("button", { name: "Fechar", exact: true }).click();
  await page.goto("/cliente/ofertas");
  await expect(page.locator(".list-card", { hasText: mensagem })).toContainText(
    "Pendente",
  );
  await sair(page);

  // 2. Corretor aceita a oferta e prepara o contrato
  await entrar(page, "corretor");
  await expect(page).toHaveURL(/\/corretor$/);
  await expect(page.getByText("Ofertas esperando um corretor")).toBeVisible();
  await page.getByRole("link", { name: "Ofertas", exact: true }).click();
  const oferta = page.locator(".list-card", { hasText: mensagem });
  await expect(oferta).toContainText("Pendente");
  await oferta.getByRole("button", { name: "Aceitar" }).click();
  await expect(page).toHaveURL(/\/corretor\/contratos$/);
  const novo = page
    .locator(".list-card", { hasText: IMOVEL })
    .filter({ hasText: "Em preparação" });
  await expect(novo).toHaveCount(1);
  const numero = (await novo.locator(".eyebrow").first().textContent()).match(
    /#(\d+)/,
  )[1];
  const cartao = () =>
    page.locator(".list-card", { hasText: `CONTRATO #${numero} ·` });
  await cartao().getByRole("button", { name: "Preparar contrato" }).click();
  const preparo = page.getByRole("dialog");
  await expect(preparo.getByLabel("Valor final acordado (R$)")).toHaveValue(
    "550000",
  );
  await preparo.getByLabel("Valor final acordado (R$)").fill("555000");
  await preparo.getByRole("button", { name: "Enviar para aprovação" }).click();
  await expect(cartao()).toContainText("Aguardando aprovação");
  await expect(cartao()).toContainText("Vez da administração");
  await sair(page);

  // 3. Administrador revisa e aprova
  await entrar(page, "admin");
  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByText(`Contrato #${numero} · ${IMOVEL}`)).toBeVisible();
  await page.getByRole("link", { name: /Revisar contratos/ }).click();
  await expect(cartao()).toContainText("R$ 555.000,00");
  await cartao().getByRole("button", { name: "Aprovar" }).click();
  await expect(page.getByRole("status")).toContainText("Contrato aprovado");
  await page.getByRole("link", { name: "Contratos", exact: true }).click();
  await expect(cartao()).toContainText("Aguardando assinatura");
  await sair(page);

  // 4. Cliente assina (o nome precisa ser o do cadastro)
  await entrar(page, "cliente");
  await expect(page).toHaveURL(/\/cliente$/);
  const passo = page.locator(".todo-list li", {
    hasText: `Contrato #${numero} ·`,
  });
  await expect(passo).toContainText("Pronto para você assinar");
  await passo.getByRole("link", { name: "Assinar" }).click();
  await cartao().getByRole("button", { name: "Assinar contrato" }).click();
  const assinatura = page.getByRole("dialog");
  const nome = assinatura.getByLabel("Seu nome completo, conforme o cadastro");
  await nome.fill("Outra Pessoa");
  await assinatura.getByLabel("Li e concordo com o contrato.").check();
  await assinatura.getByRole("button", { name: "Confirmar assinatura" }).click();
  await expect(assinatura.getByRole("alert")).toContainText("nome completo");
  await nome.fill(CONTAS.cliente.nome);
  await assinatura.getByRole("button", { name: "Confirmar assinatura" }).click();
  await expect(page.getByRole("status")).toContainText("Contrato assinado");
  await page.getByRole("link", { name: "Contratos", exact: true }).click();
  await expect(cartao()).toContainText("Assinado");

  // O imóvel vendido sai da vitrine
  await page.goto("/");
  await page.getByRole("textbox", { name: "Buscar imóvel" }).fill(IMOVEL);
  await expect(
    page.getByRole("heading", { name: "Sem resultados por aqui" }),
  ).toBeVisible();
  expect(erros).toEqual([]);
});
