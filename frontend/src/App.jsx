import React, {
  useState,
  useEffect,
  useContext,
  createContext,
  useCallback,
} from "react";
import {
  Routes,
  Route,
  Link,
  NavLink,
  Navigate,
  useNavigate,
  useLocation,
  useParams,
} from "react-router-dom";
import {
  Home,
  ArrowUpRight,
  ArrowRight,
  Heart,
  MapPin,
  BedDouble,
  Car,
  Maximize,
  Search,
  SlidersHorizontal,
  ChevronLeft,
  ChevronRight,
  LogOut,
  Menu,
  X,
  LayoutDashboard,
  Building2,
  Users,
  FileText,
  Handshake,
  Camera,
  Plus,
  Pencil,
  Trash2,
  Download,
  Check,
  Mail,
  ShieldCheck,
  LoaderCircle,
} from "lucide-react";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  PieChart,
  Pie,
  Cell,
} from "recharts";
import { api, API, money, date, label, available, normalize } from "./api";
const Auth = createContext(null),
  Notice = createContext(null);
function useData(loader, deps = []) {
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
function State({ state, empty = false, children }) {
  if (state.loading)
    return (
      <div className="state">
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
        <h3>Sem resultados por aqui</h3>
        <p>Tente outros filtros ou volte mais tarde.</p>
      </div>
    );
  return children;
}
function Status({ value }) {
  return <span className={"badge " + normalize(value)}>{label(value)}</span>;
}
function Modal({ title, onClose, children }) {
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
function Form({ onSubmit, children, submit = "Salvar", initialError = "" }) {
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
              Salvando…
            </>
          ) : (
            submit
          )}
        </button>
      </fieldset>
    </form>
  );
}
function Field({
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
function Protected({ role, children }) {
  const { user, loading } = useContext(Auth);
  const location = useLocation();
  if (loading) return <div className="state">Verificando sessão…</div>;
  if (!user)
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  if (user.perfil !== role)
    return (
      <div className="state error">
        <ShieldCheck />
        <h2>Acesso negado</h2>
        <Link to="/">Voltar à vitrine</Link>
      </div>
    );
  return children;
}
function Shell() {
  const { user, logout } = useContext(Auth),
    [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);
  const admin = location.pathname.startsWith("/admin"),
    client = location.pathname.startsWith("/cliente");
  const links =
    user?.perfil === "ADMIN"
      ? [
          ["/admin", "Painel", LayoutDashboard],
          ["/admin/imoveis", "Imóveis", Building2],
          ["/admin/bairros", "Bairros", MapPin],
          ["/admin/tipos", "Tipos de imóveis", Home],
          ["/admin/fotos", "Fotos", Camera],
          ["/admin/usuarios", "Usuários", Users],
          ["/admin/ofertas", "Ofertas", Handshake],
          ["/admin/contratos", "Contratos", FileText],
        ]
      : [
          ["/cliente", "Minhas ofertas", Handshake],
          ["/cliente/favoritos", "Favoritos", Heart],
          ["/cliente/contratos", "Contratos", FileText],
        ];
  return (
    <>
      <header className="topbar">
        <Link to="/" className="brand">
          <span className="brand-mark">
            <Home size={23} />
          </span>
          morada<span className="brand-dot">.</span>
        </Link>
        <button
          className="mobile-toggle icon-btn"
          onClick={() => setOpen(!open)}
          aria-label="Abrir menu"
        >
          {open ? <X /> : <Menu />}
        </button>
        <nav className={open ? "open" : ""}>
          <NavLink to="/">Encontrar um imóvel</NavLink>
          <Link to="/#vitrine">
            Fale com a gente <ArrowUpRight size={15} />
          </Link>
          {user ? (
            <>
              <Link
                className="nav-account"
                to={user.perfil === "ADMIN" ? "/admin" : "/cliente"}
              >
                {user.nome.split(" ")[0]} · {label(user.perfil)}
              </Link>
              <button className="icon-btn" onClick={logout} aria-label="Sair">
                <LogOut size={18} />
              </button>
            </>
          ) : (
            <Link className="btn small" to="/login">
              Minha conta <ArrowUpRight size={16} />
            </Link>
          )}
        </nav>
      </header>
      {admin || client ? (
        <div className="workspace">
          <aside className="sidebar">
            <p className="eyebrow">
              {admin ? "GESTÃO DA IMOBILIÁRIA" : "SEU ESPAÇO"}
            </p>
            {links.map(([url, text, Icon]) => (
              <NavLink key={url} to={url} end>
                <Icon size={18} />
                {text}
              </NavLink>
            ))}
            <div className="sidebar-note">
              <ShieldCheck />
              <span>
                Seu próximo capítulo
                <br />
                começa com confiança.
              </span>
            </div>
          </aside>
          <main className="workspace-main">
            <Routes>
              <Route
                path="/admin"
                element={
                  <Protected role="ADMIN">
                    <Dashboard />
                  </Protected>
                }
              />
              <Route
                path="/admin/:resource"
                element={
                  <Protected role="ADMIN">
                    <AdminPage />
                  </Protected>
                }
              />
              <Route
                path="/cliente"
                element={
                  <Protected role="USER">
                    <Offers />
                  </Protected>
                }
              />
              <Route
                path="/cliente/favoritos"
                element={
                  <Protected role="USER">
                    <Favorites />
                  </Protected>
                }
              />
              <Route
                path="/cliente/contratos"
                element={
                  <Protected role="USER">
                    <Contracts />
                  </Protected>
                }
              />
            </Routes>
          </main>
        </div>
      ) : (
        <Routes>
          <Route path="/" element={<Catalog />} />
          <Route path="/imoveis/:id" element={<Detail />} />
          <Route path="/login" element={<Login />} />
          <Route
            path="*"
            element={
              <div className="state">
                <h1>Página não encontrada</h1>
                <Link to="/">Voltar ao início</Link>
              </div>
            }
          />
        </Routes>
      )}
      <footer>
        <Link className="brand" to="/">
          morada.
        </Link>
        <span>Um lugar para cada novo começo.</span>
        <span>Imobiliária · Panambi e região</span>
      </footer>
    </>
  );
}
export default function App() {
  const [user, setUser] = useState(null),
    [loading, setLoading] = useState(true),
    [notice, setNotice] = useState("");
  const navigate = useNavigate();
  const notify = useCallback((text) => setNotice(text), []);
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(""), 6500);
    return () => clearTimeout(timer);
  }, [notice]);
  const logout = () => {
    sessionStorage.removeItem("morada-token");
    setUser(null);
    navigate("/login");
  };
  useEffect(() => {
    let active = true;
    if (sessionStorage.getItem("morada-token"))
      api("/auth/me")
        .then((u) => {
          if (active) setUser(u);
        })
        .catch(() => {
          sessionStorage.removeItem("morada-token");
        })
        .finally(() => {
          if (active) setLoading(false);
        });
    else setLoading(false);
    const expired = () => {
      setUser(null);
      notify("Sua sessão expirou. Entre novamente.");
      navigate("/login", { replace: true });
    };
    window.addEventListener("sessao-expirada", expired);
    return () => {
      active = false;
      window.removeEventListener("sessao-expirada", expired);
    };
  }, []);
  return (
    <Auth.Provider value={{ user, setUser, loading, logout }}>
      <Notice.Provider value={notify}>
        <Shell />
        {notice && (
          <div className="toast" role="status">
            {notice}
            <button aria-label="Fechar aviso" onClick={() => setNotice("")}>
              <X size={16} />
            </button>
          </div>
        )}
      </Notice.Provider>
    </Auth.Provider>
  );
}
function Login() {
  const { user, setUser } = useContext(Auth),
    navigate = useNavigate();
  if (user)
    return (
      <Navigate to={user.perfil === "ADMIN" ? "/admin" : "/cliente"} replace />
    );
  return (
    <main className="login-page">
      <section className="login-story">
        <p className="eyebrow">BEM-VINDO À MORADA</p>
        <h1>
          Seu espaço.
          <br />
          Suas possibilidades.
        </h1>
        <p>
          Acompanhe suas propostas, guarde seus imóveis favoritos e dê o próximo
          passo com tranquilidade.
        </p>
        <div className="login-house">
          <Home size={140} strokeWidth={0.7} />
        </div>
        <span>Mais perto do seu novo endereço.</span>
      </section>
      <section className="login-form">
        <p className="eyebrow">MINHA CONTA</p>
        <h2>Que bom ter você aqui.</h2>
        <p className="muted">Entre para continuar sua jornada.</p>
        <Form
          submit="Entrar na minha conta →"
          onSubmit={async (dados) => {
            const token = await api("/auth/login", {
              method: "POST",
              body: dados,
            });
            sessionStorage.setItem("morada-token", token.token);
            try {
              const me = await api("/auth/me");
              setUser(me);
              navigate(me.perfil === "ADMIN" ? "/admin" : "/cliente", {
                replace: true,
              });
            } catch (e) {
              sessionStorage.removeItem("morada-token");
              throw e;
            }
          }}
        >
          <Field
            name="email"
            title="Email"
            type="email"
            autoComplete="username"
            placeholder="voce@exemplo.com"
          />
          <Field
            name="senha"
            title="Senha"
            type="password"
            autoComplete="current-password"
          />
          <p className="hint">
            Ainda não tem acesso? Solicite seu cadastro à imobiliária.
          </p>
        </Form>
        <Link className="back-link" to="/">
          ← Voltar para os imóveis
        </Link>
      </section>
    </main>
  );
}
function Card({ item, onFavorite, favorited = false, purpose }) {
  const rental = purpose === "ALUGUEL" || !item.precoVenda;
  const photos = useData(() => api("/fotos/imovel/" + item.id), [item.id]);
  const photo = photos.data?.find((p) => p.capa) || photos.data?.[0];
  return (
    <article className="property-card">
      <Link to={"/imoveis/" + item.id} className="card-image">
        {photo ? (
          <img
            src={API + "/fotos/" + photo.id + "/arquivo"}
            alt={item.titulo}
            loading="lazy"
          />
        ) : (
          <div className="photo-placeholder">
            <Home size={48} strokeWidth={1} />
            <span>Seu próximo endereço</span>
          </div>
        )}
        <span className="photo-tag">{item.finalidade || "Imóvel"}</span>
      </Link>
      {onFavorite && (
        <button
          className={"favorite-button " + (favorited ? "selected" : "")}
          onClick={() => onFavorite(item)}
          aria-label={
            favorited ? "Remover dos favoritos" : "Adicionar aos favoritos"
          }
        >
          <Heart size={19} fill={favorited ? "currentColor" : "none"} />
        </button>
      )}
      <div className="card-content">
        <p className="card-type">
          {item.tipoImovel?.nome || "Imóvel"} <span>· Cód. {item.id}</span>
        </p>
        <Link to={"/imoveis/" + item.id}>
          <h3>{item.titulo}</h3>
        </Link>
        <p className="location">
          <MapPin size={14} />
          {item.bairro?.nome || "Bairro não informado"}
          {item.cidade && " · " + item.cidade}
        </p>
        <div className="features">
          <span>
            <BedDouble size={16} />
            {item.dormitorios || 0} quartos
          </span>
          <span>
            <Car size={16} />
            {item.garagem || 0} vagas
          </span>
          <span>
            <Maximize size={15} />
            {item.areaTotal || "—"} m²
          </span>
        </div>
        <div className="card-bottom">
          <div>
            <small>{rental ? "Aluguel mensal" : "Valor de venda"}</small>
            <strong>
              {money(rental ? item.precoAluguel : item.precoVenda)}
              {rental && <small> /mês</small>}
            </strong>
          </div>
          <Link
            to={"/imoveis/" + item.id}
            className="round-link"
            aria-label="Ver imóvel"
          >
            <ArrowUpRight size={21} />
          </Link>
        </div>
        {item.precoVenda && item.precoAluguel && (
          <p className="hint">
            Também para aluguel: {money(item.precoAluguel)}/mês
          </p>
        )}
      </div>
    </article>
  );
}
function Catalog() {
  const all = useData(() =>
    Promise.all([api("/imoveis"), api("/bairros")]).then(
      ([items, bairros]) => ({ items, bairros }),
    ),
  );
  const { user } = useContext(Auth),
    navigate = useNavigate(),
    notify = useContext(Notice);
  const favorites = useData(
    () => (user?.perfil === "USER" ? api("/favoritos") : Promise.resolve([])),
    [user?.id],
  );
  const [filter, setFilter] = useState({
      bairro: "",
      finalidade: "",
      min: "",
      max: "",
      search: "",
      sort: "recentes",
    }),
    [page, setPage] = useState(0);
  const change = (key, value) => {
    setFilter((x) => ({ ...x, [key]: value }));
    setPage(0);
  };
  let items = (all.data?.items || [])
    .filter(available)
    .filter((i) => !filter.bairro || String(i.bairro?.id) === filter.bairro)
    .filter(
      (i) =>
        !filter.search ||
        normalize(i.titulo + " " + i.cidade + " " + i.bairro?.nome).includes(
          normalize(filter.search),
        ),
    )
    .filter((i) => {
      const prices =
        filter.finalidade === "ALUGUEL"
          ? [i.precoAluguel]
          : filter.finalidade === "VENDA"
            ? [i.precoVenda]
            : [i.precoVenda, i.precoAluguel];
      return prices.some(
        (p) =>
          p > 0 &&
          (!filter.min || p >= Number(filter.min)) &&
          (!filter.max || p <= Number(filter.max)),
      );
    });
  const price = (i) =>
    filter.finalidade === "ALUGUEL"
      ? i.precoAluguel
      : i.precoVenda || i.precoAluguel;
  items.sort((a, b) =>
    filter.sort === "menor"
      ? price(a) - price(b)
      : filter.sort === "maior"
        ? price(b) - price(a)
        : b.id - a.id,
  );
  const pages = Math.ceil(items.length / 6),
    shown = items.slice(page * 6, page * 6 + 6);
  async function favorite(i) {
    if (!user) {
      navigate("/login");
      return;
    }
    if (user.perfil !== "USER") {
      notify("Favoritos são exclusivos da área do cliente.");
      return;
    }
    try {
      const exists = favorites.data?.some((f) => f.imovel.id === i.id);
      await api("/favoritos/" + i.id, { method: exists ? "DELETE" : "POST" });
      favorites.reload();
      notify(
        exists
          ? "Imóvel removido dos favoritos."
          : "Imóvel adicionado aos favoritos.",
      );
    } catch (e) {
      notify(e.message);
    }
  }
  return (
    <>
      <section className="hero">
        <div className="hero-content">
          <p className="eyebrow">
            <span className="tiny-line" /> ENCONTRE SEU LUGAR NO MUNDO
          </p>
          <h1>
            Um novo endereço.
            <br />
            Um novo <em>começo.</em>
          </h1>
          <p>
            Espaços para viver seus planos. Encontre o imóvel
            <br className="desktop" /> que combina com a sua próxima história.
          </p>
          <a className="hero-link" href="#vitrine">
            Explore os imóveis <ArrowDown />
          </a>
          <div className="hero-meta">
            <span>
              <ShieldCheck size={17} />
              Negociações com confiança
            </span>
            <span>
              <MapPin size={17} />
              Panambi e região
            </span>
          </div>
        </div>
        <div className="hero-visual">
          <div className="house-scene">
            <div className="sun" />
            <div className="house-back" />
            <div className="house-front">
              <div className="window" />
              <div className="door" />
            </div>
            <div className="garden" />
            <div className="tree tree-one" />
            <div className="tree tree-two" />
          </div>
          <div className="visual-caption">
            <span>O lugar certo para chamar de lar.</span>
            <ArrowUpRight size={22} />
          </div>
          <span className="visual-number">01 / MORAR BEM</span>
        </div>
      </section>
      <section className="catalog section" id="vitrine">
        <div className="section-head">
          <div>
            <p className="eyebrow">ESCOLHIDOS PARA VOCÊ</p>
            <h2>Encontre sua próxima morada</h2>
          </div>
          <span className="catalog-count">
            {all.loading
              ? "Buscando imóveis…"
              : `${items.length} imóveis disponíveis`}
          </span>
        </div>
        <div className="filters">
          <label className="search-field">
            <Search size={18} />
            <input
              aria-label="Buscar imóvel"
              placeholder="Busque por imóvel ou cidade"
              value={filter.search}
              onChange={(e) => change("search", e.target.value)}
            />
          </label>
          <select
            aria-label="Bairro"
            value={filter.bairro}
            onChange={(e) => change("bairro", e.target.value)}
          >
            <option value="">Todos os bairros</option>
            {all.data?.bairros.map((b) => (
              <option value={b.id} key={b.id}>
                {b.nome}
              </option>
            ))}
          </select>
          <select
            aria-label="Finalidade"
            value={filter.finalidade}
            onChange={(e) => change("finalidade", e.target.value)}
          >
            <option value="">Comprar ou alugar</option>
            <option value="VENDA">Comprar</option>
            <option value="ALUGUEL">Alugar</option>
          </select>
          <input
            aria-label="Preço mínimo"
            type="number"
            min="0"
            placeholder="Mínimo R$"
            value={filter.min}
            onChange={(e) => change("min", e.target.value)}
          />
          <input
            aria-label="Preço máximo"
            type="number"
            min="0"
            placeholder="Máximo R$"
            value={filter.max}
            onChange={(e) => change("max", e.target.value)}
          />
          <button
            className="icon-btn"
            title="Limpar filtros"
            onClick={() => {
              setFilter({
                bairro: "",
                finalidade: "",
                min: "",
                max: "",
                search: "",
                sort: "recentes",
              });
              setPage(0);
            }}
          >
            <X size={18} />
          </button>
        </div>
        <div className="results-toolbar">
          <p>
            <SlidersHorizontal size={15} /> Seu próximo capítulo está aqui
          </p>
          <select
            aria-label="Ordenar imóveis"
            value={filter.sort}
            onChange={(e) => change("sort", e.target.value)}
          >
            <option value="recentes">Mais recentes</option>
            <option value="menor">Menor preço</option>
            <option value="maior">Maior preço</option>
          </select>
        </div>
        {filter.min &&
          filter.max &&
          Number(filter.min) > Number(filter.max) && (
            <p className="alert error">
              O preço mínimo deve ser menor ou igual ao máximo.
            </p>
          )}
        <State state={all} empty={!items.length}>
          <div className="property-grid">
            {shown.map((i) => (
              <Card
                key={i.id}
                item={i}
                purpose={filter.finalidade}
                onFavorite={favorite}
                favorited={favorites.data?.some((f) => f.imovel.id === i.id)}
              />
            ))}
          </div>
          <div className="pagination">
            <button
              disabled={page === 0}
              onClick={() => setPage((x) => x - 1)}
              aria-label="Página anterior"
            >
              <ChevronLeft size={18} />
            </button>
            {Array.from({ length: pages }, (_, n) => (
              <button
                key={n}
                className={page === n ? "active" : ""}
                onClick={() => setPage(n)}
              >
                {n + 1}
              </button>
            ))}
            <button
              disabled={page >= pages - 1}
              onClick={() => setPage((x) => x + 1)}
              aria-label="Próxima página"
            >
              <ChevronRight size={18} />
            </button>
          </div>
        </State>
      </section>
      <section className="contact-strip">
        <div>
          <p className="eyebrow">CADA HISTÓRIA TEM UM ENDEREÇO</p>
          <h2>Vamos encontrar o seu?</h2>
          <p>Conte com a gente em cada passo da sua escolha.</p>
        </div>
        <a className="btn light" href="#vitrine">
          Encontre um imóvel e fale com a gente <ArrowUpRight size={18} />
        </a>
      </section>
    </>
  );
}
function ArrowDown() {
  return <span>↓</span>;
}
function Detail() {
  const { id } = useParams(),
    { user } = useContext(Auth),
    notify = useContext(Notice);
  const state = useData(
    () =>
      Promise.all([api("/imoveis/" + id), api("/fotos/imovel/" + id)]).then(
        ([item, photos]) => ({ item, photos }),
      ),
    [id],
  );
  const [offer, setOffer] = useState(false),
    [contact, setContact] = useState(false),
    [photo, setPhoto] = useState(0);
  const i = state.data?.item;
  return (
    <main className="section detail">
      <Link className="back-link" to="/">
        ← Voltar para os imóveis
      </Link>
      <State state={state}>
        {i && (
          <>
            <div className="section-head">
              <div>
                <p className="eyebrow">
                  {i.tipoImovel?.nome} · CÓD. {i.id}
                </p>
                <h1>{i.titulo}</h1>
                <p className="location">
                  <MapPin size={17} />
                  {[i.endereco, i.numero, i.bairro?.nome, i.cidade]
                    .filter(Boolean)
                    .join(" · ")}
                </p>
              </div>
              <Status value={i.status} />
            </div>
            <div className="detail-layout">
              <div>
                <div className="detail-image">
                  {state.data.photos.length ? (
                    <img
                      src={
                        API +
                        "/fotos/" +
                        state.data.photos[photo]?.id +
                        "/arquivo"
                      }
                      alt={i.titulo}
                    />
                  ) : (
                    <div className="photo-placeholder">
                      <Home size={90} strokeWidth={1} />
                      <p>Foto ainda não cadastrada</p>
                    </div>
                  )}
                </div>
                {state.data.photos.length > 1 && (
                  <div className="thumbnails">
                    {state.data.photos.map((p, n) => (
                      <button
                        key={p.id}
                        onClick={() => setPhoto(n)}
                        aria-label={"Ver foto " + (n + 1)}
                      >
                        <img
                          src={API + "/fotos/" + p.id + "/arquivo"}
                          alt={"Foto " + (n + 1)}
                        />
                      </button>
                    ))}
                  </div>
                )}
                <div className="features detail-features">
                  <span>
                    <BedDouble />
                    {i.dormitorios || 0} quartos
                  </span>
                  <span>
                    <Car />
                    {i.garagem || 0} vagas
                  </span>
                  <span>
                    <Maximize />
                    {i.areaTotal || "—"} m²
                  </span>
                  <span>{i.banheiros || 0} banheiros</span>
                </div>
                <h2>Um pouco sobre este lugar</h2>
                <p className="description">
                  {i.descricao ||
                    "Entre em contato para conhecer mais detalhes deste imóvel."}
                </p>
                <h3>Características</h3>
                <p>{i.caracteristicas || "Consulte a imobiliária."}</p>
                <p>
                  Área construída: {i.areaConstruida || "—"} m² · CEP{" "}
                  {i.cep || "—"}
                </p>
              </div>
              <aside className="price-panel">
                <p className="eyebrow">SEU PRÓXIMO PASSO</p>
                {i.precoVenda > 0 && (
                  <>
                    <p>Valor de venda</p>
                    <h2>{money(i.precoVenda)}</h2>
                  </>
                )}
                {i.precoAluguel > 0 && (
                  <>
                    <p>Aluguel mensal</p>
                    <h2>
                      {money(i.precoAluguel)}
                      <small>/mês</small>
                    </h2>
                  </>
                )}
                <button className="btn" onClick={() => setContact(true)}>
                  <Mail size={18} />
                  Tenho interesse
                </button>
                {available(i) &&
                  (user?.perfil === "USER" ? (
                    <button
                      className="btn secondary"
                      onClick={() => setOffer(true)}
                    >
                      <Handshake size={18} />
                      Fazer oferta
                    </button>
                  ) : !user ? (
                    <Link className="btn secondary" to="/login">
                      Entre para fazer uma oferta
                    </Link>
                  ) : null)}
                {user?.perfil === "USER" && (
                  <button
                    className="text-button"
                    onClick={async () => {
                      try {
                        await api("/favoritos/" + id, { method: "POST" });
                        notify("Imóvel salvo nos favoritos.");
                      } catch (e) {
                        notify(e.message);
                      }
                    }}
                  >
                    <Heart size={17} />
                    Salvar nos favoritos
                  </button>
                )}
                <p className="hint">
                  <ShieldCheck size={15} />
                  Sua proposta é acompanhada pela nossa equipe.
                </p>
              </aside>
            </div>
            {offer && (
              <Modal title="Sua proposta" onClose={() => setOffer(false)}>
                <p>
                  {i.titulo} · Código {i.id}
                </p>
                <Form
                  submit="Enviar oferta"
                  onSubmit={async (d) => {
                    await api("/ofertas", {
                      method: "POST",
                      body: {
                        ...d,
                        imovelId: Number(id),
                        valorProposto: Number(d.valorProposto),
                      },
                    });
                    setOffer(false);
                    notify("Oferta enviada! Acompanhe em Minhas ofertas.");
                  }}
                >
                  <Field
                    name="tipo"
                    title="Tipo de oferta"
                    value={i.precoVenda ? "VENDA" : "ALUGUEL"}
                  >
                    {i.precoVenda > 0 && <option value="VENDA">Compra</option>}
                    {i.precoAluguel > 0 && (
                      <option value="ALUGUEL">Aluguel</option>
                    )}
                  </Field>
                  <Field
                    name="valorProposto"
                    title="Valor proposto (R$)"
                    type="number"
                    min="0.01"
                    step="0.01"
                    max="999999999999.99"
                  />
                  <Field
                    name="mensagem"
                    title="Mensagem"
                    type="textarea"
                    maxLength="4000"
                  />
                </Form>
              </Modal>
            )}
            {contact && (
              <Modal
                title="Vamos conversar sobre este imóvel"
                onClose={() => setContact(false)}
              >
                <p className="hint">
                  Envie seu interesse para a imobiliária. Os dados do imóvel já
                  estão preenchidos.
                </p>
                <Form
                  submit="Enviar interesse por email"
                  onSubmit={async (d) => {
                    await api("/contato/" + id, { method: "POST", body: d });
                    setContact(false);
                    notify("Interesse enviado à imobiliária. Obrigado!");
                  }}
                >
                  <Field
                    name="nome"
                    title="Seu nome"
                    value={user?.nome}
                    maxLength="150"
                  />
                  <Field
                    name="email"
                    title="Seu email"
                    type="email"
                    value={user?.email}
                  />
                  <Field
                    name="assunto"
                    title="Assunto"
                    value={`Interesse no imóvel #${i.id} · ${i.tipoImovel?.nome} · ${i.bairro?.nome}`}
                    maxLength="200"
                  />
                  <Field
                    name="mensagem"
                    title="Mensagem"
                    type="textarea"
                    maxLength="4000"
                    value={`Olá! Tenho interesse no imóvel #${i.id}, tipo ${i.tipoImovel?.nome}, bairro ${i.bairro?.nome}, ${i.precoVenda ? "venda por " + money(i.precoVenda) : ""}${i.precoAluguel ? " aluguel por " + money(i.precoAluguel) : ""}. Gostaria de mais informações.`}
                  />
                </Form>
              </Modal>
            )}
          </>
        )}
      </State>
    </main>
  );
}
function Offers({ admin = false }) {
  const [filter, setFilter] = useState(""),
    state = useData(
      () => api("/ofertas" + (filter ? "?status=" + filter : "")),
      [filter],
    ),
    notify = useContext(Notice),
    [busy, setBusy] = useState(null);
  async function decidir(id, acao) {
    if (!confirm(`${acao === "aceitar" ? "Aceitar" : "Recusar"} esta oferta?`))
      return;
    setBusy(id);
    try {
      await api(`/ofertas/${id}/${acao}`, { method: "POST" });
      state.reload();
      notify(
        acao === "aceitar"
          ? "Oferta aceita. O contrato foi criado e o imóvel reservado."
          : "Oferta recusada.",
      );
    } catch (e) {
      notify(e.message);
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={admin ? "NEGOCIAÇÕES" : "ÁREA DO CLIENTE"}
        title={admin ? "Gestão de ofertas" : "Minhas ofertas"}
        description="Cada proposta é um passo mais perto do seu novo endereço."
      />
      {admin && (
        <select
          aria-label="Filtrar ofertas por situação"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
        >
          <option value="">Todas as situações</option>
          {["PENDENTE", "ACEITA", "EM_ESPERA", "RECUSADA", "CANCELADA"].map(
            (s) => (
              <option key={s}>{s}</option>
            ),
          )}
        </select>
      )}
      <State state={state} empty={!state.data?.length}>
        <div className="list-stack">
          {state.data?.map((o) => (
            <article className="list-card" key={o.id}>
              <div>
                <p className="eyebrow">
                  OFERTA #{o.id} · {label(o.tipo)}
                </p>
                <Link to={"/imoveis/" + o.imovel.id}>
                  <h3>{o.imovel.titulo}</h3>
                </Link>
                {admin && (
                  <p>
                    {o.usuario.nome} · {o.usuario.email}
                  </p>
                )}
                <p className="muted">{o.mensagem}</p>
                <small>{date(o.dataCriacao)}</small>
              </div>
              <div className="list-actions">
                <Status value={o.status} />
                <strong>{money(o.valorProposto)}</strong>
                {admin && ["PENDENTE", "EM_ESPERA"].includes(o.status) && (
                  <div className="actions">
                    <button
                      disabled={busy === o.id}
                      className="btn small"
                      onClick={() => decidir(o.id, "aceitar")}
                    >
                      Aceitar
                    </button>
                    <button
                      disabled={busy === o.id}
                      className="btn small secondary"
                      onClick={() => decidir(o.id, "recusar")}
                    >
                      Recusar
                    </button>
                  </div>
                )}
              </div>
            </article>
          ))}
        </div>
      </State>
    </>
  );
}
function Favorites() {
  const state = useData(() => api("/favoritos")),
    notify = useContext(Notice);
  return (
    <>
      <PageHeading
        eyebrow="SUA SELEÇÃO"
        title="Meus favoritos"
        description="Os lugares que você quer guardar por perto."
      />
      <State state={state} empty={!state.data?.length}>
        <div className="property-grid compact">
          {state.data?.map((f) => (
            <Card
              key={f.id}
              item={f.imovel}
              favorited
              onFavorite={async () => {
                try {
                  await api("/favoritos/" + f.imovel.id, { method: "DELETE" });
                  state.reload();
                } catch (e) {
                  notify(e.message);
                }
              }}
            />
          ))}
        </div>
      </State>
    </>
  );
}
function PageHeading({ eyebrow, title, description, children }) {
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
function Contracts({ admin = false }) {
  const state = useData(() => api("/contratos")),
    notify = useContext(Notice),
    [sign, setSign] = useState(null),
    [busy, setBusy] = useState(null);
  async function action(c, acao) {
    if (
      ["cancelar", "recusar"].includes(acao) &&
      !confirm("Cancelar este contrato e liberar o imóvel para novas ofertas?")
    )
      return;
    setBusy(c.id);
    try {
      const res = await api(`/contratos/${c.id}/${acao}`, { method: "POST" });
      notify(res.mensagem || "Contrato atualizado.");
      state.reload();
    } catch (e) {
      notify(e.message);
    } finally {
      setBusy(null);
    }
  }
  async function download(c) {
    try {
      const blob = await api(`/contratos/${c.id}/pdf`, { download: true });
      const url = URL.createObjectURL(blob),
        a = document.createElement("a");
      a.href = url;
      a.download = `contrato-${c.id}.pdf`;
      a.click();
      setTimeout(() => URL.revokeObjectURL(url), 1000);
    } catch (e) {
      notify(e.message);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={admin ? "DOCUMENTOS" : "ÁREA DO CLIENTE"}
        title={admin ? "Gestão de contratos" : "Meus contratos"}
        description="Tudo organizado para o próximo passo."
      />
      <State state={state} empty={!state.data?.length}>
        <div className="list-stack">
          {state.data?.map((c) => (
            <article key={c.id} className="list-card">
              <div>
                <p className="eyebrow">
                  CONTRATO #{c.id} · OFERTA #{c.oferta.id}
                </p>
                <h3>{c.oferta.imovel.titulo}</h3>
                <p>
                  {c.oferta.usuario.nome} · {label(c.oferta.tipo)} ·{" "}
                  {money(c.oferta.valorProposto)}
                </p>
                <p className="hint">
                  Aprovação: {date(c.dataAprovacao)}
                  <br />
                  Envio: {date(c.dataEnvio)}
                  <br />
                  Assinatura: {date(c.dataAssinatura)}
                </p>
              </div>
              <div className="list-actions">
                <Status value={c.status} />
                <div className="actions wrap">
                  <button
                    className="btn small secondary"
                    onClick={() => download(c)}
                  >
                    <Download size={15} />
                    PDF
                  </button>
                  {admin && c.status === "AGUARDANDO_ASSINATURA" && (
                    <>
                      <button
                        disabled={busy === c.id || !!c.dataAprovacao}
                        className="btn small"
                        onClick={() => action(c, "aprovar")}
                      >
                        Aprovar
                      </button>
                      <button
                        disabled={busy === c.id || !c.dataAprovacao}
                        className="btn small secondary"
                        onClick={() => action(c, "enviar")}
                      >
                        <Mail size={15} />
                        {c.dataEnvio ? "Reenviar" : "Enviar ao cliente"}
                      </button>
                      <button
                        disabled={busy === c.id}
                        className="btn small danger"
                        onClick={() => action(c, "recusar")}
                      >
                        Recusar
                      </button>
                    </>
                  )}
                  {admin && c.status === "ASSINADO" && (
                    <button
                      disabled={busy === c.id}
                      className="btn small danger"
                      onClick={() => action(c, "cancelar")}
                    >
                      Cancelar contrato
                    </button>
                  )}
                  {!admin &&
                    c.status === "AGUARDANDO_ASSINATURA" &&
                    c.dataAprovacao &&
                    c.dataEnvio && (
                      <button className="btn small" onClick={() => setSign(c)}>
                        Assinar contrato
                      </button>
                    )}
                </div>
                {!admin &&
                  c.status === "AGUARDANDO_ASSINATURA" &&
                  !c.dataEnvio && (
                    <p className="hint">
                      Aguarde a aprovação e o envio pela imobiliária.
                    </p>
                  )}
              </div>
            </article>
          ))}
        </div>
      </State>
      {sign && (
        <Modal
          title={"Assinar contrato #" + sign.id}
          onClose={() => setSign(null)}
        >
          <p>
            Baixe e leia o PDF antes de confirmar. A assinatura é registrada com
            sua conta autenticada.
          </p>
          <button className="text-button" onClick={() => download(sign)}>
            Baixar contrato
          </button>
          <Form
            submit="Confirmar assinatura"
            onSubmit={async (d) => {
              await api(`/contratos/${sign.id}/assinar`, {
                method: "POST",
                body: { nome: d.nome, concordo: d.concordo === "on" },
              });
              setSign(null);
              state.reload();
              notify("Contrato assinado com sucesso.");
            }}
          >
            <Field name="nome" title="Seu nome completo, conforme o cadastro" />
            <label className="check">
              <input name="concordo" type="checkbox" required />
              Li e concordo com o contrato.
            </label>
          </Form>
        </Modal>
      )}
    </>
  );
}
function Dashboard() {
  const state = useData(() => api("/painel"));
  const colors = ["#214f43", "#cb9a65", "#789887", "#d3bd9c", "#8d818d"];
  return (
    <>
      <PageHeading
        eyebrow="VISÃO GERAL"
        title="Bem-vindo à sua gestão"
        description="Acompanhe os números e as oportunidades da imobiliária."
      />
      <State state={state}>
        {state.data && (
          <>
            <div className="stats">
              {[
                [Building2, "Imóveis disponíveis", state.data.disponiveis],
                [Handshake, "Ofertas pendentes", state.data.pendentes],
                [FileText, "Aguardando assinatura", state.data.aguardando],
              ].map(([Icon, text, value]) => (
                <div className="stat" key={text}>
                  <Icon />
                  <p>{text}</p>
                  <strong>{value}</strong>
                  <span>Visão atual da imobiliária</span>
                </div>
              ))}
            </div>
            <div className="charts">
              {[
                ["Imóveis por bairro", "porBairro"],
                ["Ofertas por situação", "porSituacao"],
                ["Imóveis por finalidade", "porFinalidade"],
              ].map(([title, key], index) => (
                <section className="chart-card" key={key}>
                  <h3>{title}</h3>
                  {!state.data[key].length ? (
                    <div className="state">Sem resultados para exibir.</div>
                  ) : (
                    <ResponsiveContainer width="100%" height={260}>
                      {index === 1 ? (
                        <PieChart>
                          <Pie
                            data={state.data[key]}
                            dataKey="total"
                            nameKey="nome"
                            innerRadius={55}
                            outerRadius={85}
                            label
                          >
                            {state.data[key].map((_, n) => (
                              <Cell key={n} fill={colors[n % colors.length]} />
                            ))}
                          </Pie>
                          <Tooltip formatter={(v, n) => [v, label(n)]} />
                        </PieChart>
                      ) : (
                        <BarChart
                          data={state.data[key]}
                          margin={{ top: 15, right: 10, bottom: 15, left: -20 }}
                        >
                          <XAxis dataKey="nome" fontSize={11} />
                          <YAxis allowDecimals={false} />
                          <Tooltip />
                          <Bar
                            dataKey="total"
                            name="Imóveis"
                            fill={colors[index]}
                            radius={[5, 5, 0, 0]}
                          />
                        </BarChart>
                      )}
                    </ResponsiveContainer>
                  )}
                </section>
              ))}
            </div>
          </>
        )}
      </State>
    </>
  );
}
function AdminPage() {
  const { resource } = useParams();
  if (resource === "ofertas") return <Offers admin />;
  if (resource === "contratos") return <Contracts admin />;
  if (resource === "fotos") return <Photos />;
  if (["imoveis", "bairros", "tipos", "usuarios"].includes(resource))
    return <Crud key={resource} resource={resource} />;
  return <div className="state">Página não encontrada.</div>;
}
const paths = {
  imoveis: "/imoveis",
  bairros: "/bairros",
  tipos: "/tipos-imoveis",
  usuarios: "/usuarios",
};
function Crud({ resource }) {
  const path = paths[resource],
    state = useData(() => api(path)),
    options = useData(() =>
      resource === "imoveis"
        ? Promise.all([api("/tipos-imoveis"), api("/bairros")])
        : Promise.resolve([[], []]),
    ),
    [editing, setEditing] = useState(null),
    [search, setSearch] = useState(""),
    [busy, setBusy] = useState(null);
  const { user } = useContext(Auth),
    notify = useContext(Notice);
  const items = (state.data || []).filter((i) =>
    normalize((i.nome || i.titulo) + " " + (i.email || "")).includes(
      normalize(search),
    ),
  );
  const names = {
    imoveis: "Imóveis",
    bairros: "Bairros",
    tipos: "Tipos de imóveis",
    usuarios: "Usuários",
  };
  async function remove(item) {
    if (
      !confirm(
        `Excluir ${item.nome || item.titulo}? Esta ação não pode ser desfeita.`,
      )
    )
      return;
    setBusy(item.id);
    try {
      await api(path + "/" + item.id, { method: "DELETE" });
      state.reload();
      notify("Registro excluído.");
    } catch (e) {
      notify(e.message);
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="CADASTROS"
        title={names[resource]}
        description="Organize os dados e mantenha tudo atualizado."
      >
        <button className="btn" onClick={() => setEditing({})}>
          <Plus size={17} />
          Novo cadastro
        </button>
      </PageHeading>
      <div className="search-field admin-search">
        <Search size={18} />
        <input
          placeholder={
            resource === "usuarios"
              ? "Buscar por nome ou email"
              : "Buscar por nome"
          }
          aria-label="Buscar cadastro"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>
      <State state={state} empty={!items.length}>
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>Código</th>
                <th>{resource === "imoveis" ? "Imóvel" : "Nome"}</th>
                {resource === "usuarios" ? (
                  <>
                    <th>Email</th>
                    <th>Perfil</th>
                  </>
                ) : resource === "imoveis" ? (
                  <>
                    <th>Bairro / cidade</th>
                    <th>Situação</th>
                  </>
                ) : null}
                <th>Ações</th>
              </tr>
            </thead>
            <tbody>
              {items.map((i) => (
                <tr key={i.id}>
                  <td>#{i.id}</td>
                  <td>{i.nome || i.titulo}</td>
                  {resource === "usuarios" ? (
                    <>
                      <td>{i.email}</td>
                      <td>
                        <Status value={i.perfil} />
                      </td>
                    </>
                  ) : resource === "imoveis" ? (
                    <>
                      <td>
                        {i.bairro?.nome}
                        <small className="block">{i.cidade}</small>
                      </td>
                      <td>
                        <Status value={i.status} />
                      </td>
                    </>
                  ) : null}
                  <td>
                    <div className="actions">
                      <button
                        className="icon-btn"
                        aria-label="Editar"
                        onClick={() => setEditing(i)}
                      >
                        <Pencil size={17} />
                      </button>
                      <button
                        className="icon-btn danger"
                        aria-label="Excluir"
                        disabled={
                          busy === i.id ||
                          (resource === "usuarios" && i.id === user.id)
                        }
                        title={
                          resource === "usuarios" && i.id === user.id
                            ? "Você não pode excluir sua conta"
                            : "Excluir"
                        }
                        onClick={() => remove(i)}
                      >
                        <Trash2 size={17} />
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </State>
      {editing && (
        <Modal
          title={
            (editing.id ? "Editar " : "Novo cadastro · ") + names[resource]
          }
          onClose={() => setEditing(null)}
        >
          <State state={options}>
            <Form
              onSubmit={async (d) => {
                let body = { ...d };
                if (resource === "usuarios" && !d.senha) delete body.senha;
                if (resource === "imoveis") {
                  for (const key of [
                    "precoVenda",
                    "precoAluguel",
                    "dormitorios",
                    "banheiros",
                    "garagem",
                    "areaTotal",
                    "areaConstruida",
                  ])
                    body[key] = d[key] === "" ? null : Number(d[key]);
                  body.tipoImovel = { id: Number(d.tipoImovel) };
                  body.bairro = { id: Number(d.bairro) };
                  body.destaque = d.destaque === "on";
                }
                await api(path + (editing.id ? "/" + editing.id : ""), {
                  method: editing.id ? "PUT" : "POST",
                  body,
                });
                setEditing(null);
                state.reload();
                notify("Cadastro salvo.");
              }}
            >
              {resource === "imoveis" ? (
                <>
                  <Field
                    name="titulo"
                    title="Título"
                    value={editing.titulo}
                    maxLength="255"
                  />
                  <Field
                    name="descricao"
                    title="Descrição"
                    type="textarea"
                    value={editing.descricao}
                    maxLength="4000"
                  />
                  <div className="form-grid">
                    <Field
                      name="tipoImovel"
                      title="Tipo de imóvel"
                      value={editing.tipoImovel?.id || ""}
                    >
                      <option value="">Selecione</option>
                      {options.data?.[0].map((t) => (
                        <option key={t.id} value={t.id}>
                          {t.nome}
                        </option>
                      ))}
                    </Field>
                    <Field
                      name="bairro"
                      title="Bairro"
                      value={editing.bairro?.id || ""}
                    >
                      <option value="">Selecione</option>
                      {options.data?.[1].map((b) => (
                        <option key={b.id} value={b.id}>
                          {b.nome}
                        </option>
                      ))}
                    </Field>
                    <Field
                      name="cidade"
                      title="Cidade"
                      value={editing.cidade}
                      maxLength="255"
                    />
                    <Field
                      name="finalidade"
                      title="Finalidade"
                      value={editing.finalidade || "Venda"}
                    >
                      <option>Venda</option>
                      <option>Aluguel</option>
                      <option>Venda e aluguel</option>
                    </Field>
                    <Field
                      name="status"
                      title="Situação"
                      value={editing.status || "Disponível"}
                    >
                      <option>Disponível</option>
                      <option>Reservado</option>
                      <option>Alugado</option>
                      <option>Vendido</option>
                    </Field>
                    {[
                      ["precoVenda", "Preço de venda (R$)"],
                      ["precoAluguel", "Aluguel mensal (R$)"],
                      ["areaTotal", "Área total (m²)"],
                      ["areaConstruida", "Área construída (m²)"],
                      ["dormitorios", "Quartos"],
                      ["banheiros", "Banheiros"],
                      ["garagem", "Vagas"],
                    ].map(([key, title]) => (
                      <Field
                        key={key}
                        name={key}
                        title={title}
                        type="number"
                        min="0"
                        step={
                          ["dormitorios", "banheiros", "garagem"].includes(key)
                            ? "1"
                            : "0.01"
                        }
                        required={false}
                        value={editing[key]}
                      />
                    ))}
                    <Field
                      name="endereco"
                      title="Endereço"
                      value={editing.endereco}
                    />
                    <Field
                      name="numero"
                      title="Número"
                      value={editing.numero}
                    />
                    <Field
                      name="complemento"
                      title="Complemento"
                      required={false}
                      value={editing.complemento}
                    />
                    <Field
                      name="cep"
                      title="CEP"
                      required={false}
                      value={editing.cep}
                    />
                  </div>
                  <Field
                    name="caracteristicas"
                    title="Características"
                    type="textarea"
                    value={editing.caracteristicas}
                    required={false}
                    maxLength="4000"
                  />
                  <label className="check">
                    <input
                      type="checkbox"
                      name="destaque"
                      defaultChecked={editing.destaque}
                    />
                    Imóvel em destaque
                  </label>
                </>
              ) : (
                <>
                  <Field
                    name="nome"
                    title="Nome"
                    value={editing.nome}
                    maxLength={resource === "usuarios" ? 150 : 255}
                  />
                  {resource === "usuarios" && (
                    <>
                      <Field
                        name="email"
                        title="Email"
                        type="email"
                        value={editing.email}
                        maxLength="254"
                      />
                      <Field
                        name="senha"
                        title={
                          editing.id
                            ? "Nova senha (vazia para manter)"
                            : "Senha"
                        }
                        type="password"
                        required={!editing.id}
                        minLength="8"
                        maxLength="72"
                        autoComplete="new-password"
                      />
                      <Field
                        name="perfil"
                        title="Perfil"
                        value={editing.perfil || "USER"}
                      >
                        <option value="USER">Cliente</option>
                        <option value="ADMIN">Administrador</option>
                      </Field>
                    </>
                  )}
                </>
              )}
            </Form>
          </State>
        </Modal>
      )}
    </>
  );
}
function Photos() {
  const properties = useData(() => api("/imoveis")),
    [id, setId] = useState(""),
    state = useData(
      () => (id ? api("/fotos/imovel/" + id) : Promise.resolve([])),
      [id],
    ),
    [editing, setEditing] = useState(null),
    notify = useContext(Notice);
  async function remove(p) {
    if (!confirm("Excluir esta foto?")) return;
    try {
      await api("/fotos/" + p.id, { method: "DELETE" });
      state.reload();
    } catch (e) {
      notify(e.message);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="GALERIA"
        title="Fotos dos imóveis"
        description="Boas imagens ajudam a contar a história de cada imóvel."
      />
      <State state={properties} empty={!properties.data?.length}>
        <label className="field">
          Selecione um imóvel
          <select value={id} onChange={(e) => setId(e.target.value)}>
            <option value="">Selecione</option>
            {properties.data?.map((i) => (
              <option key={i.id} value={i.id}>
                #{i.id} · {i.titulo}
              </option>
            ))}
          </select>
        </label>
      </State>
      {id && (
        <>
          <button className="btn" onClick={() => setEditing({})}>
            <Plus size={17} />
            Adicionar foto
          </button>
          <State state={state} empty={!state.data?.length}>
            <div className="photo-grid">
              {state.data?.map((p) => (
                <article key={p.id} className="photo-card">
                  <img
                    src={API + "/fotos/" + p.id + "/arquivo"}
                    alt={"Foto do imóvel " + id}
                  />
                  <div className="actions wrap">
                    <span>{p.capa ? "Capa" : "Ordem " + p.ordem}</span>
                    <button
                      className="icon-btn"
                      aria-label="Editar foto"
                      onClick={() => setEditing(p)}
                    >
                      <Pencil size={17} />
                    </button>
                    <button
                      className="icon-btn danger"
                      aria-label="Excluir foto"
                      onClick={() => remove(p)}
                    >
                      <Trash2 size={17} />
                    </button>
                    {!p.capa && (
                      <button
                        className="text-button"
                        onClick={async () => {
                          try {
                            await api("/fotos/" + p.id + "/capa", {
                              method: "PUT",
                            });
                            state.reload();
                          } catch (e) {
                            notify(e.message);
                          }
                        }}
                      >
                        Definir capa
                      </button>
                    )}
                  </div>
                </article>
              ))}
            </div>
          </State>
        </>
      )}
      {editing && (
        <Modal
          title={editing.id ? "Editar foto" : "Adicionar foto"}
          onClose={() => setEditing(null)}
        >
          <Form
            onSubmit={async (d, form) => {
              const data = new FormData(form);
              if (!editing.id) data.set("imovelId", id);
              if (editing.id && !data.get("arquivo")?.size)
                data.delete("arquivo");
              await api("/fotos" + (editing.id ? "/" + editing.id : ""), {
                method: editing.id ? "PUT" : "POST",
                body: data,
              });
              setEditing(null);
              state.reload();
              notify("Foto salva.");
            }}
          >
            <Field
              name="arquivo"
              title="Imagem JPG, PNG ou GIF (até 10 MB)"
              type="file"
              accept="image/jpeg,image/png,image/gif"
              required={!editing.id}
            />
            {editing.id && (
              <Field
                name="ordem"
                title="Ordem"
                type="number"
                min="0"
                step="1"
                value={editing.ordem}
              />
            )}
          </Form>
        </Modal>
      )}
    </>
  );
}
