// Ofertas e contratos. O mesmo componente atende cliente, corretor e administrador:
// cada perfil vê só as ações que pode fazer, e o servidor confere a permissão de novo.
// Fluxo: cliente envia oferta → corretor aceita e prepara o contrato → administrador aprova → cliente assina.
import React, { useContext, useEffect, useState } from "react";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { Ban, Check, Download, Mail, PenLine, Undo2 } from "lucide-react";
import { api, money, date, day, label } from "./api";
import {
  Notice,
  useData,
  useDebounced,
  State,
  Status,
  Modal,
  Form,
  Field,
  PageHeading,
  Pagination,
  ListFilters,
  pageInfo,
} from "./ui";

const POR_PAGINA = 5;

// Lista paginada no servidor (Pageable), com busca e filtro de situação.
// O filtro inicial pode vir da URL (?situacao=...), usado pelos atalhos dos painéis.
function usePaginaFiltrada(rota, situacaoPadrao) {
  const [params] = useSearchParams();
  const inicial = params.get("situacao") ?? situacaoPadrao;
  const [situacao, setSituacao] = useState(inicial);
  const [texto, setTexto] = useState("");
  const [page, setPage] = useState(0);
  useEffect(() => {
    setSituacao(inicial);
    setPage(0);
  }, [inicial]);
  const busca = useDebounced(texto.trim());
  const query = new URLSearchParams({
    page: String(page),
    size: String(POR_PAGINA),
  });
  if (situacao) query.set("status", situacao);
  if (busca) query.set("busca", busca);
  const q = query.toString();
  const state = useData(() => api(rota + "?" + q), [q]);
  const info = pageInfo(state.data);
  // Se uma ação esvaziou a última página, volta para a anterior.
  useEffect(() => {
    if (!state.loading && state.data && !info.items.length && page > 0)
      setPage((p) => p - 1);
  }, [state.loading, state.data]);
  return {
    state,
    ...info,
    page,
    setPage,
    situacao,
    texto,
    setSituacao: (valor) => {
      setSituacao(valor);
      setPage(0);
    },
    setTexto: (valor) => {
      setTexto(valor);
      setPage(0);
    },
  };
}

function Contagem({ lista, singular, plural }) {
  if (!lista.state.data) return <p className="list-count">&nbsp;</p>;
  return (
    <p className="list-count">
      {lista.total} {lista.total === 1 ? singular : plural}
    </p>
  );
}

// ---------- Ofertas ----------
const SITUACOES_OFERTA = [
  ["", "Todas as situações"],
  ["PENDENTE,EM_ESPERA", "Em aberto"],
  ["PENDENTE", "Pendentes"],
  ["EM_ESPERA", "Em espera"],
  ["ACEITA", "Aceitas"],
  ["RECUSADA", "Recusadas"],
  ["CANCELADA", "Canceladas"],
];
const TEXTOS_OFERTAS = {
  USER: {
    eyebrow: "ÁREA DO CLIENTE",
    title: "Minhas ofertas",
    description: "Acompanhe cada proposta que você enviou.",
    busca: "Buscar por imóvel ou código",
    vazio: "Encontre um imóvel na vitrine e envie sua proposta.",
  },
  CORRETOR: {
    eyebrow: "ATENDIMENTO",
    title: "Ofertas recebidas",
    description: "Aceite uma proposta para começar a preparar o contrato.",
    busca: "Buscar por imóvel, cliente ou código",
    vazio: "Nenhuma oferta nesta situação. Troque o filtro para ver outras.",
  },
  ADMIN: {
    eyebrow: "NEGOCIAÇÕES",
    title: "Todas as ofertas",
    description: "Acompanhe as propostas e o atendimento dos corretores.",
    busca: "Buscar por imóvel, cliente ou código",
    vazio: "Nenhuma oferta nesta situação. Troque o filtro para ver outras.",
  },
};

