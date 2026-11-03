(() => {
    const dialog = document.querySelector("#sabermais-dialog");
    const form = document.querySelector("#sabermais-form");
    const status = document.querySelector("#sabermais-status");
    const send = document.querySelector("#sabermais-send");
    let imovelId = null;
    document.addEventListener("click", event => {
        const button = event.target.closest("[data-sabermais]");
        if (!button) return;
        imovelId = Number(button.dataset.sabermais);
        form.reset();
        status.hidden = true;
        document.querySelector("#sabermais-property").textContent =
            "Imóvel #" + imovelId + " — " + button.closest("tr").children[2].textContent;
        dialog.showModal();
    });
    document.querySelector("#sabermais-close").addEventListener("click", () => dialog.close());
    form.addEventListener("submit", async event => {
        event.preventDefault();
        if (!form.reportValidity() || send.disabled) return;
        const body = Object.fromEntries(new FormData(form));
        for (const campo of Object.keys(body)) body[campo] = body[campo].trim();
        body.imovelId = imovelId;
        send.disabled = true;
        document.querySelector("#sabermais-close").disabled = true;
        status.hidden = false;
        status.textContent = "Enviando sua solicitação...";
        try {
            const response = await authFetch("/imoveis/sabermais/" + imovelId, {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify(body)
            });
            if (!response.ok) {
                const mensagens = {
                    400: "Confira nome, telefone, email e mensagem.",
                    404: "Este imóvel não foi encontrado. Atualize a lista.",
                    502: "O email não pôde ser enviado. Tente novamente mais tarde.",
                    503: "O envio de email está indisponível no momento."
                };
                let diagnostico;
                if (response.status === 502) {
                    try { diagnostico = (await response.json()).mensagem; } catch {}
                }
                throw new Error(diagnostico || mensagens[response.status] || "Não foi possível enviar a solicitação.");
            }
            status.textContent = "Solicitação enviada! As informações foram encaminhadas para seu email.";
            form.reset();
        } catch (error) {
            status.textContent = error.message;
        } finally {
            send.disabled = false;
            document.querySelector("#sabermais-close").disabled = false;
        }
    });
    dialog.addEventListener("cancel", event => {
        if (send.disabled) event.preventDefault();
    });
})();