// Painéis com gráficos de cada perfil: administrador, corretor e cliente.
import React, { useContext, useState } from "react";
import { Link } from "react-router-dom";
import {
  ResponsiveContainer,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  Tooltip,
  CartesianGrid,
  Cell,
  LabelList,
} from "recharts";
import {
  ArrowRight,
  Building2,
  ClipboardCheck,
  FileText,
  Handshake,
  Heart,
  Inbox,
  PenLine,
  Wallet,
} from "lucide-react";
import { api, money, moneyShort, date, label } from "./api";
import { Auth, useData, State, PageHeading } from "./ui";

// Cores conferidas com o validador de paleta (fundo branco dos cartões):
// SERIES = azul e terracota da marca para séries diferentes;
// ETAPAS = um só azul, do claro ao escuro, porque as etapas do contrato têm ordem.
const SERIES = ["#2f6ea8", "#c0613f"];
const ETAPAS = ["#7fa6d1", "#5487bf", "#2f6ea8", "#1f5282"];
const TINTA = "#263d50";
const TINTA_SUAVE = "#5f6b74";
const GRADE = "#ebe8e1";
const EIXO = "#d9d6cf";
const DESTAQUE = "#f4f1ea";

const inteiro = (v) => Number(v || 0).toLocaleString("pt-BR");
const primeiroNome = (user) => user?.nome?.split(" ")[0] || "";

function Tiles({ itens }) {
  return (
    <div className="stats">
      {itens.map(([Icon, texto, valor, nota]) => (
        <div className="stat" key={texto}>
          <Icon aria-hidden="true" />
          <p>{texto}</p>
          <strong>{valor}</strong>
          <span>{nota}</span>
        </div>
      ))}
    </div>
  );
}

