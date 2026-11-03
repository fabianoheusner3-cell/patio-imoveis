const el = id => document.getElementById(id);
const feedback = text => { el('feedback').textContent = text; };
function atualizarConta() {
  el('status-section').hidden = !sessionStorage.getItem('token');
  el('admin-section').hidden = sessionStorage.getItem('perfil') !== 'ADMIN';
}
async function enviar(path, method, body) {
  const response = await authFetch(path, { method, headers: { 'Content-Type': 'application/json' },
    ...(body ? {body: JSON.stringify(body)} : {}) });
  if (!response.ok) throw new Error('Não foi possível concluir. Confira os dados. HTTP ' + response.status);
  return response.status === 204 ? null : response.json();
}
el('login-form').onsubmit = async event => {
  event.preventDefault();
  try {
    const response = await fetch('/auth/login', { method: 'POST', headers: {'Content-Type':'application/json'},
      body: JSON.stringify({email: el('email').value, senha: el('senha').value}) });
    if (!response.ok) throw new Error('Email ou senha inválidos.');
    const dados = await response.json();
    sessionStorage.setItem('token', dados.token); sessionStorage.setItem('perfil', dados.perfil);
    el('senha').value = ''; atualizarConta(); feedback('Login realizado. Perfil: ' + dados.perfil);
  } catch (error) { feedback(error.message); }
};
el('logout').onclick = () => { sessionStorage.removeItem('token'); sessionStorage.removeItem('perfil');
  el('usuarios').replaceChildren(); atualizarConta(); feedback('Você saiu da conta.'); };
el('status-form').onsubmit = async event => {
  event.preventDefault();
  try { await enviar('/imoveis/' + el('imovel-id').value + '/status', 'PATCH', {status: el('novo-status').value});
    feedback('Status atualizado.'); } catch(error) { feedback(error.message); }
};
el('usuario-form').onsubmit = async event => {
  event.preventDefault();
  const id = el('usuario-id').value;
  const dados = {nome: el('nome-usuario').value, email: el('email-usuario').value, perfil: el('perfil-usuario').value};
  if (el('senha-usuario').value) dados.senha = el('senha-usuario').value;
  try { await enviar('/usuarios' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', dados);
    el('usuario-form').reset(); feedback('Usuário salvo.'); await listar(); } catch(error) { feedback(error.message); }
};
async function listar() {
  const usuarios = await enviar('/usuarios', 'GET');
  el('usuarios').replaceChildren();
  for (const usuario of usuarios) {
    const row = document.createElement('p');
    const texto = document.createElement('span');
    texto.textContent = `${usuario.id} — ${usuario.nome} — ${usuario.email} — ${usuario.perfil} `;
    const editar = document.createElement('button'); editar.textContent = 'Editar';
    editar.onclick = () => { el('usuario-id').value = usuario.id; el('nome-usuario').value = usuario.nome;
      el('email-usuario').value = usuario.email; el('perfil-usuario').value = usuario.perfil; el('senha-usuario').value = ''; };
    const excluir = document.createElement('button'); excluir.textContent = 'Excluir';
    excluir.onclick = async () => {
      if (!confirm('Excluir este usuário?')) return;
      try { await enviar('/usuarios/' + usuario.id, 'DELETE'); await listar(); } catch(error) { feedback(error.message); }
    };
    row.append(texto, editar, excluir); el('usuarios').append(row);
  }
}
el('listar').onclick = () => listar().catch(error => feedback(error.message));
atualizarConta();
