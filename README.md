# Morada - React + Vite e Spring Boot

Consulte [README-REACT.md](README-REACT.md) para executar a nova interface, configurar emails, testar e recriar os dados de demonstracao.

Resultados: [VALIDACAO-REACT.md](VALIDACAO-REACT.md).

---

# Atividade JWT

Consulte [ATIVIDADE-JWT.md](ATIVIDADE-JWT.md) para as respostas, regras de acesso, configuracao do administrador e execucao desta versao.

# Tipos de imóveis com paginação

Adaptação do exemplo do professor para tipos de imóveis, com campos `id` e `nome`.

| Classe | Responsabilidade |
| --- | --- |
| `TipoImovelModel` | Entidade JPA da tabela `tipos_imoveis`, com ID automático e nome. |
| `TipoImovelRepository` | Acesso ao banco com `JpaRepository<TipoImovelModel, Integer>`. |
| `TipoImovelService` | Consulta, cadastro, atualização, exclusão e paginação. |
| `TipoImovelController` | Requisições HTTP em `/tipos-imoveis`. |

As classes estão em `src/main/java/com/example/demo`, nos pacotes `models`, `repositories`, `services` e `controllers`. `DemoApplication` inicia a aplicação.

## Executar

Requisitos: JDK 25 ou superior e Maven 3.6.3 ou superior. Projeto com Spring Boot 4.1.1 e Lombok 1.18.48.

```powershell
cd C:\Fabiano\BairroPageable
mvn spring-boot:run
```

Aguarde `Started DemoApplication`. Para encerrar, pressione `Ctrl+C`.

O banco H2 salva os dados no arquivo `data/tipos-imoveis.mv.db`. Cadastros, edições e exclusões permanecem após reiniciar a aplicação. Execute sempre a partir da pasta do projeto para utilizar o mesmo banco. Não apague a pasta `data`.

Os registros da aplicação anterior foram preservados na migração local. Em uma instalação nova, o banco começa vazio; utilize POST para cadastrar. Os 12 exemplos automáticos são exclusivos dos testes, que usam um banco separado em memória.

## Cadastrar pelo terminal PowerShell

Com a aplicação em execução, abra outro terminal e execute:

```powershell
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/tipos-imoveis" -ContentType "application/json; charset=utf-8" -Body '{"nome":"Casa geminada"}'
```

O POST cadastra no banco e gera o ID automaticamente. A resposta 201 não tem corpo, por isso o terminal pode ficar sem mensagem. Confira em http://localhost:8080/tipos-imoveis. PUT altera um ID existente; POST cria um registro novo.

## Cadastrar, editar e apagar pela tela

Abra **http://localhost:8080/** no navegador.

- Para cadastrar, preencha o nome e clique em **Cadastrar tipo**.
- Para editar, clique em **Editar** na linha, altere o nome e clique em **Salvar alterações**.
- Para apagar, clique em **Apagar** na linha e confirme na janela.
- Use **Anterior**, **Próxima** e **Por página** para navegar pelos registros.

A tela usa POST, PUT e DELETE na API e grava no banco H2 em arquivo. Não é necessário usar Postman ou terminal para essas operações. Os arquivos da interface ficam em `src/main/resources/static`; as quatro classes Java mantêm a estrutura do professor.

## Consultar o JSON no navegador

Lista completa: http://localhost:8080/tipos-imoveis

Consulta paginada:

```text
http://localhost:8080/tipos-imoveis/tipos-imoveis-page?page=0&size=5&sort=id,DESC
```

- `page=0`: primeira página. Troque por `page=1` ou `page=2` na barra de endereço e pressione Enter.
- `size=5`: até cinco registros por página.
- `sort=id,DESC`: IDs do maior para o menor. Use `ASC` para ordem crescente.

O número de páginas depende dos registros cadastrados. O JSON contém `content`, `totalElements`, `totalPages`, `number` e `size`.

O controller recebe `Pageable`, o service o repassa e o repository executa `findAll(pageable)`.

## Cadastrar, editar e apagar no Postman

Mantenha a aplicação em execução. Escolha o método e informe a URL:

| Método | URL | Operação |
| --- | --- | --- |
| GET | `http://localhost:8080/tipos-imoveis` | Listar todos. |
| GET | `http://localhost:8080/tipos-imoveis/1` | Consultar ID 1. |
| POST | `http://localhost:8080/tipos-imoveis` | Cadastrar; retorna 201 e cabeçalho `Location`. |
| PUT | `http://localhost:8080/tipos-imoveis/1` | Editar ID 1; retorna 204. |
| DELETE | `http://localhost:8080/tipos-imoveis/1` | Apagar ID 1; retorna 204. |

Para POST e PUT, selecione **Body → raw → JSON** e envie:

```json
{
  "nome": "Casa"
}
```

No POST, omita o ID para gerá-lo automaticamente. No PUT e DELETE, substitua `1` pelo ID desejado. No DELETE, não envie corpo; clique em **Send** e consulte a lista para conferir.

A barra de endereço do navegador faz consultas GET; ela não edita nem apaga registros.

