# Pátio — imobiliária com React + Vite e API Spring Boot

Projeto da disciplina **Programação Web IV** — Fabiano Heusner.

O frontend React está em `frontend/`. Ele consome a API REST Java existente, ampliada com ofertas, contratos e favoritos. O frontend antigo da atividade JWT foi mantido em `src/main/resources/static/` como referência; a nova interface é acessada pela porta **5173**.

## Executar

Requisitos: **JDK 25 ou superior**, Maven e **Node 22.12 ou superior** (testado com Node 24). Não é necessário instalar MySQL: o backend usa H2 persistente em `data/tipos-imoveis.mv.db`. As novas tabelas são criadas pelo Hibernate ao iniciar.

Abra dois terminais PowerShell dentro da pasta extraída do projeto (a que contém `pom.xml`).

**Terminal 1 — backend:**

```powershell
$env:ADMIN_EMAIL = 'admin@example.com'
$env:ADMIN_PASSWORD = 'AdminTeste123!'
mvn '-Dmaven.repo.local=../.m2/repository' spring-boot:run
```

O primeiro administrador é criado se o banco estiver sem usuários. Em um banco já existente, use as credenciais que já foram cadastradas. Os valores acima são exemplos para desenvolvimento. `JWT_SECRET` pode definir uma chave estável com no mínimo 32 bytes; sem ela, a chave é aleatória por execução e os tokens são invalidados ao reiniciar.

**Terminal 2 — frontend:**

```powershell
cd frontend
npm.cmd ci
npm.cmd run dev
```

Abra **http://localhost:5173**. Usar `npm.cmd` evita o bloqueio de `npm.ps1` em máquinas Windows com execução de scripts desativada. O Vite encaminha `/api` para `http://127.0.0.1:8080`, removendo esse prefixo. A API continua disponível diretamente na porta 8080.

### Dados de demonstração opcionais

Com o backend ligado, execute na pasta que contém `pom.xml`:

```powershell
node scripts/dados-demo.mjs
```

O script adiciona oito imóveis ilustrativos, tipos, bairros, fotos e o cliente `cliente@example.com`, senha `Cliente123!`. Ele pode ser repetido sem duplicar os mesmos títulos/contas e **não apaga os dados existentes**. Não execute em um ambiente de produção. As imagens são ilustrativas, não representam os imóveis descritos. Os arquivos estão em `frontend/public/demo/`; fontes: Unsplash, fotos `1600585154340-be6161a56a0c`, `1600047509807-ba8f99d2cdde`, `1600596542815-ffad4c1539a9` e `1564013799919-ab600027ffc6`.

## O que foi implementado

| Área | Funcionalidades |
|---|---|
| Pública | Vitrine em cards com fotos, tipo, bairro, cidade e preços; detalhes e galeria; consulta por bairro, finalidade, faixa de preço e busca textual; filtros combinados; ordenação por preço; paginação de seis itens; formulário de interesse preenchido com dados do imóvel |
| Login | JWT, validações, mensagens amigáveis, redirecionamento por perfil e restauração da sessão via `/auth/me` |
| Cliente | Fazer ofertas em imóveis disponíveis, consultar apenas as próprias ofertas, adicionar/listar/remover favoritos, listar contratos, baixar PDF, assinar contrato aprovado e enviado, sair |
| Administrador | CRUD de imóveis, bairros, tipos e fotos; busca/edição/perfil/exclusão de usuários; proteção contra excluir a própria conta; ofertas com filtro e aceite/recusa; contratos com aprovação, envio, recusa, cancelamento e PDF |
| Painel | Totais de imóveis disponíveis, ofertas pendentes e contratos aguardando assinatura; gráficos de imóveis por bairro/finalidade e ofertas por situação |
| Interface | Layout responsivo, menus por perfil, rotas protegidas, carregamento/erro/sem resultados, confirmações antes de excluir e validações de formulário |

A vitrine usa `GET /imoveis/imoveis-page?vitrine=true` com `Pageable`: paginação (`page`, `size`), ordenação (`sort=preco,asc|desc`) e filtros (`bairro`, `busca`, `finalidade`, `min`, `max`) são feitos no servidor e só aparecem imóveis disponíveis. Sem `vitrine=true`, a rota continua como no exemplo da aula.