// Cartão do gráfico: título, alternância com a tabela (mesmos números, sem depender de cor) e estado vazio.
function ChartCard({
  titulo,
  subtitulo,
  linhas,
  colunas,
  vazio,
  rodape,
  largo = false,
  children,
}) {
  const [tabela, setTabela] = useState(false);
  return (
    <section className={largo ? "chart-card largo" : "chart-card"}>
      <div className="chart-head">
        <div>
          <h3>{titulo}</h3>
          {subtitulo && <p className="chart-sub">{subtitulo}</p>}
        </div>
        {!vazio && (
          <button
            className="text-button"
            aria-pressed={tabela}
            onClick={() => setTabela(!tabela)}
          >
            {tabela ? "Ver gráfico" : "Ver tabela"}
          </button>
        )}
      </div>
      {vazio ? (
        <div className="chart-empty">
          <Inbox size={22} aria-hidden="true" />
          {vazio}
        </div>
      ) : tabela ? (
        <table className="chart-table">
          <thead>
            <tr>
              {colunas.map((c) => (
                <th key={c}>{c}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {linhas.map((linha, n) => (
              <tr key={n}>
                {linha.map((v, k) => (
                  <td key={k}>{v}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      ) : (
        children
      )}
      {rodape && <p className="chart-foot">{rodape}</p>}
    </section>
  );
}

// Dica do gráfico: o valor vem primeiro, o nome da série depois.
function Dica({ active, payload, label: titulo, formato = inteiro }) {
  if (!active || !payload?.length) return null;
  return (
    <div className="chart-tip">
      <span>{titulo}</span>
      {payload.map((p) => (
        <span className="chart-tip-row" key={p.dataKey}>
          <i style={{ background: p.payload?.cor || p.color }} />
          <strong>{formato(p.value)}</strong> {p.name}
        </span>
      ))}
    </div>
  );
}

// Barras horizontais com o valor na ponta (todas rotuladas, por isso sem eixo numérico).
function Barras({ dados, unidade, nome = (n) => n, cores }) {
  const linhas = dados.map((d, n) => ({
    ...d,
    rotulo: nome(d.nome),
    cor: cores ? cores[n % cores.length] : SERIES[0],
  }));
  const maior = Math.max(...linhas.map((l) => l.rotulo.length), 4);
  return (
    <ResponsiveContainer width="100%" height={linhas.length * 36 + 8}>
      <BarChart
        data={linhas}
        layout="vertical"
        margin={{ top: 4, right: 40, bottom: 4, left: 0 }}
        barCategoryGap={10}
      >
        <XAxis type="number" hide domain={[0, "dataMax"]} />
        <YAxis
          type="category"
          dataKey="rotulo"
          width={Math.min(170, maior * 7 + 14)}
          tickLine={false}
          axisLine={{ stroke: EIXO }}
          tick={{ fill: TINTA_SUAVE, fontSize: 12 }}
        />
        <Tooltip cursor={{ fill: DESTAQUE }} content={<Dica />} />
        <Bar
          dataKey="total"
          name={unidade}
          fill={SERIES[0]}
          barSize={16}
          radius={[0, 4, 4, 0]}
          isAnimationActive={false}
        >
          {linhas.map((l) => (
            <Cell key={l.nome} fill={l.cor} />
          ))}
          <LabelList
            dataKey="total"
            position="right"
            fill={TINTA}
            fontSize={12}
            formatter={inteiro}
          />
        </Bar>
      </BarChart>
    </ResponsiveContainer>
  );
}

// Colunas por mês com duas séries; só o mês atual leva o número em cima (o resto fica na dica e na tabela).
function Meses({ dados, series }) {
  // O número só existe no último mês; nos outros fica vazio e o rótulo não aparece.
  const linhas = dados.map((m, n) => {
    const rotulos = {};
    for (const [chave] of series)
      rotulos["rotulo_" + chave] =
        n === dados.length - 1 && m[chave] ? m[chave] : null;
    return { ...m, ...rotulos };
  });
  return (
    <>
      <ul className="chart-legend">
        {series.map(([chave, nome], n) => (
          <li key={chave}>
            <i style={{ background: SERIES[n] }} />
            {nome}
          </li>
        ))}
      </ul>
      <ResponsiveContainer width="100%" height={230}>
        <BarChart
          data={linhas}
          margin={{ top: 18, right: 8, bottom: 0, left: -16 }}
          barGap={2}
          barCategoryGap="30%"
        >
          <CartesianGrid vertical={false} stroke={GRADE} />
          <XAxis
            dataKey="nome"
            tickLine={false}
            axisLine={{ stroke: EIXO }}
            tick={{ fill: TINTA_SUAVE, fontSize: 12 }}
          />
          <YAxis
            allowDecimals={false}
            tickLine={false}
            axisLine={false}
            width={44}
            tick={{ fill: TINTA_SUAVE, fontSize: 12 }}
          />
          <Tooltip cursor={{ fill: DESTAQUE }} content={<Dica />} />
          {series.map(([chave, nome], n) => (
            <Bar
              key={chave}
              dataKey={chave}
              name={nome}
              fill={SERIES[n]}
              barSize={14}
              radius={[4, 4, 0, 0]}
              isAnimationActive={false}
            >
              <LabelList
                dataKey={"rotulo_" + chave}
                position="top"
                fill={TINTA}
                fontSize={11}
              />
            </Bar>
          ))}
        </BarChart>
      </ResponsiveContainer>
    </>
  );
}

const vazioSeZero = (dados, texto) =>
  !dados?.length || dados.every((d) => !d.total) ? texto : null;

function GraficoEtapas({ dados, cancelados, titulo, subtitulo }) {
  return (
    <ChartCard
      titulo={titulo}
      subtitulo={subtitulo}
      vazio={vazioSeZero(dados, "Nenhum contrato em andamento ainda.")}
      colunas={["Etapa", "Contratos"]}
      linhas={dados.map((d) => [label(d.nome), inteiro(d.total)])}
      rodape={
        cancelados
          ? `${inteiro(cancelados)} cancelado(s), fora do funil.`
          : null
      }
    >
      <Barras dados={dados} unidade="contratos" nome={label} cores={ETAPAS} />
    </ChartCard>
  );
}

function GraficoBarras({
  titulo,
  subtitulo,
  dados,
  unidade,
  nome = (n) => n,
  coluna,
  vazio,
}) {
  return (
    <ChartCard
      titulo={titulo}
      subtitulo={subtitulo}
      vazio={vazioSeZero(dados, vazio)}
      colunas={[coluna, unidade[0].toUpperCase() + unidade.slice(1)]}
      linhas={(dados || []).map((d) => [nome(d.nome), inteiro(d.total)])}
    >
      <Barras dados={dados} unidade={unidade} nome={nome} />
    </ChartCard>
  );
}

function GraficoMeses({ titulo, subtitulo, dados, series, largo }) {
  const vazio = dados.every((m) => series.every(([chave]) => !m[chave]));
  return (
    <ChartCard
      largo={largo}
      titulo={titulo}
      subtitulo={subtitulo}
      vazio={vazio ? "Ainda não há movimento nos últimos seis meses." : null}
      colunas={["Mês", ...series.map(([, nome]) => nome)]}
      linhas={dados.map((m) => [
        m.nome,
        ...series.map(([chave]) => inteiro(m[chave])),
      ])}
    >
      <Meses dados={dados} series={series} />
    </ChartCard>
  );
}

// Lista curta de pendências com atalho para a tela onde a ação acontece.
function Pendencias({ titulo, itens, vazio, link, rotulo }) {
  return (
    <section className="todo-card">
      <div className="chart-head">
        <h3>{titulo}</h3>
        {link && (
          <Link className="text-button" to={link}>
            {rotulo} <ArrowRight size={14} />
          </Link>
        )}
      </div>
      {itens.length ? (
        <ul className="todo-list">
          {itens.map((i) => (
            <li key={i.chave}>
              <div>
                <strong>{i.titulo}</strong>
                <small>{i.detalhe}</small>
              </div>
              {i.acao}
            </li>
          ))}
        </ul>
      ) : (
        <p className="hint">{vazio}</p>
      )}
    </section>
  );
}

const valorTipo = (item) =>
  money(item.valor) + (item.tipo === "ALUGUEL" ? "/mês" : "");

export function AdminDashboard() {
  const state = useData(() => api("/painel"));
  const d = state.data;
  return (
    <>
      <PageHeading
        eyebrow="VISÃO GERAL"
        title="Bem-vindo à sua gestão"
        description="Acompanhe os números e as oportunidades da imobiliária."
      />
      <State state={state}>
        {d && (
          <>
            <Tiles
              itens={[
                [
                  Building2,
                  "Imóveis disponíveis",
                  inteiro(d.disponiveis),
                  "Na vitrine agora",
                ],
                [
                  Handshake,
                  "Ofertas pendentes",
                  inteiro(d.pendentes),
                  "Esperando um corretor",
                ],
                [
                  ClipboardCheck,
                  "Aguardando aprovação",
                  inteiro(d.aguardandoAprovacao),
                  "Preparados pelos corretores",
                ],
                [
                  PenLine,
                  "Aguardando assinatura",
                  inteiro(d.aguardando),
                  "Já aprovados",
                ],
                [
                  Wallet,
                  "Valor negociado",
                  moneyShort(d.valorNegociado),
                  `${inteiro(d.assinados)} contrato(s) assinado(s)`,
                ],
              ]}
            />
            <Pendencias
              titulo="Contratos esperando sua aprovação"
              link="/admin/contratos?situacao=AGUARDANDO_APROVACAO"
              rotulo="Revisar contratos"
              vazio="Nenhum contrato esperando aprovação agora."
              itens={d.paraAprovar.map((c) => ({
                chave: c.id,
                titulo: `Contrato #${c.id} · ${c.imovel}`,
                detalhe: `${c.cliente} · corretor ${c.corretor || "—"} · ${valorTipo(c)}`,
              }))}
            />
            <div className="charts">
              <GraficoEtapas
                titulo="Contratos por etapa"
                subtitulo="Do aceite do corretor até a assinatura"
                dados={d.contratosPorEtapa}
                cancelados={d.cancelados}
              />
              <GraficoBarras
                titulo="Ofertas por situação"
                dados={d.porSituacao}
                unidade="ofertas"
                coluna="Situação"
                nome={label}
                vazio="Nenhuma oferta recebida ainda."
              />
              <GraficoMeses
                titulo="Últimos seis meses"
                subtitulo="Ofertas recebidas e contratos assinados por mês"
                dados={d.porMes}
                series={[
                  ["ofertas", "Ofertas recebidas"],
                  ["assinados", "Contratos assinados"],
                ]}
              />
              <GraficoBarras
                titulo="Negócios por corretor"
                subtitulo="Contratos em andamento ou assinados"
                dados={d.porCorretor}
                unidade="contratos"
                coluna="Corretor"
                vazio="Nenhum contrato aberto por corretores ainda."
              />
              <GraficoBarras
                titulo="Imóveis por bairro"
                dados={d.porBairro}
                unidade="imóveis"
                coluna="Bairro"
                vazio="Nenhum imóvel cadastrado."
              />
              <GraficoBarras
                titulo="Imóveis por finalidade"
                dados={d.porFinalidade}
                unidade="imóveis"
                coluna="Finalidade"
                vazio="Nenhum imóvel cadastrado."
              />
            </div>
          </>
        )}
      </State>
    </>
  );
}

export function CorretorDashboard() {
  const { user } = useContext(Auth);
  const state = useData(() => api("/painel/corretor"));
  const d = state.data;
  return (
    <>
      <PageHeading
        eyebrow="ÁREA DO CORRETOR"
        title={`Olá, ${primeiroNome(user)}`}
        description="Sua fila de ofertas e o andamento dos contratos que você prepara."
      />
      <State state={state}>
        {d && (
          <>
            <Tiles
              itens={[
                [
                  Handshake,
                  "Ofertas na fila",
                  inteiro(d.fila),
                  "Pendentes ou em espera",
                ],
                [
                  FileText,
                  "Para preparar",
                  inteiro(d.emPreparacao),
                  "Contratos com você",
                ],
                [
                  ClipboardCheck,
                  "Com a administração",
                  inteiro(d.aguardandoAprovacao),
                  "Aguardando aprovação",
                ],
                [
                  PenLine,
                  "Com o cliente",
                  inteiro(d.aguardandoAssinatura),
                  "Aguardando assinatura",
                ],
                [
                  Wallet,
                  "Valor negociado",
                  moneyShort(d.valorNegociado),
                  `${inteiro(d.assinados)} contrato(s) assinado(s)`,
                ],
              ]}
            />
            <div className="todo-grid">
              <Pendencias
                titulo="Ofertas esperando um corretor"
                link="/corretor/ofertas"
                rotulo="Ver ofertas"
                vazio="Nenhuma oferta na fila agora."
                itens={d.proximas.map((o) => ({
                  chave: o.id,
                  titulo: `Oferta #${o.id} · ${o.imovel}`,
                  detalhe: `${o.cliente} · ${valorTipo(o)} · ${label(o.status)} desde ${date(o.data)}`,
                }))}
              />
              <Pendencias
                titulo="Contratos para preparar"
                link="/corretor/contratos?situacao=EM_PREPARACAO"
                rotulo="Preparar"
                vazio="Nenhum contrato esperando por você."
                itens={d.paraPreparar.map((c) => ({
                  chave: c.id,
                  titulo: `Contrato #${c.id} · ${c.imovel}`,
                  detalhe: `${c.cliente} · ${valorTipo(c)}`,
                }))}
              />
            </div>
            <div className="charts">
              <GraficoEtapas
                titulo="Suas negociações por etapa"
                subtitulo="Contratos que você aceitou"
                dados={d.contratosPorEtapa}
                cancelados={d.cancelados}
              />
              <GraficoBarras
                titulo="Fila de ofertas por bairro"
                subtitulo="Onde estão as propostas em aberto"
                dados={d.filaPorBairro}
                unidade="ofertas"
                coluna="Bairro"
                vazio="Nenhuma oferta em aberto."
              />
              <GraficoMeses
                largo
                titulo="Seus últimos seis meses"
                subtitulo="Ofertas que você aceitou e contratos assinados"
                dados={d.porMes}
                series={[
                  ["aceitas", "Ofertas aceitas"],
                  ["assinados", "Contratos assinados"],
                ]}
              />
            </div>
          </>
        )}
      </State>
    </>
  );
}

const PASSO_CLIENTE = {
  EM_PREPARACAO: "O corretor está preparando o contrato.",
  AGUARDANDO_APROVACAO: "A imobiliária está revisando o contrato.",
  AGUARDANDO_ASSINATURA: "Pronto para você assinar.",
};

export function ClienteResumo() {
  const { user } = useContext(Auth);
  const state = useData(() => api("/painel/cliente"));
  const d = state.data;
  return (
    <>
      <PageHeading
        eyebrow="SEU ESPAÇO"
        title={`Olá, ${primeiroNome(user)}`}
        description="Acompanhe suas propostas, seus contratos e os imóveis que você guardou."
      />
      <State state={state}>
        {d && (
          <>
            <Tiles
              itens={[
                [
                  Handshake,
                  "Ofertas em aberto",
                  inteiro(d.ofertasAbertas),
                  "Pendentes ou em espera",
                ],
                [
                  PenLine,
                  "Contratos para assinar",
                  inteiro(d.paraAssinar),
                  "Aprovados pela imobiliária",
                ],
                [
                  FileText,
                  "Contratos assinados",
                  inteiro(d.assinados),
                  "Negócios concluídos",
                ],
                [Heart, "Favoritos", inteiro(d.favoritos), "Imóveis guardados"],
              ]}
            />
            <Pendencias
              titulo="Próximos passos"
              link="/cliente/contratos"
              rotulo="Meus contratos"
              vazio="Nenhum contrato em andamento. Que tal procurar um imóvel na vitrine?"
              itens={d.emAndamento.map((c) => ({
                chave: c.id,
                titulo: `Contrato #${c.id} · ${c.imovel}`,
                detalhe: `${PASSO_CLIENTE[c.status]} · ${valorTipo(c)}`,
                acao:
                  c.status === "AGUARDANDO_ASSINATURA" ? (
                    <Link
                      className="btn small"
                      to="/cliente/contratos?situacao=AGUARDANDO_ASSINATURA"
                    >
                      Assinar
                    </Link>
                  ) : null,
              }))}
            />
            <div className="charts">
              <GraficoBarras
                titulo="Suas ofertas por situação"
                dados={d.porSituacao}
                unidade="ofertas"
                coluna="Situação"
                nome={label}
                vazio="Você ainda não enviou ofertas."
              />
            </div>
          </>
        )}
      </State>
    </>
  );
}
