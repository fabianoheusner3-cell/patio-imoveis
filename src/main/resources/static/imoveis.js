const fields = [
 ["titulo","Título","text"], ["descricao","Descrição","textarea"],
 ["precoVenda","Preço de venda","number"], ["precoAluguel","Preço de aluguel","number"],
 ["finalidade","Finalidade","text"], ["status","Status","text"],
 ["dormitorios","Dormitórios","integer"], ["banheiros","Banheiros","integer"], ["garagem","Vagas de garagem","integer"],
 ["areaTotal","Área total (m²)","number"], ["areaConstruida","Área construída (m²)","number"],
 ["endereco","Endereço","text"], ["numero","Número","text"], ["complemento","Complemento","text"],
 ["cep","CEP","text"], ["caracteristicas","Características","textarea"], ["destaque","Destaque","checkbox"],
 ["tipoImovel","Tipo de imóvel","select"], ["bairro","Bairro","neighborhood"]
];
const form = document.querySelector("#imovel-form");
let editing = null, page = 0, content = [];
let initialPhotoId = Number(new URLSearchParams(location.search).get("fotos"));
const escapeHtml = (value) => String(value ?? "").replace(/[&<>"']/g, c => ({ "&":"&amp;", "<":"&lt;", ">":"&gt;", '"':"&quot;", "'":"&#39;" }[c]));
function message(text, error = false) {
 const el = document.querySelector("#message"); el.hidden = false; el.textContent = text; el.className = error ? "error" : "success";
}
async function api(path, method = "GET", body) {
 const res = await authFetch(path, { method, headers: { "Content-Type": "application/json" }, ...(body ? { body: JSON.stringify(body) } : {}) });
 if (!res.ok) throw new Error(res.status === 404 ? "Imóvel não encontrado. Atualize a lista." : res.status === 400 ? "Confira os campos e selecione um tipo e bairro cadastrados." : "Não foi possível concluir a operação.");
 if (res.status === 201) return { id: Number(res.headers.get("Location").split("/").pop()) };
 return res.status === 204 || method !== "GET" ? null : res.json();
}
document.querySelector("#fields").innerHTML = fields.map(([name,label,type]) => {
 const required = ["titulo","tipoImovel","bairro"].includes(name) ? "required" : "";
 const input = type === "neighborhood" ? '<input id="bairro" name="bairro" list="bairro-options" required maxlength="255" placeholder="Digite ou escolha um bairro" autocomplete="off"><datalist id="bairro-options"></datalist><p class="hint">Se o bairro ainda não existir, ele será cadastrado ao salvar o imóvel.</p>' : type === "select" ? '<select id="'+name+'" name="'+name+'" required></select>' :
 type === "textarea" ? '<textarea id="'+name+'" name="'+name+'" maxlength="4000"></textarea>' :
 '<input id="'+name+'" name="'+name+'" type="'+(type === "integer" ? "number" : type)+'" '+required+(["number","integer"].includes(type) ? ' min="0" step="'+(type === "integer" ? "1" : "0.01")+'"' : ' maxlength="255"')+'>';
 return '<label for="'+name+'">'+label+'</label>'+input;
}).join("");
async function options() {
 const [types, neighborhoods] = await Promise.all([api("/tipos-imoveis"), api("/bairros")]);
 document.querySelector("#bairro-options").innerHTML = neighborhoods.map(item => '<option value="'+escapeHtml(item.nome)+'"></option>').join("");
 for (const [name, items] of [["tipoImovel",types]]) {
  const select = form.elements[name], selected = select.value;
  select.innerHTML = '<option value="">Selecione</option>'+items.map(item => '<option value="'+item.id+'">'+escapeHtml(item.nome)+'</option>').join("");
  select.value = selected;
 }
}
async function resolveBairro(nome) {
 nome = nome.trim();
 if (!nome) throw new Error("Informe o nome do bairro.");
 const neighborhoods = await api("/bairros");
 const existing = neighborhoods.find(item => item.nome.trim().toLocaleLowerCase("pt-BR") === nome.toLocaleLowerCase("pt-BR"));
 if (existing) return { id: existing.id };
 const created = await api("/bairros", "POST", { nome });
 await options();
 return created;
}
async function load() {
 const data = await api("/imoveis/imoveis-page?page="+page+"&size=5&sort=id,DESC");
 if (page > 0 && !data.content.length) { page--; return load(); }
 content = data.content;
 const photoSelect = document.querySelector("#photos-property");
 const previousPhotoSelection = photoSelect.value;
 photoSelect.innerHTML = content.map(item => '<option value="'+item.id+'">'+escapeHtml(item.titulo)+'</option>').join("");
 if (content.some(item => String(item.id) === previousPhotoSelection)) photoSelect.value = previousPhotoSelection;
 const photoOpenButton = document.querySelector("#open-photos");
 photoOpenButton.disabled = !content.length;
 photoOpenButton.dataset.photos = photoSelect.value;
 photoSelect.onchange = () => { photoOpenButton.dataset.photos = photoSelect.value; };
 const thumbnails = new Map(await Promise.all(content.map(async item => {
  try {
   const photos = await api("/fotos/imovel/" + item.id);
   return [item.id, photos.find(photo => photo.capa) || photos[0]];
  } catch { return [item.id, null]; }
 })));
 const photoCell = item => {
  const photo = thumbnails.get(item.id);
  return photo ? '<button type="button" class="photo-thumbnail" data-photos="'+item.id+'" aria-label="Ver fotos de '+escapeHtml(item.titulo)+'"><img src="/fotos/'+photo.id+'/arquivo?v='+encodeURIComponent(photo.nomeArquivo)+'" alt="Foto de '+escapeHtml(item.titulo)+'" loading="lazy"></button>' : '<span class="muted">Sem foto</span>';
 };
 document.querySelector("#rows").innerHTML = content.map(item => '<tr><td>'+item.id+'</td><td>'+photoCell(item)+'</td><td>'+escapeHtml(item.titulo)+'</td><td>'+escapeHtml(item.tipoImovel?.nome)+' / '+escapeHtml(item.bairro?.nome)+'</td><td><button class="secondary" data-sabermais="'+item.id+'">Saber mais</button> <button class="secondary" data-photos="'+item.id+'">Fotos</button> <button class="secondary" data-edit="'+item.id+'">Editar</button> <button class="danger" data-delete="'+item.id+'">Apagar</button></td></tr>').join("") || '<tr><td colspan="5">Nenhum imóvel cadastrado.</td></tr>';
 document.querySelector("#total").textContent = data.totalElements+" imóveis";
 document.querySelector("#page-label").textContent = "Página "+(page+1)+" de "+Math.max(1,data.totalPages);
 document.querySelector("#previous").disabled = data.first;
 document.querySelector("#next").disabled = data.last;
 if (initialPhotoId) {
  const photoButton = document.querySelector('[data-photos="'+initialPhotoId+'"]');
  if (photoButton) { initialPhotoId = null; photoButton.click(); }
 }
}
function reset() {
 editing = null; form.reset(); document.querySelector("#cancel").hidden = true;
 document.querySelector("#form-title").textContent = "Novo imóvel"; document.querySelector("#save").textContent = "Cadastrar imóvel";
}
form.addEventListener("submit", async event => {
 event.preventDefault(); const button = document.querySelector("#save"); button.disabled = true;
 try {
  const body = {};
  for (const [name,,type] of fields) {
   const field = form.elements[name];
   body[name] = type === "select" ? {id:Number(field.value)} : type === "checkbox" ? field.checked :
    ["number","integer"].includes(type) ? (field.value === "" ? null : Number(field.value)) : field.value.trim();
  }
  body.bairro = await resolveBairro(form.elements.bairro.value);
  await api(editing ? "/imoveis/"+editing : "/imoveis", editing ? "PUT" : "POST", body);
  reset(); await load(); message("Imóvel salvo.");
 } catch(error) { message(error.message,true); } finally { button.disabled = false; }
});
document.querySelector("#cancel").onclick = reset;
document.querySelector("#rows").addEventListener("click", async event => {
 const button = event.target.closest("button"); if (!button) return;
 try {
  if (button.dataset.edit) {
   const item = await api("/imoveis/"+button.dataset.edit); editing = item.id;
   for (const [name,,type] of fields) {
    if (type === "checkbox") form.elements[name].checked = !!item[name];
    else if (type === "neighborhood") form.elements[name].value = item.bairro?.nome ?? "";
    else form.elements[name].value = type === "select" ? item[name]?.id ?? "" : item[name] ?? "";
   }
   document.querySelector("#form-title").textContent = "Editar imóvel #"+item.id;
   document.querySelector("#save").textContent = "Salvar alterações"; document.querySelector("#cancel").hidden = false;
   form.scrollIntoView({behavior:"smooth"});
  }
  if (button.dataset.delete && confirm("Apagar este imóvel e suas fotos? Esta ação não pode ser desfeita.")) {
   button.disabled = true; await api("/imoveis/"+button.dataset.delete,"DELETE");
   if (String(editing) === button.dataset.delete) reset(); await load(); message("Imóvel apagado.");
  }
 } catch(error) { message(error.message,true); } finally { button.disabled = false; }
});
document.querySelector("#previous").onclick = () => { if(page>0) page--; load().catch(e=>message(e.message,true)); };
document.querySelector("#next").onclick = () => { page++; load().catch(e=>message(e.message,true)); };
document.querySelector("#bairro-form").onsubmit = async event => {
 event.preventDefault(); const button=event.currentTarget.querySelector("button"); button.disabled=true;
 try { const nome = document.querySelector("#bairro-nome").value.trim(); await resolveBairro(nome); event.target.reset(); await options(); form.elements.bairro.value = nome; message("Bairro selecionado no imóvel."); }
 catch(error) {message(error.message,true);} finally {button.disabled=false;}
};
Promise.all([options(),load()]).catch(e=>message(e.message,true));