## Correspondência com o exemplo do professor

Foram adaptados os nomes das classes, a entidade, os campos e as rotas. A lógica do exemplo foi preservada: consultar ID inexistente retorna 200 com corpo vazio; atualizar retorna `null` no service se o ID não existir ou ocorrer uma exceção, mas o controller retorna 204. Consulte o registro para confirmar uma alteração.

A configuração Maven/Spring Boot, a classe de inicialização, o banco H2, os dados de exemplo e os testes foram acrescentados para executar e verificar o projeto; não foram fornecidos pelo professor.

## Testar e gerar o executável

```powershell
mvn verify
java -jar target/tipo-imovel-pageable-0.0.1-SNAPSHOT.jar
```

Os testes verificam paginação, ordenação, cadastro, consulta, atualização e exclusão.

## Fotos dos imóveis (exemplo do professor)

As classes FotoImovelModel, FotoImovelRepository, FotoImovelService e FotoImovelController
foram adaptadas para o pacote com.example.demo. O banco H2 existente foi mantido;
a configuração MySQL do exemplo não foi aplicada.

- POST /fotos: enviar multipart/form-data com arquivo e imovelId de um imóvel cadastrado.
- GET /fotos/imovel/{id}: listar apenas as fotos daquele imóvel.
- DELETE /fotos/{id}: apagar o arquivo no disco e seu registro no banco.
- POST /fotos/debug: consultar os nomes das partes enviadas no formulário.

Os arquivos ficam em uploads/{imovelId}, com nome único; o limite de cada envio é 10 MB.
Editar foto permite alterar a ordem (zero ou maior) e substituir a imagem mantendo o ID. Tornar capa seleciona uma única foto por imóvel; excluir a capa deixa o imóvel sem capa até uma nova escolha.
Na tela /imoveis.html, clique em Fotos na linha de um imóvel para enviar, visualizar
ou apagar fotos. Clique na imagem para abrir o tamanho original. A API aceita imagens
JPG, PNG ou GIF válidas. GET /fotos/{id}/arquivo entrega a imagem para o navegador.
Apagar um imóvel também apaga suas fotos e os arquivos correspondentes.

PUT /fotos/{id} com JSON {"ordem":1} altera a ordem.
PUT /fotos/{id} com multipart/form-data (ordem e arquivo opcional) edita a foto.
PUT /fotos/{id}/capa torna a foto capa e desmarca as demais fotos do mesmo imóvel.

## Email em texto com Mailgun

Para usar SMTP, defina MAIL_TRANSPORT=smtp. O projeto utiliza spring-boot-starter-mail e JavaMailSender com STARTTLS na porta 587.
Consulte as credenciais **SMTP** do seu dominio no Mailgun (nao use a chave da API).
No sandbox, adicione o destinatario em Authorized Recipients e confirme o email recebido.

No PowerShell, configure antes de iniciar a aplicacao:

```powershell
$env:MAIL_TRANSPORT = 'smtp'
$env:MAILGUN_SMTP_USERNAME = 'smtp'
 = 'postmaster@sandboxSEU_DOMINIO.mailgun.org'
$env:MAILGUN_SMTP_PASSWORD = 'SUA_SENHA_SMTP'
$env:MAILGUN_FROM = 'smtp'
 = 'postmaster@sandboxSEU_DOMINIO.mailgun.org'
$env:MAIL_TEST_ENABLED = 'true'
mvn spring-boot:run
```

A rota de teste fica desativada por padrao. Ative apenas durante testes locais;
ela nao possui autenticacao. MAIL_DEBUG=true habilita o debug SMTP, desativado por padrao.
MAILGUN_SMTP_HOST permite trocar o servidor pelo indicado no painel do seu dominio.

Em outro terminal:

```powershell
$body = @{
    destinatario = 'SEU_EMAIL_AUTORIZADO'
    assunto = 'Teste Mailgun'
    texto = 'Ola! Este email foi enviado em texto pelo Spring Boot.'
} | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/emails/texto' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

Resposta 204 indica que o servidor SMTP aceitou a mensagem. Confira a caixa de entrada,
o spam e os logs de envio do Mailgun para verificar a entrega.
400 indica campos invalidos; 503 indica remetente ausente; 502 indica falha SMTP.
Os testes automatizados usam um remetente simulado e nao enviam emails reais.

Documentacao: https://documentation.mailgun.com/docs/mailgun/user-manual/sending-messages/send-smtp
Sandbox: https://documentation.mailgun.com/docs/mailgun/user-manual/domains/domains-sandbox

## Testar pela API HTTP do Mailgun (codigo gerado pelo painel)

O transporte padrao agora e API HTTP, com RestClient do Spring, sem precisar do Unirest.
A implementacao envia multipart/form-data com autenticacao Basic, conforme a documentacao:
https://documentation.mailgun.com/docs/mailgun/api-reference/send/mailgun/messages/post-v3--domain-name--messages

Antes de testar, reative a conta com o suporte do Mailgun, revogue a chave compartilhada
e crie uma nova. Confirme fabianoheusner3@gmail.com como destinatario autorizado.
A chave nao fica gravada no projeto. API_KEY tambem e aceita como alternativa a MAILGUN_API_KEY.

No PowerShell, use uma chave nova (a senha digitada fica oculta):

```powershell
$chaveMailgun = Read-Host 'Nova chave de API Mailgun' -AsSecureString
$env:MAILGUN_API_KEY = [System.Net.NetworkCredential]::new('', $chaveMailgun).Password
$env:MAIL_TRANSPORT = 'api'
$env:MAIL_TEST_ENABLED = 'true'
mvn spring-boot:run
```

O dominio sandbox e o remetente do exemplo ja estao configurados.
MAILGUN_DOMAIN, MAILGUN_FROM e MAILGUN_API_BASE_URL permitem alterar esses valores.

Em outro terminal, envie um unico teste:

```powershell
$body = @{
    destinatario = 'fabianoheusner3@gmail.com'
    assunto = 'Teste Mailgun - Spring Boot'
    texto = 'Ola Fabiano! Este email foi enviado pela API do Mailgun.'
} | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/emails/texto' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

