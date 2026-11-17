// Peças de interface usadas em todas as áreas (vitrine, cliente, corretor e administrador).
import React, { useState, useEffect, createContext } from "react";
import {
  ChevronLeft,
  ChevronRight,
  LoaderCircle,
  Search,
  X,
} from "lucide-react";
import { label, normalize } from "./api";

export const Auth = createContext(null);
export const Notice = createContext(null);

// Área inicial de cada perfil.
export const homeFor = (perfil) =>
  ({ ADMIN: "/admin", CORRETOR: "/corretor" })[perfil] || "/cliente";

export function useData(loader, deps = []) {
  const [data, setData] = useState(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(true);
  const [version, setVersion] = useState(0);
  useEffect(() => {
    let active = true;
    setLoading(true);
    setError("");
    Promise.resolve()
      .then(loader)
      .then((x) => {
        if (active) setData(x);
      })
      .catch((e) => {
        if (active) setError(e.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
  }, [...deps, version]);
  return { data, error, loading, reload: () => setVersion((x) => x + 1) };
}

// Espera o usuário parar de digitar para não chamar a API a cada tecla.
export function useDebounced(value, delay = 350) {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(timer);
  }, [value, delay]);
  return debounced;
}

// Estados da tela: carregando, erro (com nova tentativa), vazio ou conteúdo.
// Numa recarga (troca de página, filtro ou ação) a tela anterior fica esmaecida em vez de sumir.
export function State({
  state,
  empty = false,
  emptyTitle = "Sem resultados por aqui",
  emptyText = "Tente outros filtros ou volte mais tarde.",
  children,
}) {
  if (state.loading && (state.data == null || empty))
    return (
      <div className="state" aria-live="polite">
        <LoaderCircle className="spin" /> Carregando…
      </div>
    );
  if (state.error)
    return (
      <div className="state error" role="alert">
        {state.error}
        <button className="btn secondary" onClick={state.reload}>
          Tentar novamente
        </button>
      </div>
    );
  if (empty)
    return (
      <div className="state">
        <Search />
        <h3>{emptyTitle}</h3>
        <p>{emptyText}</p>
      </div>
    );
  return (
    <div
      className={state.loading ? "refreshing" : undefined}
      aria-busy={state.loading || undefined}
    >
      {children}
    </div>
  );
}

export function Status({ value }) {
  return <span className={"badge " + normalize(value)}>{label(value)}</span>;
}

export function Modal({ title, onClose, children }) {
  useEffect(() => {
    const handler = (e) => {
      if (e.key === "Escape") onClose();
    };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);
  return (
    <div className="modal-backdrop" onClick={onClose}>
      <section
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className="modal"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="section-head">
          <h2>{title}</h2>
          <button className="icon-btn" aria-label="Fechar" onClick={onClose}>
            <X />
          </button>
        </div>
        {children}
      </section>
    </div>
  );
}

export function Form({
  onSubmit,
  children,
  submit = "Salvar",
  busyText = "Salvando…",
  initialError = "",
}) {
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(initialError);
  return (
    <form
      onSubmit={async (e) => {
        e.preventDefault();
        const form = e.currentTarget;
        if (!form.reportValidity()) return;
        setBusy(true);
        setError("");
        try {
          await onSubmit(Object.fromEntries(new FormData(form)), form);
        } catch (e) {
          setError(e.message);
        } finally {
          setBusy(false);
        }
      }}
    >
      {error && (
        <p className="alert error" role="alert">
          {error}
        </p>
      )}
      <fieldset disabled={busy}>
        {children}
        <button className="btn" type="submit">
          {busy ? (
            <>
              <LoaderCircle className="spin" />
              {busyText}
            </>
          ) : (
            submit
          )}
        </button>
      </fieldset>
    </form>
  );
}

export function Field({
  name,
  title,
  type = "text",
  value,
  required = true,
  children,
  ...rest
}) {
  const id = React.useId();
  return (
    <div className="field">
      <label htmlFor={id}>{title}</label>
      {children ? (
        <select
          id={id}
          name={name}
          defaultValue={value}
          required={required}
          {...rest}
        >
          {children}
        </select>
      ) : type === "textarea" ? (
        <textarea
          id={id}
          name={name}
          defaultValue={value}
          required={required}
          rows="4"
          {...rest}
        />
      ) : (
        <input
          id={id}
          name={name}
          type={type}
          defaultValue={value ?? ""}
          required={required}
          {...rest}
        />
      )}
    </div>
  );
}

export function PageHeading({ eyebrow, title, description, children }) {
  return (
    <div className="section-head page-heading">
      <div>
        <p className="eyebrow">{eyebrow}</p>
        <h1>{title}</h1>
        {description && <p className="muted">{description}</p>}
      </div>
      {children}
    </div>
  );
}

// O Spring pode devolver os totais na raiz ou dentro de "page", conforme a versão.
export function pageInfo(data) {
  return {
    items: data?.content || [],
    total: data?.page?.totalElements ?? data?.totalElements ?? 0,
    pages: data?.page?.totalPages ?? data?.totalPages ?? 0,
  };
}

// Paginação com no máximo sete números visíveis.
export function Pagination({ page, pages, onChange }) {
  if (pages <= 1) return null;
  const inicio = Math.max(0, Math.min(page - 3, pages - 7));
  const numeros = [];
  for (let n = inicio; n < Math.min(pages, inicio + 7); n++) numeros.push(n);
  return (
    <nav className="pagination" aria-label="Paginação">
      <button
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        aria-label="Página anterior"
      >
        <ChevronLeft size={18} />
      </button>
      {numeros.map((n) => (
        <button
          key={n}
          className={page === n ? "active" : ""}
          aria-current={page === n ? "page" : undefined}
          onClick={() => onChange(n)}
        >
          {n + 1}
        </button>
      ))}
      <button
        disabled={page >= pages - 1}
        onClick={() => onChange(page + 1)}
        aria-label="Próxima página"
      >
        <ChevronRight size={18} />
      </button>
    </nav>
  );
}

// Busca + situação, na mesma linha, acima da lista que eles filtram.
export function ListFilters({ search, onSearch, placeholder, children }) {
  return (
    <div className="list-filters">
      <label className="search-field">
        <Search size={18} />
        <input
          aria-label="Buscar"
          placeholder={placeholder}
          value={search}
          onChange={(e) => onSearch(e.target.value)}
        />
      </label>
      {children}
    </div>
  );
}
