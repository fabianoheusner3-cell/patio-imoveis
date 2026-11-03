(() => {
 const dialog = document.querySelector("#photos-dialog");
 const gallery = document.querySelector("#photos-gallery");
 const uploadForm = document.querySelector("#photos-form");
 const input = document.querySelector("#photos-files");
 const closeButton = document.querySelector("#photos-close");
 let selected = null, busy = false, requestVersion = 0;
 function notify(text, error = false) {
  const el = document.querySelector("#photos-message");
  el.hidden = false; el.textContent = text; el.className = error ? "error" : "success";
 }
 function setBusy(value) {
  busy = value;
  uploadForm.querySelector("button").disabled = value;
  input.disabled = value;
  closeButton.disabled = value;
  gallery.querySelectorAll("button, input").forEach(button => button.disabled = value);
 }
 async function photoRequest(url, options) {
  const response = await authFetch(url, options);
  if (!response.ok) {
   const errors = {400:"Confira a ordem (zero ou maior) e use uma imagem JPG, PNG ou GIF válida.",404:"A foto ou o imóvel não foi encontrado.",413:"A foto ultrapassa o limite de 10 MB."};
   throw new Error(errors[response.status] || "Não foi possível concluir a operação. Tente novamente.");
  }
  return response.status === 204 ? null : response.json();
 }
 async function loadGallery() {
  const version = ++requestVersion, id = selected;
  const photos = await photoRequest("/fotos/imovel/" + id);
  if (version !== requestVersion || id !== selected) return;
  gallery.replaceChildren();
  if (!photos.length) {
   const empty = document.createElement("p");
   empty.textContent = "Este imóvel ainda não tem fotos."; gallery.append(empty); return;
  }
  photos.forEach(photo => {
   const card = document.createElement("figure");
   const link = document.createElement("a");
   link.href = "/fotos/" + photo.id + "/arquivo"; link.target = "_blank"; link.rel = "noopener";
   const img = document.createElement("img");
   img.src = link.href + "?v=" + encodeURIComponent(photo.nomeArquivo); img.alt = "Foto do imóvel #" + selected; img.loading = "lazy";
   link.append(img);
   const caption = document.createElement("figcaption");
   caption.textContent = "Foto #" + photo.id + (photo.capa ? " · Capa" : "") + (photo.ordem != null ? " · Ordem " + photo.ordem : "");
   card.classList.toggle("is-cover", !!photo.capa);
   const cover = document.createElement("button");
   cover.type = "button"; cover.className = "secondary";
   cover.textContent = photo.capa ? "Foto de capa" : "Tornar capa";
   cover.disabled = busy;
   cover.onclick = async () => {
    if (busy) return;
    setBusy(true);
    try {
     await photoRequest("/fotos/" + photo.id + "/capa", {method:"PUT"});
     await loadGallery(); notify("Foto definida como capa.");
    } catch (error) { notify(error.message, true); }
    finally { setBusy(false); }
   };
   const edit = document.createElement("button");
   edit.type = "button"; edit.className = "secondary"; edit.textContent = "Editar foto";
   const editForm = document.createElement("form"); editForm.className = "photo-edit"; editForm.hidden = true;
   const orderLabel = document.createElement("label");
   const orderInput = document.createElement("input");
   orderLabel.textContent = "Ordem de exibição"; orderInput.type = "number";
   orderInput.min = "0"; orderInput.step = "1"; orderInput.required = true; orderInput.value = photo.ordem ?? 0;
   orderLabel.append(orderInput);
   const fileLabel = document.createElement("label");
   const fileInput = document.createElement("input");
   fileLabel.textContent = "Substituir imagem (opcional)"; fileInput.type = "file";
   fileInput.accept = "image/jpeg,image/png,image/gif"; fileLabel.append(fileInput);
   const save = document.createElement("button"); save.type = "submit"; save.className = "primary"; save.textContent = "Salvar foto";
   const cancel = document.createElement("button"); cancel.type = "button"; cancel.className = "secondary"; cancel.textContent = "Cancelar";
   cancel.onclick = () => { editForm.hidden = true; };
   edit.onclick = () => { if (!busy) editForm.hidden = !editForm.hidden; };
   editForm.append(orderLabel, fileLabel, save, cancel);
   editForm.onsubmit = async event => {
    event.preventDefault(); if (busy) return;
    const file = fileInput.files[0];
    if (file && (file.size > 10 * 1024 * 1024 || !/\.(jpe?g|png|gif)$/i.test(file.name))) {
     notify("Selecione uma imagem JPG, PNG ou GIF de até 10 MB.", true); return;
    }
    const body = new FormData(); body.append("ordem", orderInput.value);
    if (file) body.append("arquivo", file);
    setBusy(true);
    try {
     await photoRequest("/fotos/" + photo.id, {method:"PUT",body});
     await loadGallery(); notify("Foto editada.");
    } catch (error) { notify(error.message, true); }
    finally { setBusy(false); }
   };
   const remove = document.createElement("button");
   remove.type = "button"; remove.className = "danger"; remove.textContent = "Apagar foto";
   remove.disabled = busy;
   remove.onclick = async () => {
    if (busy || !confirm("Apagar esta foto?")) return;
    setBusy(true);
    try {
     await photoRequest("/fotos/" + photo.id, {method:"DELETE"});
     await loadGallery(); notify("Foto apagada.");
    } catch (error) { notify(error.message, true); }
    finally { setBusy(false); }
   };
   card.append(link, caption, cover, edit, remove, editForm); gallery.append(card);
  });
 }
 document.querySelector("main").addEventListener("click", async event => {
  const button = event.target.closest("[data-photos]");
  if (!button || busy) return;
  selected = Number(button.dataset.photos);
  const item = content.find(item => item.id === selected);
  document.querySelector("#photos-title").textContent = "Fotos: " + (item?.titulo || "Imóvel #" + selected);
  uploadForm.reset(); document.querySelector("#photos-message").hidden = true;
  gallery.textContent = "Carregando fotos…";
  dialog.showModal();
  try { await loadGallery(); }
  catch (error) { gallery.textContent = ""; notify(error.message, true); }
 });
 closeButton.onclick = () => { if (!busy) dialog.close(); };
 dialog.addEventListener("cancel", event => { if (busy) event.preventDefault(); });
 dialog.addEventListener("close", () => { selected = null; requestVersion++; load().catch(error => message(error.message, true)); });
 uploadForm.addEventListener("submit", async event => {
  event.preventDefault();
  if (busy || selected === null) return;
  const files = Array.from(input.files);
  if (!files.length) { notify("Selecione uma foto.", true); return; }
  if (files.some(file => file.size > 10 * 1024 * 1024)) { notify("Cada foto deve ter até 10 MB.", true); return; }
  if (files.some(file => !/\.(jpe?g|png|gif)$/i.test(file.name))) { notify("Selecione fotos JPG, PNG ou GIF.", true); return; }
  setBusy(true); let sent = 0;
  try {
   for (const file of files) {
    notify("Enviando foto " + (sent + 1) + " de " + files.length + "…");
    const body = new FormData(); body.append("arquivo", file); body.append("imovelId", selected);
    await photoRequest("/fotos", {method:"POST",body}); sent++;
   }
   uploadForm.reset(); await loadGallery();
   notify(sent === 1 ? "Foto enviada." : sent + " fotos enviadas.");
  } catch (error) {
   notify((sent ? sent + " foto(s) enviada(s). " : "") + error.message, true);
   try { await loadGallery(); } catch {}
  } finally { setBusy(false); }
 });
})();