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

O script adiciona oito imóveis ilustrativos, tipos, bairros, fotos, o cliente `cliente@example.com` (senha `Cliente123!`) e o corretor `corretor@example.com` (senha `Corretor123!`). Ele pode ser repetido sem duplicar os mesmos títulos/contas e **não apaga os dados existentes**. Não execute em um ambiente de produção. As imagens são ilustrativas, não representam os imóveis descritos. Os arquivos estão em `frontend/public/demo/`; fontes: Unsplash, fotos `1600585154340-be6161a56a0c`, `1600047509807-ba8f99d2cdde`, `1600596542815-ffad4c1539a9` e `1564013799919-ab600027ffc6`.

## O que foi implementado

| Área | Funcionalidades |
|---|---|
| Pública | Vitrine em cards com fotos, tipo, bairro, cidade e preços; detalhes e galeria; consulta por bairro, finalidade, faixa de preço e busca textual; filtros combinados; ordenação por preço; paginação de seis itens; formulário de interesse preenchido com dados do imóvel |
| Login | JWT, validações, mensagens amigáveis, redirecionamento por perfil e restauração da sessão via `/auth/me` |
| Cliente | Resumo com indicadores, gráfico e próximos passos; fazer ofertas pelo detalhe ou pelos favoritos; acompanhar e cancelar as próprias ofertas em aberto; favoritos; contratos com etapas, PDF e assinatura depois da aprovação |
| Corretor | Painel com indicadores e gráficos; fila de ofertas com aceite ou recusa (com mensagem ao cliente); preparar o contrato (valor final, início, prazo e condições) e enviar para aprovação; corrigir contratos devolvidos |
| Administrador | CRUD de imóveis, bairros, tipos e fotos; usuários com perfil cliente, corretor ou administrador; proteção contra excluir a própria conta; consulta de todas as ofertas; contratos: aprovar, devolver ao corretor com motivo, reenviar o email, cancelar e PDF |
| Painéis | Um por perfil, com indicadores, lista de pendências e gráficos (etapas dos contratos, ofertas por situação, últimos seis meses, negócios por corretor, imóveis por bairro e finalidade). Cada gráfico tem “Ver tabela” com os mesmos números |
| Listas | Ofertas e contratos com busca, filtro de situação e paginação no servidor (`Pageable`); os atalhos dos painéis abrem a lista já filtrada |
| Interface | Layout responsivo, menus por perfil, rotas protegidas, carregamento/erro com nova tentativa/sem resultados, confirmações antes de ações importantes e validações de formulário |

A vitrine usa `GET /imoveis/imoveis-page?vitrine=true` com `Pageable`: paginação (`page`, `size`), ordenação (`sort=preco,asc|desc`) e filtros (`bairro`, `busca`, `finalidade`, `min`, `max`) são feitos no servidor e só aparecem imóveis disponíveis. Sem `vitrine=true`, a rota continua como no exemplo da aula.

## Regras de negócio e integridade

Fluxo da negociação (Aula 3): **cliente envia oferta → corretor aceita e prepara o contrato → administrador aprova → cliente assina**. O contrato passa pelas etapas `EM_PREPARACAO` → `AGUARDANDO_APROVACAO` → `AGUARDANDO_ASSINATURA` → `ASSINADO` (ou `CANCELADO`), e nenhuma etapa pode ser pulada.

- Somente imóveis com status disponível recebem ofertas. Tipo, valor positivo e mensagem são validados no servidor; o preço cadastrado deve permitir aluguel ou venda conforme a oferta.
- Um cliente não pode ter duas ofertas em aberto (pendente ou em espera) para o mesmo imóvel. Clientes diferentes podem criar propostas para ele. O cliente pode cancelar a própria oferta enquanto ela está em aberto.
- O aceite e a recusa são exclusivos do CORRETOR. Aceitar reserva o imóvel, coloca as demais ofertas pendentes em espera e abre o contrato em preparação, com esse corretor como responsável. A recusa pode levar uma mensagem que o cliente vê.
- Só o corretor responsável prepara o contrato: valor final positivo, data de início entre hoje e os próximos dois anos, prazo de 1 a 120 meses no aluguel e condições obrigatórias. Outro corretor não vê nem altera o contrato.
- Só o ADMIN aprova ou devolve o contrato ao corretor, com o motivo do ajuste. Quem prepara não aprova.
- A aprovação libera a assinatura e tenta avisar o cliente por email. Se o email falhar, o contrato continua aprovado e o cliente assina pela área de contratos; o administrador pode reenviar o aviso.
- Assinar exige titularidade, o nome do cadastro e a confirmação de concordância. A assinatura marca o imóvel como alugado ou vendido e recusa as outras ofertas abertas, com uma resposta explicando o motivo.
- Cancelar contrato (somente ADMIN) cancela a oferta vinculada e devolve o imóvel ao status disponível. As propostas que estavam em espera permanecem em espera; um corretor pode aceitá-las depois. Novas ofertas também são permitidas.
- O bloqueio pessimista do imóvel serializa decisões concorrentes. Há um teste com dois corretores aceitando ofertas do mesmo imóvel ao mesmo tempo: apenas um aceite é concluído.
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

