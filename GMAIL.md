# Envio de emails pelo Gmail

Conta escolhida: **fabianoheusner3@gmail.com**. Ela será o remetente das notificações e o destinatário dos pedidos de interesse da imobiliária. A senha não é armazenada no projeto.

## 1. Criar a senha de app

Ative a verificação em duas etapas na conta Google. Depois, entre em [Senhas de app](https://myaccount.google.com/apppasswords) e crie uma senha para o projeto **Pátio Imobiliária**. O Google mostra uma senha de 16 caracteres.

Use essa senha de app, não a senha habitual do Gmail. Se a opção não estiver disponível, consulte as condições da conta e a [ajuda oficial do Google](https://support.google.com/mail/answer/185833?hl=pt-BR). Não envie a senha pelo chat e não a coloque no Git.

## 2. Iniciar o backend

Pare qualquer backend já ligado na porta 8080 com Ctrl+C. No PowerShell, dentro da pasta que contém `pom.xml`, execute:

```powershell
.\iniciar-gmail.cmd
```

O script solicita a senha com entrada oculta e inicia o backend usando o perfil `gmail`. O endereço SMTP é `smtp.gmail.com`, porta 587 com STARTTLS. As credenciais são passadas ao Java pelas variáveis de ambiente do processo; não são gravadas em arquivo. Ao encerrar o script, os valores anteriores do terminal são restaurados.

Em outro terminal:

```powershell
cd frontend
npm.cmd run dev
```

Abra **http://localhost:5173**. O administrador de demonstração continua sendo `admin@example.com`, senha `AdminTeste123!`, se foi criado com essas credenciais. A conta usada no login do sistema e a conta de envio Gmail são configurações diferentes.

## 3. Validar o envio

1. Abra um imóvel e clique em **Tenho interesse**. Preencha o formulário e envie. O pedido será enviado ao Gmail escolhido para a imobiliária.
2. Para testar contratos, use no cadastro do cliente um email real que você possa consultar. Aceite uma oferta como ADMIN, aprove o contrato e clique em **Enviar ao cliente**.
3. Confira a chegada do email na caixa de entrada e no spam. Só após o envio ser concluído o contrato fica liberado para assinatura na área do titular.

O script não envia mensagens automaticamente. A confirmação externa de entrega ainda depende de executar esses passos com a senha de app válida. Os testes automatizados usam um remetente simulado e não enviam emails reais.

Para trocar a conta ou o destinatário:

```powershell
.\iniciar-gmail.cmd -Email outro@gmail.com -Destinatario destino@exemplo.com
```

Referências: [Senhas de app](https://support.google.com/mail/answer/185833?hl=pt-BR) e [Gmail SMTP](https://developers.google.com/workspace/gmail/imap/imap-smtp).