204 significa mensagem aceita pelo provedor, sem garantir entrega na caixa de entrada.
503 significa configuracao ausente ou invalida; 502 significa falha no provedor
(por exemplo: conta desativada, chave invalida ou destinatario nao autorizado).
Confira os logs do Mailgun para detalhes. A rota continua desativada por padrao.

## Email com template HTML (atualizacao do professor)

A dependencia spring-boot-starter-thymeleaf processa o arquivo
src/main/resources/templates/email/template.html com nome, itens e link.
EmailService.enviarEmailComTemplate utiliza TemplateEngine e MimeMessageHelper
no transporte SMTP; na API do Mailgun, envia o HTML renderizado no campo html.

Com MAIL_TEST_ENABLED=true e a aplicacao iniciada, envie no PowerShell:

```powershell
$body = @{
    destinatario = 'fabianoheusner3@gmail.com'
    assunto = 'Bem-vindo!'
    nome = 'Maria'
    itens = @('Plano Pro', 'Suporte 24h')
    link = 'https://example.com'
} | ConvertTo-Json
Invoke-WebRequest -Method Post -Uri 'http://localhost:8080/emails/template' -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
```

A rota usa POST e retorna 204 quando o provedor aceita o envio. O template e fixo
em email/template; a requisicao informa somente os dados. O link deve ser HTTP ou HTTPS.
Para seguir o exemplo SMTP do professor, configure MAIL_TRANSPORT=smtp e as credenciais
SMTP do Mailgun. Para continuar com a chave da API, mantenha MAIL_TRANSPORT=api.
O remetente vem de MAILGUN_FROM, utilizando o dominio autorizado do Mailgun.
Os testes verificam renderizacao, acentos UTF-8, escape de HTML e envio simulado
nos dois transportes, sem enviar mensagens reais.

## Mailtrap Sandbox: testar texto e template sem Mailgun

O perfil mailtrap configura sandbox.smtp.mailtrap.io:587 com STARTTLS e transporte SMTP.
O usuario da caixa apresentada esta configurado; caso redefina as credenciais, informe
MAILTRAP_SMTP_USERNAME com o novo usuario. A senha fica somente no ambiente do processo.

Na pasta do projeto, execute no PowerShell:

```powershell
$senhaMailtrap = Read-Host 'Cole a senha SMTP completa do Mailtrap' -AsSecureString
$env:MAILTRAP_SMTP_PASSWORD = [System.Net.NetworkCredential]::new('', $senhaMailtrap).Password
$env:MAIL_TEST_ENABLED = 'true'
mvn spring-boot:run "-Dspring-boot.run.profiles=mailtrap"
```

Pare uma instancia anterior com Ctrl+C antes de iniciar outra na mesma porta.
Use as rotas POST /emails/texto e POST /emails/template documentadas acima.
A caixa My Sandbox captura as mensagens; elas nao chegam ao Gmail.
O remetente de teste e imobiliaria@example.com, personalizavel por MAILTRAP_FROM.
O perfil fixa SMTP mesmo que MAIL_TRANSPORT=api tenha sido definido anteriormente.
Ao encerrar, remova a senha do terminal com Remove-Item Env:MAILTRAP_SMTP_PASSWORD.

Para automatizar os dois testes, execute:
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\testar-mailtrap.ps1

O script gera o JAR, solicita a senha sem exibi-la, inicia uma instancia temporaria
em uma porta livre escolhida automaticamente com banco em memoria, envia um email em texto e outro com template e encerra
somente essa instancia. As mensagens aparecem em My Sandbox. A senha nao e salva.

## Atividade da Aula 10 — Saber mais sobre um imóvel

Implementação e instruções de teste em [ATIVIDADE-AULA10.md](ATIVIDADE-AULA10.md).
POST /imoveis/sabermais/{id} recebe nome, telefone, email e mensagem; imovelId opcional
no JSON deve coincidir com a URL. ImovelController chama o serviço de email e envia
o template HTML email/saber-mais ao cliente. A tela de imóveis inclui o botão Saber mais.
