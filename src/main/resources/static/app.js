'use strict';
const $ = (id) => document.getElementById(id);
const api = '/tipos-imoveis';
let page = 0;
let pages = 1;
let editing = null;
let deleting = null;
let busy = false;

function message(text, error = false) {
  $('message').textContent = text;
  $('message').classList.toggle('error', error);
  $('message').hidden = false;
}

function controls() {
  document.querySelectorAll('button, input, select').forEach((element) => { element.disabled = busy; });
  $('previous').disabled = busy || page === 0;
  $('next').disabled = busy || page + 1 >= pages;
}

async function request(path, options = {}) {
  const response = await authFetch(path, options);
  if (!response.ok) throw new Error('Não foi possível concluir a operação. Verifique se a aplicação está funcionando e tente novamente.');
  return response;
}

function resetForm() {
  editing = null;
  $('form').reset();
  $('form-title').textContent = 'Novo tipo de imóvel';
  $('form-description').textContent = 'Informe um nome para adicionar à lista.';
  $('save').textContent = 'Cadastrar tipo';
  $('cancel').hidden = true;
}

function edit(item) {
  editing = item.id;
  $('form-title').textContent = 'Editar tipo de imóvel';
  $('form-description').textContent = `Alterando o registro de ID ${item.id}.`;
  $('nome').value = item.nome ?? '';
  $('save').textContent = 'Salvar alterações';
  $('cancel').hidden = false;
  $('nome').focus();
  $('form-title').scrollIntoView({ behavior: 'smooth', block: 'center' });
}

async function load() {
  const response = await request(`${api}/tipos-imoveis-page?page=${page}&size=${$('size').value}&sort=id,DESC`);
  const data = await response.json();
  pages = Math.max(1, data.totalPages);
  if (page >= pages) { page = pages - 1; return load(); }
  $('rows').replaceChildren();
  for (const item of data.content) {
    const row = document.createElement('tr');
    for (const value of [item.id, item.nome]) {
      const cell = document.createElement('td');
      cell.textContent = value;
      row.append(cell);
    }
    const cell = document.createElement('td');
    const actions = document.createElement('div');
    actions.className = 'row-actions';
    for (const [label, className, handler] of [
      ['Editar', 'edit', () => edit(item)],
      ['Apagar', 'delete', () => {
        deleting = item;
        $('delete-description').textContent = `Você vai apagar “${item.nome}” (ID ${item.id}).`;
        $('delete-dialog').showModal();
        $('delete-cancel').focus();
      }]
    ]) {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = className;
      button.textContent = label;
      button.setAttribute('aria-label', `${label} ${item.nome}`);
      button.addEventListener('click', handler);
      actions.append(button);
    }
    cell.append(actions);
    row.append(cell);
    $('rows').append(row);
  }
  $('empty').hidden = data.totalElements !== 0;
  $('total').textContent = `${data.totalElements} ${data.totalElements === 1 ? 'tipo cadastrado' : 'tipos cadastrados'}`;
  $('page-label').textContent = `Página ${page + 1} de ${pages}`;
}

async function run(action) {
  if (busy) return;
  busy = true;
  controls();
  try { await action(); }
  catch (error) {
    message(error instanceof TypeError ? 'Não foi possível conectar à aplicação. Confira se ela está em execução e clique em Atualizar.' : error.message, true);
  } finally { busy = false; controls(); }
}

$('form').addEventListener('submit', (event) => {
  event.preventDefault();
  const nome = $('nome').value.trim();
  if (!nome) { message('Informe o nome do tipo de imóvel.', true); $('nome').focus(); return; }
  run(async () => {
    const id = editing;
    const response = await request(id === null ? api : `${api}/${id}`, {
      method: id === null ? 'POST' : 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ nome })
    });
    // O exemplo do professor retorna 204 mesmo quando a atualização falha.
    // Confirme o registro antes de mostrar uma mensagem de sucesso.
    const location = id === null ? response.headers.get('Location') : `${api}/${id}`;
    if (!location) throw new Error('Não foi possível confirmar o cadastro. Atualize a lista antes de tentar novamente.');
    const confirmed = await request(location);
    const text = await confirmed.text();
    if (!text || JSON.parse(text).nome !== nome) throw new Error('A alteração não foi confirmada. Atualize a lista e tente novamente.');
    resetForm();
    if (id === null) page = 0;
    message(id === null ? 'Tipo de imóvel cadastrado com sucesso.' : 'Alterações salvas com sucesso.');
    await load();
  });
});

$('cancel').addEventListener('click', () => { resetForm(); $('nome').focus(); });
$('delete-cancel').addEventListener('click', () => $('delete-dialog').close());
$('delete-dialog').addEventListener('cancel', (event) => { if (busy) event.preventDefault(); });
$('delete-confirm').addEventListener('click', () => run(async () => {
  if (!deleting) return;
  const item = deleting;
  try {
    await request(`${api}/${item.id}`, { method: 'DELETE' });
    if (editing === item.id) resetForm();
    deleting = null;
    message('Tipo de imóvel apagado com sucesso.');
    await load();
  } finally { $('delete-dialog').close(); }
}));
$('refresh').addEventListener('click', () => run(load));
$('previous').addEventListener('click', () => run(async () => { page = Math.max(0, page - 1); await load(); }));
$('next').addEventListener('click', () => run(async () => { page = Math.min(pages - 1, page + 1); await load(); }));
$('size').addEventListener('change', () => run(async () => { page = 0; await load(); }));
run(load);