## Regras de negócio e integridade

- Somente imóveis com status disponível recebem ofertas. Tipo, valor positivo e mensagem são validados no servidor; o preço cadastrado deve permitir aluguel ou venda conforme a oferta.
- Um cliente não pode criar duas ofertas pendentes para o mesmo imóvel. Clientes diferentes podem criar propostas para ele.
- Aceitar uma oferta reserva o imóvel, cria o contrato e coloca as demais ofertas pendentes em espera. O aceite é exclusivo de ADMIN.
- Assinar exige titularidade, nome completo, confirmação de concordância, contrato aprovado e email de envio concluído. A assinatura marca o imóvel como alugado ou vendido e recusa as outras ofertas abertas.
- Recusar/cancelar contrato cancela a oferta vinculada e devolve o imóvel ao status disponível. As propostas que estavam em espera permanecem em espera; o administrador pode aceitá-las depois de liberar o imóvel. Novas ofertas também são permitidas.
- O bloqueio pessimista do imóvel serializa decisões concorrentes. Há um teste com dois aceites simultâneos: apenas um é concluído.
- Não é possível editar manualmente imóvel/status com contrato ativo. Imóveis com histórico de ofertas não são excluídos para preservar a negociação; a API retorna 409. Exclusão de outros registros vinculados também recebe 409.
- Favoritos possuem restrição única por cliente e imóvel. Os IDs de usuário são derivados do JWT, não de campos enviados pelo navegador.
- Clientes não conseguem ler ou assinar contratos de terceiros. Senhas são armazenadas com BCrypt e não são serializadas pela API.

A assinatura é uma confirmação autenticada para demonstração acadêmica; não usa certificado digital. O PDF é gerado pelo PDFBox e fica acessível pelo caminho autenticado gravado no contrato. O conteúdo acompanha a situação e as datas do contrato.

## Emails

**Configuração escolhida: Gmail.** Veja [GMAIL.md](GMAIL.md). Na pasta do projeto, execute `.\iniciar-gmail.cmd`: o script solicita a senha de app com entrada oculta e inicia a API com o perfil Gmail. O endereço autorizado como remetente e destinatário é `fabianoheusner3@gmail.com`. Nenhuma senha é gravada no código ou na entrega.

O envio usa o `EmailService` existente. Configure o destinatário da imobiliária e as credenciais **antes de iniciar o backend**. Exemplo SMTP:

```powershell
$env:IMOBILIARIA_EMAIL = 'endereco-real-da-imobiliaria@seu-dominio.com'
$env:MAIL_TRANSPORT = 'smtp'
$env:MAILGUN_SMTP_HOST = 'seu-servidor-smtp'
$env:MAILGUN_SMTP_USERNAME = 'seu-usuario-smtp'
$env:MAILGUN_SMTP_PASSWORD = 'sua-senha-smtp'
$env:MAILGUN_FROM = 'remetente-autorizado@seu-dominio.com'
$env:FRONTEND_URL = 'http://localhost:5173'
```

As variáveis SMTP acima são as utilizadas em `application.properties`; consulte esse arquivo para ajustar porta e TLS ao seu provedor. Também é possível manter `MAIL_TRANSPORT=api` com `MAILGUN_API_KEY` e `MAILGUN_DOMAIN`.

O formulário público envia o interesse **à imobiliária**, incluindo código, tipo, bairro e valores. O envio de contrato notifica **o email do cliente** com o link da área de contratos, onde ele entra para baixar o PDF. Aprovar e enviar são ações separadas: se o email falhar, o contrato continua aprovado, sem marcar envio nem liberar assinatura. O administrador pode tentar novamente. As falhas de serviço são informadas na tela, sem expor credenciais.

**Nenhuma credencial real de email faz parte da entrega.** O envio externo real precisa ser validado com as credenciais do seu provedor; nos testes automatizados o serviço de email é simulado.

## Rotas novas

