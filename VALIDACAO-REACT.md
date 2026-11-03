# Validação — 03/11/2026

| Verificação | Resultado |
|---|---|
| Build de produção React + Vite | Concluído, sem erros |
| Suíte de testes Java | 18 testes, 0 falhas, 0 erros |
| Testes no Chrome com Playwright | 4 testes, todos passaram |
| Vitrine em desktop e celular de 390 px | Conferida visualmente; sem rolagem horizontal |
| Segurança | JWT, 401/403, isolamento por cliente, acesso negado a outra área |
| Regras de ofertas | Duplicidade, múltiplos clientes, reserva e espera, somente um aceite simultâneo |
| Contratos | PDF, aprovação, falha/sucesso de envio simulado, titularidade, assinatura e cancelamento |
| Email real | Requer configuração SMTP/Mailgun; teste automatizado usa serviço simulado |
| GitHub remoto | Depende da conexão da conta; repositório e commit locais preparados |

Os testes de navegador fizeram chamadas à API local real e verificaram vitrine, paginação, finalidade, busca sem resultados, favoritos, detalhes, pré-preenchimento do contato, login e validação, proteção de rotas e CRUD de bairros com confirmação de exclusão. O fluxo de negociação e suas transações foram validados nos testes Java.

Não foram enviados emails reais durante a validação. A pasta `target/surefire-reports/` contém os relatórios Java após executar os testes. O build React está em `frontend/dist/` após `npm.cmd run build`.