export function Offers({ perfil }) {
  const t = TEXTOS_OFERTAS[perfil];
  const lista = usePaginaFiltrada(
    "/ofertas/ofertas-page",
    perfil === "CORRETOR" ? "PENDENTE,EM_ESPERA" : "",
  );
  const notify = useContext(Notice),
    navigate = useNavigate();
  const [busy, setBusy] = useState(null),
    [recusando, setRecusando] = useState(null);
  async function executar(o, acao, pergunta, sucesso) {
    if (pergunta && !confirm(pergunta)) return;
    setBusy(o.id);
    try {
      await api(`/ofertas/${o.id}/${acao}`, { method: "POST" });
      notify(sucesso);
      if (acao === "aceitar") navigate("/corretor/contratos");
      else lista.state.reload();
    } catch (e) {
      notify(e.message);
    } finally {
      setBusy(null);
    }
  }
  return (
    <>
      <PageHeading
        eyebrow={t.eyebrow}
        title={t.title}
        description={t.description}
      />
      <ListFilters
        search={lista.texto}
        onSearch={lista.setTexto}
        placeholder={t.busca}
      >
        <select
          aria-label="Situação da oferta"
          value={lista.situacao}
          onChange={(e) => lista.setSituacao(e.target.value)}
        >
          {SITUACOES_OFERTA.map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </select>
      </ListFilters>
      <Contagem lista={lista} singular="oferta" plural="ofertas" />
      <State
        state={lista.state}
        empty={!lista.items.length}
        emptyTitle="Nenhuma oferta por aqui"
        emptyText={t.vazio}
      >
        <div className="list-stack">
          {lista.items.map((o) => (
            <OfertaCard
              key={o.id}
              o={o}
              perfil={perfil}
              busy={busy === o.id}
              onAceitar={() =>
                executar(
                  o,
                  "aceitar",
                  `Aceitar a oferta #${o.id} de ${o.usuario.nome}? O imóvel fica reservado e o contrato vai para preparação.`,
                  "Oferta aceita. Agora prepare o contrato e envie para aprovação.",
                )
              }
              onRecusar={() => setRecusando(o)}
              onCancelar={() =>
                executar(
                  o,
                  "cancelar",
                  `Cancelar sua oferta #${o.id}?`,
                  "Oferta cancelada.",
                )
              }
            />
          ))}
        </div>
        <Pagination
          page={lista.page}
          pages={lista.pages}
          onChange={lista.setPage}
        />
      </State>
      {recusando && (
        <RecusarModal
          o={recusando}
          onClose={() => setRecusando(null)}
          onDone={() => {
            setRecusando(null);
            notify("Oferta recusada. O cliente verá sua mensagem.");
            lista.state.reload();
          }}
        />
      )}
    </>
  );
}

function OfertaCard({ o, perfil, busy, onAceitar, onRecusar, onCancelar }) {
  const aberta = ["PENDENTE", "EM_ESPERA"].includes(o.status);
  const aluguel = o.tipo === "ALUGUEL";
  const anunciado = Number(
    aluguel ? o.imovel.precoAluguel : o.imovel.precoVenda,
  );
  const diferenca =
    anunciado > 0
      ? Math.round(((o.valorProposto - anunciado) / anunciado) * 1000) / 10
      : null;
  return (
    <article className="list-card">
      <div>
        <p className="eyebrow">
          OFERTA #{o.id} · {label(o.tipo)}
        </p>
        <Link to={"/imoveis/" + o.imovel.id}>
          <h3>{o.imovel.titulo}</h3>
        </Link>
        {perfil !== "USER" && (
          <p>
            {o.usuario.nome} · {o.usuario.email}
          </p>
        )}
        <p className="muted">{o.mensagem}</p>
        {o.resposta && (
          <p className="note">
            <strong>Resposta:</strong> {o.resposta}
          </p>
        )}
        {o.status === "EM_ESPERA" && (
          <p className="hint">
            Outra proposta está em negociação para este imóvel. Se ela não for
            concluída, esta oferta ainda pode ser aceita.
          </p>
        )}
        <small>
          Enviada em {date(o.dataCriacao)}
          {o.dataResposta && " · respondida em " + date(o.dataResposta)}
        </small>
      </div>
      <div className="list-actions">
        <Status value={o.status} />
        <strong>
          {money(o.valorProposto)}
          {aluguel && <small> /mês</small>}
        </strong>
        {diferenca !== null && (
          <span className="hint">
            Anunciado: {money(anunciado)}
            {diferenca !== 0 &&
              ` · ${Math.abs(diferenca).toLocaleString("pt-BR")}% ${diferenca < 0 ? "abaixo" : "acima"}`}
          </span>
        )}
        {perfil === "CORRETOR" && aberta && (
          <div className="actions">
            <button disabled={busy} className="btn small" onClick={onAceitar}>
              <Check size={15} />
              Aceitar
            </button>
            <button
              disabled={busy}
              className="btn small secondary"
              onClick={onRecusar}
            >
              Recusar
            </button>
          </div>
        )}
        {perfil === "USER" && aberta && (
          <button
            disabled={busy}
            className="btn small secondary"
            onClick={onCancelar}
          >
            Cancelar oferta
          </button>
        )}
        {perfil === "USER" && o.status === "ACEITA" && (
          <Link className="text-button" to="/cliente/contratos">
            Acompanhar contrato
          </Link>
        )}
      </div>
    </article>
  );
}

function RecusarModal({ o, onClose, onDone }) {
  return (
    <Modal title={"Recusar oferta #" + o.id} onClose={onClose}>
      <p className="hint">
        {o.usuario.nome} · {o.imovel.titulo} · {money(o.valorProposto)}
      </p>
      <Form
        submit="Recusar oferta"
        busyText="Enviando…"
        onSubmit={async (d) => {
          await api(`/ofertas/${o.id}/recusar`, {
            method: "POST",
            body: { motivo: d.motivo },
          });
          onDone();
        }}
      >
        <Field
          name="motivo"
          title="Mensagem para o cliente (opcional)"
          type="textarea"
          maxLength="1000"
          required={false}
        />
      </Form>
    </Modal>
  );
}

// Formulário de oferta, usado no detalhe do imóvel e nos favoritos.
export function OfertaModal({ imovel, onClose }) {
  const notify = useContext(Notice);
  return (
    <Modal title="Sua proposta" onClose={onClose}>
      <p>
        {imovel.titulo} · Código {imovel.id}
      </p>
      <p className="hint">
        Anunciado:{" "}
        {[
          imovel.precoVenda > 0 && "venda por " + money(imovel.precoVenda),
          imovel.precoAluguel > 0 &&
            "aluguel por " + money(imovel.precoAluguel) + "/mês",
        ]
          .filter(Boolean)
          .join(" · ")}
      </p>
      <Form
        submit="Enviar oferta"
        busyText="Enviando…"
        onSubmit={async (d) => {
          await api("/ofertas", {
            method: "POST",
            body: {
              ...d,
              imovelId: Number(imovel.id),
              valorProposto: Number(d.valorProposto),
            },
          });
          onClose();
          notify("Oferta enviada! Acompanhe em Minhas ofertas.");
        }}
      >
        <Field
          name="tipo"
          title="Tipo de oferta"
          value={imovel.precoVenda > 0 ? "VENDA" : "ALUGUEL"}
        >
          {imovel.precoVenda > 0 && <option value="VENDA">Compra</option>}
          {imovel.precoAluguel > 0 && <option value="ALUGUEL">Aluguel</option>}
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
  );
}

// ---------- Contratos ----------
const SITUACOES_CONTRATO = [
  ["", "Todas as etapas"],
  ["EM_PREPARACAO", "Em preparação"],
  ["AGUARDANDO_APROVACAO", "Aguardando aprovação"],
  ["AGUARDANDO_ASSINATURA", "Aguardando assinatura"],
  ["ASSINADO", "Assinados"],
  ["CANCELADO", "Cancelados"],
];
const TEXTOS_CONTRATOS = {
  USER: {
    eyebrow: "ÁREA DO CLIENTE",
    title: "Meus contratos",
    description: "Acompanhe cada etapa até a assinatura.",
    busca: "Buscar por imóvel ou código",
    vazio: "Os contratos aparecem aqui quando um corretor aceita sua oferta.",
  },
  CORRETOR: {
    eyebrow: "SUAS NEGOCIAÇÕES",
    title: "Contratos",
    description:
      "Prepare os contratos das ofertas que você aceitou e envie para aprovação.",
    busca: "Buscar por imóvel, cliente ou código",
    vazio: "Aceite uma oferta para abrir um contrato.",
  },
  ADMIN: {
    eyebrow: "DOCUMENTOS",
    title: "Gestão de contratos",
    description:
      "Revise o que os corretores prepararam e aprove para a assinatura.",
    busca: "Buscar por imóvel, cliente, corretor ou código",
    vazio: "Nenhum contrato nesta etapa.",
  },
};

async function baixarPdf(c, notify) {
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

export function Contracts({ perfil }) {
  const t = TEXTOS_CONTRATOS[perfil];
  const lista = usePaginaFiltrada("/contratos/contratos-page", "");
  const notify = useContext(Notice);
  const [busy, setBusy] = useState(null),
    [preparando, setPreparando] = useState(null),
    [devolvendo, setDevolvendo] = useState(null),
    [assinando, setAssinando] = useState(null);
  async function acao(c, nome, pergunta, mensagem) {
    if (pergunta && !confirm(pergunta)) return;
    setBusy(c.id);
    try {
      const res = await api(`/contratos/${c.id}/${nome}`, { method: "POST" });
      notify(typeof mensagem === "function" ? mensagem(res) : mensagem);
      lista.state.reload();
    } catch (e) {
      notify(e.message);
    } finally {
      setBusy(null);
    }
  }
  const acoes = {
    download: (c) => baixarPdf(c, notify),
    preparar: setPreparando,
    devolver: setDevolvendo,
    assinar: setAssinando,
    aprovar: (c) =>
      acao(
        c,
        "aprovar",
        `Aprovar o contrato #${c.id}? O cliente poderá assinar em seguida.`,
        (res) =>
          res?.dataEnvio
            ? "Contrato aprovado. O cliente foi avisado por email e já pode assinar."
            : "Contrato aprovado. O email não foi enviado, mas o cliente já pode assinar pela área de contratos. Use “Enviar email” para tentar de novo.",
      ),
    enviar: (c) => acao(c, "enviar", null, "Email enviado ao cliente."),
    cancelar: (c) =>
      acao(
        c,
        "cancelar",
        `Cancelar o contrato #${c.id} e liberar o imóvel para novas ofertas?`,
        "Contrato cancelado. O imóvel voltou para a vitrine.",
      ),
  };
  const fechar = (setter, mensagem) => () => {
    setter(null);
    notify(mensagem);
    lista.state.reload();
  };
  return (
    <>
      <PageHeading
        eyebrow={t.eyebrow}
        title={t.title}
        description={t.description}
      />
      <ListFilters
        search={lista.texto}
        onSearch={lista.setTexto}
        placeholder={t.busca}
      >
        <select
          aria-label="Etapa do contrato"
          value={lista.situacao}
          onChange={(e) => lista.setSituacao(e.target.value)}
        >
          {SITUACOES_CONTRATO.map(([valor, nome]) => (
            <option key={valor} value={valor}>
              {nome}
            </option>
          ))}
        </select>
      </ListFilters>
      <Contagem lista={lista} singular="contrato" plural="contratos" />
      <State
        state={lista.state}
        empty={!lista.items.length}
        emptyTitle="Nenhum contrato por aqui"
        emptyText={t.vazio}
      >
        <div className="list-stack">
          {lista.items.map((c) => (
            <ContratoCard
              key={c.id}
              c={c}
              perfil={perfil}
              busy={busy === c.id}
              acoes={acoes}
            />
          ))}
        </div>
        <Pagination
          page={lista.page}
          pages={lista.pages}
          onChange={lista.setPage}
        />
      </State>
      {preparando && (
        <PrepararModal
          c={preparando}
          onClose={() => setPreparando(null)}
          onDone={fechar(
            setPreparando,
            "Contrato enviado para a aprovação do administrador.",
          )}
        />
      )}
      {devolvendo && (
        <DevolverModal
          c={devolvendo}
          onClose={() => setDevolvendo(null)}
          onDone={fechar(
            setDevolvendo,
            "Contrato devolvido ao corretor com o pedido de ajuste.",
          )}
        />
      )}
      {assinando && (
        <AssinarModal
          c={assinando}
          onDownload={acoes.download}
          onClose={() => setAssinando(null)}
          onDone={fechar(
            setAssinando,
            "Contrato assinado. Parabéns pelo novo endereço!",
          )}
        />
      )}
    </>
  );
}

const PROXIMO_PASSO = {
  EM_PREPARACAO: {
    USER: "O corretor está preparando seu contrato.",
    CORRETOR: "Preencha os termos e envie para aprovação.",
    ADMIN: "Com o corretor, em preparação.",
  },
  AGUARDANDO_APROVACAO: {
    USER: "A imobiliária está revisando seu contrato.",
    CORRETOR: "Enviado. Aguardando a aprovação do administrador.",
    ADMIN: "Revise os termos e aprove ou devolva ao corretor.",
  },
  AGUARDANDO_ASSINATURA: {
    USER: "Baixe o PDF, leia com calma e assine.",
    CORRETOR: "Aprovado. Aguardando a assinatura do cliente.",
    ADMIN: "Aprovado. Aguardando a assinatura do cliente.",
  },
  ASSINADO: {
    USER: "Contrato assinado. Tudo certo com o seu novo endereço!",
    CORRETOR: "Negócio concluído.",
    ADMIN: "Negócio concluído.",
  },
  CANCELADO: {
    USER: "Esta negociação foi cancelada.",
    CORRETOR: "Negociação cancelada.",
    ADMIN: "Negociação cancelada; o imóvel voltou para a vitrine.",
  },
};

function ContratoCard({ c, perfil, busy, acoes }) {
  const o = c.oferta,
    aluguel = o.tipo === "ALUGUEL",
    porMes = aluguel ? "/mês" : "";
  // O cliente vê os termos depois que a imobiliária aprova.
  const termos = perfil !== "USER" || !!c.dataAprovacao;
  const pedidoAjuste =
    perfil !== "USER" &&
    c.motivoDevolucao &&
    ["EM_PREPARACAO", "AGUARDANDO_APROVACAO"].includes(c.status);
  const aviso =
    perfil === "ADMIN" && c.status === "AGUARDANDO_ASSINATURA"
      ? c.dataEnvio
        ? "Cliente avisado por email em " + date(c.dataEnvio) + "."
        : "O aviso por email ainda não foi enviado."
      : null;
  return (
    <article className="list-card contract-card">
      <div className="contract-main">
        <p className="eyebrow">
          CONTRATO #{c.id} · OFERTA #{o.id} · {label(o.tipo)}
        </p>
        <Link to={"/imoveis/" + o.imovel.id}>
          <h3>{o.imovel.titulo}</h3>
        </Link>
        <p>
          Cliente: {o.usuario.nome}
          {perfil !== "USER" && ` · ${o.usuario.email}`}
          <br />
          Corretor: {c.corretor?.nome || "—"}
        </p>
        {termos && (
          <dl className="terms">
            <div>
              <dt>Proposta do cliente</dt>
              <dd>
                {money(o.valorProposto)}
                {porMes}
              </dd>
            </div>
            <div>
              <dt>Valor final</dt>
              <dd>{c.valorFinal ? money(c.valorFinal) + porMes : "—"}</dd>
            </div>
            <div>
              <dt>{aluguel ? "Início da locação" : "Entrega das chaves"}</dt>
              <dd>{day(c.dataInicio)}</dd>
            </div>
            {aluguel && (
              <div>
                <dt>Prazo</dt>
                <dd>{c.prazoMeses ? c.prazoMeses + " meses" : "—"}</dd>
              </div>
            )}
          </dl>
        )}
        {termos && c.condicoes && <p className="conditions">{c.condicoes}</p>}
        {pedidoAjuste && (
          <p className="note">
            <strong>
              Ajuste pedido pela administração
              {c.dataDevolucao ? " em " + date(c.dataDevolucao) : ""}:
            </strong>{" "}
            {c.motivoDevolucao}
          </p>
        )}
        <Etapas c={c} />
      </div>
      <div className="list-actions">
        <Status value={c.status} />
        <div className="actions wrap">
          <button
            className="btn small secondary"
            onClick={() => acoes.download(c)}
          >
            <Download size={15} />
            PDF
          </button>
          {perfil === "CORRETOR" && c.status === "EM_PREPARACAO" && (
            <button
              disabled={busy}
              className="btn small"
              onClick={() => acoes.preparar(c)}
            >
              <PenLine size={15} />
              Preparar contrato
            </button>
          )}
          {perfil === "ADMIN" && c.status === "AGUARDANDO_APROVACAO" && (
            <>
              <button
                disabled={busy}
                className="btn small"
                onClick={() => acoes.aprovar(c)}
              >
                <Check size={15} />
                Aprovar
              </button>
              <button
                disabled={busy}
                className="btn small secondary"
                onClick={() => acoes.devolver(c)}
              >
                <Undo2 size={15} />
                Devolver ao corretor
              </button>
            </>
          )}
          {perfil === "ADMIN" && c.status === "AGUARDANDO_ASSINATURA" && (
            <button
              disabled={busy}
              className="btn small secondary"
              onClick={() => acoes.enviar(c)}
            >
              <Mail size={15} />
              {c.dataEnvio ? "Reenviar email" : "Enviar email"}
            </button>
          )}
          {perfil === "ADMIN" && c.status !== "CANCELADO" && (
            <button
              disabled={busy}
              className="btn small danger"
              onClick={() => acoes.cancelar(c)}
            >
              <Ban size={15} />
              Cancelar
            </button>
          )}
          {perfil === "USER" && c.status === "AGUARDANDO_ASSINATURA" && (
            <button className="btn small" onClick={() => acoes.assinar(c)}>
              <PenLine size={15} />
              Assinar contrato
            </button>
          )}
        </div>
        <p className="hint">
          {PROXIMO_PASSO[c.status]?.[perfil]}
          {aviso && (
            <>
              <br />
              {aviso}
            </>
          )}
        </p>
      </div>
    </article>
  );
}

// Linha do tempo: quem fez cada etapa e de quem é a vez.
const ETAPAS = [
  { nome: "Oferta aceita", quem: "Corretor", vez: "Vez do corretor" },
  { nome: "Contrato preparado", quem: "Corretor", vez: "Vez do corretor" },
  { nome: "Aprovado", quem: "Administração", vez: "Vez da administração" },
  { nome: "Assinado", quem: "Cliente", vez: "Vez do cliente" },
];
const ORDEM = [
  "EM_PREPARACAO",
  "AGUARDANDO_APROVACAO",
  "AGUARDANDO_ASSINATURA",
  "ASSINADO",
];
function Etapas({ c }) {
  const atual = ORDEM.indexOf(c.status); // -1 quando cancelado
  const datas = [
    c.dataCriacao || c.oferta.dataResposta,
    c.dataPreparacao,
    c.dataAprovacao,
    c.dataAssinatura,
  ];
  return (
    <ol className="steps" aria-label="Etapas do contrato">
      {ETAPAS.map((e, k) => {
        const feita = atual >= 0 ? k <= atual : !!datas[k];
        const vez = atual >= 0 && k === atual + 1;
        return (
          <li
            key={e.nome}
            className={feita ? "done" : vez ? "current" : undefined}
            aria-current={vez ? "step" : undefined}
          >
            <span className="step-dot" aria-hidden="true">
              {feita ? <Check size={12} strokeWidth={3} /> : k + 1}
            </span>
            <span className="step-text">
              <strong>{e.nome}</strong>
              <small>
                {feita
                  ? datas[k]
                    ? date(datas[k])
                    : e.quem
                  : vez
                    ? e.vez
                    : e.quem}
              </small>
            </span>
          </li>
        );
      })}
      {c.status === "CANCELADO" && (
        <li className="cancelled">
          <span className="step-dot" aria-hidden="true">
            <Ban size={12} />
          </span>
          <span className="step-text">
            <strong>Cancelado</strong>
            <small>{date(c.dataCancelamento)}</small>
          </span>
        </li>
      )}
    </ol>
  );
}

// Datas no formato do campo <input type="date">, no fuso do navegador.
function isoLocal(d) {
  const x = new Date(d);
  x.setMinutes(x.getMinutes() - x.getTimezoneOffset());
  return x.toISOString().slice(0, 10);
}
function daquiA(dias) {
  const d = new Date();
  d.setDate(d.getDate() + dias);
  return isoLocal(d);
}

function PrepararModal({ c, onClose, onDone }) {
  const o = c.oferta,
    aluguel = o.tipo === "ALUGUEL";
  const condicoesPadrao = aluguel
    ? "Aluguel pago até o dia 10 de cada mês. Garantia: caução equivalente a três aluguéis. Reajuste anual pelo IGP-M. Vistoria de entrada e de saída registrada em laudo."
    : "Pagamento à vista na assinatura da escritura. Entrega das chaves na data indicada. Despesas de ITBI e registro por conta do comprador.";
  return (
    <Modal title={"Preparar contrato #" + c.id} onClose={onClose}>
      <p className="hint">
        {o.imovel.titulo} · {o.usuario.nome} · proposta de{" "}
        {money(o.valorProposto)}
        {aluguel && "/mês"}. Revise os termos e envie para a aprovação do
        administrador.
      </p>
      {c.motivoDevolucao && (
        <p className="alert">
          Ajuste pedido pela administração: {c.motivoDevolucao}
        </p>
      )}
      <Form
        submit="Enviar para aprovação"
        busyText="Enviando…"
        onSubmit={async (d) => {
          await api(`/contratos/${c.id}/preparar`, {
            method: "POST",
            body: {
              valorFinal: Number(d.valorFinal),
              dataInicio: d.dataInicio,
              prazoMeses: aluguel ? Number(d.prazoMeses) : null,
              condicoes: d.condicoes,
            },
          });
          onDone();
        }}
      >
        <div className="form-grid">
          <Field
            name="valorFinal"
            title={
              aluguel
                ? "Aluguel mensal acordado (R$)"
                : "Valor final acordado (R$)"
            }
            type="number"
            min="0.01"
            step="0.01"
            value={c.valorFinal ?? o.valorProposto}
          />
          <Field
            name="dataInicio"
            title={aluguel ? "Início da locação" : "Entrega das chaves"}
            type="date"
            min={daquiA(0)}
            value={c.dataInicio || daquiA(7)}
          />
          {aluguel && (
            <Field
              name="prazoMeses"
              title="Prazo (meses)"
              type="number"
              min="1"
              max="120"
              step="1"
              value={c.prazoMeses ?? 12}
            />
          )}
        </div>
        <Field
          name="condicoes"
          title="Condições do contrato"
          type="textarea"
          maxLength="4000"
          value={c.condicoes || condicoesPadrao}
        />
      </Form>
    </Modal>
  );
}

function DevolverModal({ c, onClose, onDone }) {
  return (
    <Modal title={"Devolver contrato #" + c.id} onClose={onClose}>
      <p className="hint">
        O contrato volta para {c.corretor?.nome || "o corretor"} com o seu
        pedido de ajuste.
      </p>
      <Form
        submit="Devolver ao corretor"
        busyText="Enviando…"
        onSubmit={async (d) => {
          await api(`/contratos/${c.id}/devolver`, {
            method: "POST",
            body: { motivo: d.motivo },
          });
          onDone();
        }}
      >
        <Field
          name="motivo"
          title="O que o corretor precisa ajustar?"
          type="textarea"
          maxLength="1000"
        />
      </Form>
    </Modal>
  );
}

function AssinarModal({ c, onDownload, onClose, onDone }) {
  const aluguel = c.oferta.tipo === "ALUGUEL";
  return (
    <Modal title={"Assinar contrato #" + c.id} onClose={onClose}>
      <p>
        Baixe e leia o PDF antes de confirmar. A assinatura é registrada com sua
        conta autenticada.
      </p>
      <dl className="terms">
        <div>
          <dt>Valor final</dt>
          <dd>
            {money(c.valorFinal ?? c.oferta.valorProposto)}
            {aluguel && "/mês"}
          </dd>
        </div>
        <div>
          <dt>{aluguel ? "Início da locação" : "Entrega das chaves"}</dt>
          <dd>{day(c.dataInicio)}</dd>
        </div>
        {aluguel && (
          <div>
            <dt>Prazo</dt>
            <dd>{c.prazoMeses} meses</dd>
          </div>
        )}
      </dl>
      <button className="text-button" onClick={() => onDownload(c)}>
        <Download size={15} />
        Baixar contrato
      </button>
      <Form
        submit="Confirmar assinatura"
        busyText="Assinando…"
        onSubmit={async (d) => {
          await api(`/contratos/${c.id}/assinar`, {
            method: "POST",
            body: { nome: d.nome, concordo: d.concordo === "on" },
          });
          onDone();
        }}
      >
        <Field
          name="nome"
          title="Seu nome completo, conforme o cadastro"
          autoComplete="name"
        />
        <label className="check">
          <input name="concordo" type="checkbox" required />
          Li e concordo com o contrato.
        </label>
      </Form>
    </Modal>
  );
}