| Método / rota | Acesso |
|---|---|
| GET `/auth/me` | Autenticado |
| POST `/contato/{imovelId}` | Público |
| GET `/ofertas` | Cliente vê as próprias; ADMIN vê todas; filtro `?status=PENDENTE` |
| POST `/ofertas` | USER; JSON: `imovelId`, `tipo` (`ALUGUEL`/`VENDA`), `valorProposto`, `mensagem` |
| POST `/ofertas/{id}/aceitar` ou `/recusar` | ADMIN |
| GET `/contratos` e `/contratos/{id}` | Titular ou ADMIN |
| GET `/contratos/{id}/pdf` | Titular ou ADMIN |
| POST `/contratos/{id}/aprovar`, `/enviar`, `/recusar`, `/cancelar` | ADMIN |
| POST `/contratos/{id}/assinar` | Titular USER; JSON: `nome`, `concordo: true` |
| GET `/favoritos` | Autenticado; retorna apenas os próprios |
| POST/DELETE `/favoritos/{imovelId}` | USER |
| GET `/painel` | ADMIN |

Os CRUDs existentes continuam em `/imoveis`, `/bairros`, `/tipos-imoveis`, `/fotos` e `/usuarios`. A edição/exclusão de bairros agora também está implementada. As leituras de tipos inexistentes passaram a retornar 404.

## Compatibilidade com a tabela do cronograma

As rotas e os campos usados pelo professor também são aceitos. As rotas originais continuam valendo e são as usadas pelo React.

| Cronograma | Equivale a | Detalhes |
|---|---|---|
| POST `/public/login` com `email` e `password` | POST `/auth/login` com `email` e `senha` | Os dois nomes de campo são aceitos nas duas rotas |
| `/users` (GET, POST, PUT, DELETE) | `/usuarios` | Somente ADMIN. O perfil pode vir em `tipo`, `role` ou `perfil`; a senha, em `password` ou `senha` |
| `tipo` na resposta do login | `perfil` | `ADMIN` ou `USER` (antes, `tipo` trazia `Bearer`); os dois campos são enviados |
| claim `tipo` no JWT | claim `role` | Mesmo valor; `role` continua definindo as permissões |

`CronogramaApiTest` reproduz as nove verificações da tabela com essas rotas.

## Segurança e erros

O cliente centraliza HTTP em `frontend/src/api.js`. Requisições autenticadas levam `Authorization: Bearer <token>`. O login envia somente email e senha, sem reaproveitar token expirado. O token fica no `sessionStorage` e é removido no logout e em respostas 401; o usuário é encaminhado ao login. Respostas 403 mostram acesso negado; 409 mostram a mensagem da regra de negócio; 404 indicam item não encontrado. Erros de rede e de email também recebem mensagens legíveis.

O backend continua exigindo as permissões independentemente de qual tela ou ferramenta fez a chamada. Ocultar botões no React não substitui a autorização no servidor.

## Validar

Na pasta do backend:

```powershell
mvn '-Dmaven.repo.local=../.m2/repository' test
```

Na pasta `frontend`:

```powershell
npm.cmd run build
npm.cmd exec -- playwright test
```

Os testes de navegador exigem os dois servidores ligados e os dados opcionais de demonstração. A configuração usa o Chrome instalado em `C:/Program Files/Google/Chrome/Application/chrome.exe`. Em outra máquina, ajuste `playwright.config.js` ou instale o Chromium do Playwright e remova `executablePath`.

Os testes Java cobrem a tabela de verificação do cronograma (`CronogramaApiTest`), autenticação, autorização, CRUDs, isolamento entre clientes, favoritos únicos, proposta duplicada, aceite, contrato/PDF, falha e sucesso de notificação, assinatura, cancelamento e aceite concorrente. Os testes de navegador cobrem login, rota privada, validação, painel, edição/exclusão de bairros, favoritos, filtros, paginação, detalhe e responsividade.

## Git e entrega

O repositório local inclui frontend, backend, documentação e fotos ilustrativas. Banco de dados, uploads gerados, dependências, logs, build e credenciais ficam fora do Git/ZIP. Ao extrair, execute o setup acima; os dados de demonstração são recriados pelo script opcional.

Repositório no GitHub: <https://github.com/fabianoheusner3-cell/patio-imoveis> (branch `main`). Para manter a aplicação na internet, será necessário hospedar o backend Java e definir o proxy `/api` do frontend para ele; o setup desta entrega é local.

Referências: [React](https://react.dev/learn/build-a-react-app-from-scratch), [Vite](https://vite.dev/guide/), [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