O formulário público envia o interesse **à imobiliária**, incluindo código, tipo, bairro e valores. Ao aprovar um contrato, a API avisa **o email do cliente** com o link da área de contratos, onde ele entra para baixar o PDF e assinar. O aviso não trava o fluxo: se o email falhar, o contrato continua aprovado e já pode ser assinado; a tela informa a falha e o administrador pode usar “Enviar email” para tentar de novo. As falhas de serviço são informadas na tela, sem expor credenciais.

**Nenhuma credencial real de email faz parte da entrega.** O envio externo real precisa ser validado com as credenciais do seu provedor; nos testes automatizados o serviço de email é simulado.

## Rotas novas

| Método / rota | Acesso |
|---|---|
| GET `/auth/me` | Autenticado |
| POST `/contato/{imovelId}` | Público |
| GET `/ofertas` | Cliente vê as próprias; corretor e ADMIN veem todas; filtro `?status=PENDENTE` |
| GET `/ofertas/ofertas-page` | Igual, paginada (`Pageable`): `page`, `size`, `status` (uma ou várias separadas por vírgula, como `PENDENTE,EM_ESPERA`) e `busca` (imóvel, código, cliente ou email) |
| POST `/ofertas` | USER; JSON: `imovelId`, `tipo` (`ALUGUEL`/`VENDA`), `valorProposto`, `mensagem` |
| POST `/ofertas/{id}/cancelar` | USER dono da oferta, enquanto ela está em aberto |
| POST `/ofertas/{id}/aceitar` e `/recusar` | CORRETOR; a recusa aceita o JSON opcional `motivo` |
| GET `/contratos`, `/contratos/{id}` e `/contratos/{id}/pdf` | Titular, corretor responsável ou ADMIN |
| GET `/contratos/contratos-page` | Igual, paginada: `page`, `size`, `status` e `busca` (imóvel, cliente, corretor ou código) |
| POST `/contratos/{id}/preparar` | CORRETOR responsável; JSON: `valorFinal`, `dataInicio` (`AAAA-MM-DD`), `prazoMeses` (só aluguel), `condicoes` |
| POST `/contratos/{id}/aprovar`, `/devolver` (JSON `motivo`), `/enviar`, `/cancelar` ou `/recusar` | ADMIN |
| POST `/contratos/{id}/assinar` | Titular USER; JSON: `nome`, `concordo: true` |
| GET `/favoritos` | Autenticado; retorna apenas os próprios |
| POST/DELETE `/favoritos/{imovelId}` | USER |
| GET `/painel`, `/painel/corretor`, `/painel/cliente` | ADMIN, CORRETOR e USER, respectivamente |

Os CRUDs existentes continuam em `/imoveis`, `/bairros`, `/tipos-imoveis`, `/fotos` e `/usuarios`. A edição/exclusão de bairros agora também está implementada. As leituras de tipos inexistentes passaram a retornar 404.

## Aula 3 — perfis e checkpoint

| Perfil | Área | O que faz |
|---|---|---|
| Cliente (`USER`) | `/cliente` | Resumo, minhas ofertas, favoritos e contratos; envia, cancela e acompanha ofertas; assina o contrato aprovado |
| Corretor (`CORRETOR`) | `/corretor` | Painel, fila de ofertas e contratos; aceita ou recusa ofertas; prepara o contrato e envia para aprovação |
| Administrador (`ADMIN`) | `/admin` | Painel geral e cadastros; cria corretores em Usuários; aprova ou devolve contratos, reenvia o aviso por email e cancela |

O cadastro público (`/cadastro`) continua criando só clientes. Corretores são criados pelo administrador em **Usuários** (perfil “Corretor”) ou pela rota `/users` com `"tipo": "CORRETOR"`.

### Roteiro para demonstrar o fluxo completo

Com o backend, o frontend e os dados de demonstração ligados:

