window.authFetch = async function(path, options = {}) {
  const headers = new Headers(options.headers || {});
  const token = sessionStorage.getItem('token');
  if (token) headers.set('Authorization', 'Bearer ' + token);
  const response = await fetch(path, { ...options, headers });
  if (response.status === 401) {
    sessionStorage.removeItem('token');
    sessionStorage.removeItem('perfil');
    throw new Error('Faça login novamente: token ausente, inválido ou expirado.');
  }
  if (response.status === 403) throw new Error('Você não tem permissão para esta ação.');
  return response;
};
window.addEventListener('DOMContentLoaded', () => {
  const header = document.querySelector('header');
  if (!header) return;
  const link = document.createElement('a');
  link.href = '/login.html';
  link.textContent = sessionStorage.getItem('token') ? 'Minha conta / sair' : 'Entrar';
  header.append(link);
});
