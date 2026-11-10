# Validação — 03/11/2026

| Verificação | Resultado |
|---|---|
| Build de produção React + Vite | Concluído, sem erros |
| Suíte de testes Java | 19 testes, 0 falhas, 0 erros |
| Testes no Chrome com Playwright | 4 testes, todos passaram |
| Vitrine em desktop e celular de 390 px | Conferida visualmente; sem rolagem horizontal |
| Segurança | JWT, 401/403, isolamento por cliente, acesso negado a outra área |
| Regras de ofertas | Duplicidade, múltiplos clientes, reserva e espera, somente um aceite simultâneo |
| Contratos | PDF, aprovação, falha/sucesso de envio simulado, titularidade, assinatura e cancelamento |
| Email real | Requer configuração SMTP/Mailgun; teste automatizado usa serviço simulado |
| Perfil Gmail | Host, porta 587, STARTTLS obrigatório, remetente e destinatário validados com envio simulado |
| GitHub remoto | Depende da conexão da conta; repositório e commit locais preparados |

Os testes de navegador fizeram chamadas à API local real e verificaram vitrine, paginação, finalidade, busca sem resultados, favoritos, detalhes, pré-preenchimento do contato, login e validação, proteção de rotas e CRUD de bairros com confirmação de exclusão. O fluxo de negociação e suas transações foram validados nos testes Java.

Não foram enviados emails reais durante a validação. A pasta `target/surefire-reports/` contém os relatórios Java após executar os testes. O build React está em `frontend/dist/` após `npm.cmd run build`.

O script `scripts/iniciar-gmail.ps1` teve a sintaxe validada pelo parser do PowerShell. A senha de app real deve ser digitada pelo usuário ao executar `iniciar-gmail.cmd`; ela não é armazenada no projeto. Consulte `GMAIL.md`.

## Identidade Pátio Imóveis

Nome, logotipo, tipografia, cores e composição da vitrine foram atualizados. A interface usa azul profundo, terracota e fundos claros; cards horizontais no computador e empilhados no celular. Nome do contrato PDF e documentação também atualizados.

Após a alteração: build React concluído; quatro testes de navegador passaram; dois testes de negociação/contrato passaram. As versões de 1440 px e 390 px foram conferidas visualmente. Os dados de imóveis, usuários e negociações foram preservados.

# Validação — 10/11/2026 (Aula 1 do cronograma)

| Verificação | Resultado |
|---|---|
| Tabela de verificação do professor, com `/public/login`, `/users`, `password` e `tipo` | 9 de 9 conforme o esperado, com o backend em execução |
| Rotas usadas pelo React (`/auth/login`, `/auth/me`, `/usuarios`) | Funcionando, sem alteração |
| Suíte de testes Java | 21 testes, 0 falhas, 0 erros (inclui `CronogramaApiTest`) |
| Build de produção React + Vite | Concluído, sem erros |
| Listagem de imóveis pelo React | `GET /api/imoveis` pelo proxy do Vite retornou os imóveis do backend |
| GitHub | Publicado em <https://github.com/fabianoheusner3-cell/patio-imoveis> |

As verificações usaram banco em memória; o banco de `data/` não foi alterado.