1. **Cliente** (`cliente@example.com` / `Cliente123!`): abra um imóvel na vitrine, clique em **Fazer oferta** e envie. A oferta aparece como *Pendente* em **Minhas ofertas**.
2. **Corretor** (`corretor@example.com` / `Corretor123!`): o painel mostra a oferta na fila. Em **Ofertas**, clique em **Aceitar**: o imóvel fica reservado e a tela vai para **Contratos**. Clique em **Preparar contrato**, revise valor, data e condições e **Envie para aprovação**.
3. **Administrador** (`admin@example.com` / `AdminTeste123!`): o painel lista “Contratos esperando sua aprovação”. Clique em **Revisar contratos** e em **Aprovar** (ou **Devolver ao corretor** para mostrar o pedido de ajuste).
4. **Cliente**: o resumo mostra “Pronto para você assinar”. Clique em **Assinar**, baixe o PDF, digite o nome do cadastro (`Cliente Demonstração`), marque a concordância e confirme. O imóvel passa a *Vendido* ou *Alugado* e sai da vitrine.

A linha do tempo de cada contrato mostra quem fez cada etapa e de quem é a vez. O teste `frontend/tests/fluxo.spec.js` faz esse mesmo roteiro no navegador.

### Banco criado antes da Aula 3

O H2 guarda perfil e situação do contrato como colunas `ENUM`, e o `ddl-auto=update` não altera colunas existentes. Na inicialização, `AjusteEnumsH2` acrescenta os valores novos (`CORRETOR`, `EM_PREPARACAO`, `AGUARDANDO_APROVACAO`) sem mudar os dados, e contratos antigos ainda não aprovados passam para *Aguardando aprovação*. O `AjusteEnumsH2Test` simula um banco antigo.

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

O cliente centraliza HTTP em `frontend/src/api.js`. Requisições autenticadas levam `Authorization: Bearer <token>`. O login e o cadastro (`/auth/login` e `/public/*`) nunca enviam token, para que um token antigo não atrapalhe. O token fica no `sessionStorage` e é removido no logout e em respostas 401; durante o uso, o usuário é encaminhado ao login. Ao abrir o site com um token que não vale mais (o backend gera outra chave a cada execução), ele é descartado em silêncio e a pessoa continua na página em que estava, como o cadastro. Respostas 403 mostram acesso negado; 409 mostram a mensagem da regra de negócio; 404 indicam item não encontrado. Erros de rede e de email também recebem mensagens legíveis.

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

Os testes de navegador exigem os dois servidores ligados e os dados opcionais de demonstração, e **gravam dados** (ofertas, contratos, um bairro de teste). Para não misturar com o banco de `data/`, suba o backend com banco em memória e uploads em `target/`:

```powershell
mvn '-Dmaven.repo.local=../.m2/repository' package -DskipTests
java -jar target/tipo-imovel-pageable-0.0.1-SNAPSHOT.jar --spring.datasource.url=jdbc:h2:mem:demo --app.admin.email=admin@example.com --app.admin.password=AdminTeste123! --app.upload-dir=target/uploads-teste
node scripts/dados-demo.mjs
```

A configuração usa o Chrome instalado em `C:/Program Files/Google/Chrome/Application/chrome.exe`. Em outra máquina, ajuste `playwright.config.js` ou instale o Chromium do Playwright e remova `executablePath`.

Os testes Java (27) cobrem a tabela de verificação do cronograma (`CronogramaApiTest`), autenticação, autorização, CRUDs e, na negociação: o fluxo do checkpoint com as permissões de cada perfil, nenhuma etapa pulada, devolução ao corretor, aluguel com prazo, desistência e recusa com mensagem, falha e sucesso do email, assinatura, cancelamento e dois corretores aceitando ao mesmo tempo (`NegociacaoApiTest`); o perfil corretor criado pela tabela do professor, listas paginadas com filtros e os três painéis (`PerfisPaineisApiTest`); e a atualização de um banco antigo (`AjusteEnumsH2Test`).

Os testes de navegador (9) cobrem login, rota privada, validação, painel, bairros, favoritos, filtros, paginação, detalhe e celular (`interface.spec.js`); o fluxo completo do checkpoint, inclusive a oferta duplicada e o nome errado na assinatura (`fluxo.spec.js`); e os estados de carregamento, erro 500 com nova tentativa, falta de conexão, lista vazia, acesso negado, sessão expirada e cadastro com uma sessão antiga no navegador (`estados.spec.js`). As falhas são simuladas interceptando a API com `page.route`.

## Git e entrega

O repositório local inclui frontend, backend, documentação e fotos ilustrativas. Banco de dados, uploads gerados, dependências, logs, build e credenciais ficam fora do Git/ZIP. Ao extrair, execute o setup acima; os dados de demonstração são recriados pelo script opcional.

Repositório no GitHub: <https://github.com/fabianoheusner3-cell/patio-imoveis> (branch `main`). Para manter a aplicação na internet, será necessário hospedar o backend Java e definir o proxy `/api` do frontend para ele; o setup desta entrega é local.

Referências: [React](https://react.dev/learn/build-a-react-app-from-scratch), [Vite](https://vite.dev/guide/), [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html).
