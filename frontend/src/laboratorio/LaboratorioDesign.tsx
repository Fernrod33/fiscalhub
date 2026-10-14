// Laboratório de direção visual (só em desenvolvimento). Mesma tela nas direções A, B e C, nos temas claro e escuro.
import '@fontsource-variable/inter';
import '@fontsource-variable/geist';
import '@fontsource-variable/source-serif-4';
import './laboratorio.css';
import { useEffect, useState } from 'react';
import { animate, motion, useMotionValue, useReducedMotion, useTransform } from 'motion/react';
import {
  AlertOctagon, AlertTriangle, Building2, ChevronRight, FileText, Info, LayoutDashboard, Moon, RotateCcw,
  Search, Settings, Sun, Upload,
} from 'lucide-react';
import { clienteExemplo, documentosExemplo, kpisExemplo, pontosAtencaoExemplo, type StatusExemplo } from './fixture';

type Direcao = 'a' | 'b' | 'c';
type Tema = 'claro' | 'escuro';

const DIRECOES: Record<Direcao, { nome: string; descricao: string }> = {
  a: { nome: 'A — Institucional sóbrio', descricao: 'Inter · azul-petróleo · raios pequenos · alta densidade' },
  b: { nome: 'B — Fintech moderno', descricao: 'Geist · esmeralda · raios maiores · menu translúcido' },
  c: { nome: 'C — Editorial jurídico', descricao: 'Source Serif 4 nos títulos + Inter · bordô · mais respiro' },
};

// Tokens de movimento (os mesmos da Tarefa 3)
const DUR = { fast: 0.12, base: 0.2, slow: 0.32 };
const EASE_STANDARD = [0.2, 0, 0, 1] as const;

const moeda = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const inteiro = new Intl.NumberFormat('pt-BR');
const dataHora = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const tamanho = (bytes: number) =>
  bytes >= 1_048_576 ? `${(bytes / 1_048_576).toLocaleString('pt-BR', { maximumFractionDigits: 1 })} MB`
    : `${Math.round(bytes / 1024).toLocaleString('pt-BR')} KB`;

const STATUS: Record<StatusExemplo, { rotulo: string; tom: string }> = {
  RECEBIDO: { rotulo: 'Recebido', tom: 'info' },
  PROCESSANDO: { rotulo: 'Processando', tom: 'warning' },
  PROCESSADO: { rotulo: 'Processado', tom: 'success' },
  ERRO: { rotulo: 'Erro', tom: 'danger' },
};

export default function LaboratorioDesign() {
  const [direcao, setDirecao] = useState<Direcao>('a');
  const [tema, setTema] = useState<Tema>('claro');
  const [rodada, setRodada] = useState(0);

  return (
    <div className="lab">
      <div className="lab-controles" role="toolbar" aria-label="Controles do laboratório">
        <strong>Laboratório de design</strong>
        <span className="lab-aviso">dados de exemplo — fora do build de produção</span>
        <div className="lab-grupo" role="radiogroup" aria-label="Direção visual">
          {(Object.keys(DIRECOES) as Direcao[]).map((d) => (
            <button key={d} type="button" role="radio" aria-checked={direcao === d}
                    className={direcao === d ? 'ativo' : ''} onClick={() => setDirecao(d)}>
              {DIRECOES[d].nome}
            </button>
          ))}
        </div>
        <button type="button" onClick={() => setTema(tema === 'claro' ? 'escuro' : 'claro')}>
          {tema === 'claro' ? <Moon size={16} aria-hidden /> : <Sun size={16} aria-hidden />}
          {tema === 'claro' ? 'Tema escuro' : 'Tema claro'}
        </button>
        <button type="button" onClick={() => setRodada((r) => r + 1)}>
          <RotateCcw size={16} aria-hidden /> Repetir animações
        </button>
      </div>
      <p className="lab-descricao">{DIRECOES[direcao].descricao}</p>

      <div className="dir" data-direcao={direcao} data-tema={tema}>
        <TelaVisaoGeral key={`${direcao}-${tema}-${rodada}`} />
      </div>
    </div>
  );
}

function TelaVisaoGeral() {
  const reduzido = useReducedMotion();
  const entrada = reduzido
    ? { initial: { opacity: 0 }, animate: { opacity: 1 }, transition: { duration: DUR.fast } }
    : { initial: { opacity: 0, y: 8 }, animate: { opacity: 1, y: 0 }, transition: { duration: DUR.slow, ease: EASE_STANDARD } };

  return (
    <div className="app">
      <aside className="menu" aria-label="Menu principal">
        <div className="marca">FiscalHub</div>
        <nav>
          {[
            { icone: LayoutDashboard, rotulo: 'Painel' },
            { icone: Building2, rotulo: 'Clientes', ativo: true },
            { icone: Upload, rotulo: 'Importações' },
            { icone: Settings, rotulo: 'Configurações' },
          ].map(({ icone: Icone, rotulo, ativo }) => (
            <a key={rotulo} href="#" className={ativo ? 'ativo' : ''} aria-current={ativo ? 'page' : undefined}
               onClick={(e) => e.preventDefault()}>
              <Icone size={18} aria-hidden /> {rotulo}
            </a>
          ))}
        </nav>
      </aside>

      <div className="principal">
        <header className="cabecalho">
          <nav className="trilha" aria-label="Trilha">
            <span>Clientes</span> <ChevronRight size={14} aria-hidden /> <span aria-current="page">{clienteExemplo.razaoSocial}</span>
          </nav>
          <button type="button" className="seletor-cliente">
            <Search size={16} aria-hidden /> Buscar cliente <kbd>Ctrl K</kbd>
          </button>
        </header>

        <motion.main className="conteudo" {...entrada}>
          <div className="titulo-pagina">
            <div>
              <h1>{clienteExemplo.razaoSocial}</h1>
              <p className="muted num">CNPJ {clienteExemplo.cnpj}</p>
            </div>
            <button type="button" className="botao-primario"><Upload size={16} aria-hidden /> Importar arquivo</button>
          </div>

          <motion.ul className="kpis" initial="oculto" animate="visivel"
                     variants={{ visivel: { transition: { staggerChildren: reduzido ? 0 : 0.06 } } }}>
            {kpisExemplo.map((k) => (
              <motion.li key={k.rotulo} className="cartao kpi"
                         variants={reduzido
                           ? { oculto: { opacity: 0 }, visivel: { opacity: 1 } }
                           : { oculto: { opacity: 0, y: 8 }, visivel: { opacity: 1, y: 0, transition: { duration: DUR.base, ease: EASE_STANDARD } } }}>
                <span className="muted">{k.rotulo}</span>
                <NumeroAnimado valor={k.valor} formato={k.formato} />
              </motion.li>
            ))}
          </motion.ul>

          <div className="grade">
            <section className="cartao" aria-labelledby="titulo-docs">
              <h2 id="titulo-docs">Documentos importados</h2>
              <table>
                <thead>
                  <tr><th>Arquivo</th><th>Tipo</th><th>Competência</th><th className="dir-num">Tamanho</th><th>Importado em</th><th>Status</th></tr>
                </thead>
                <tbody>
                  {documentosExemplo.map((d) => (
                    <tr key={d.arquivo}>
                      <td><FileText size={16} aria-hidden className="muted" /> {d.arquivo}</td>
                      <td>{d.tipo}</td>
                      <td className="num">{d.competencia}</td>
                      <td className="num dir-num">{tamanho(d.tamanho)}</td>
                      <td className="num">{dataHora.format(new Date(d.importadoEm))}</td>
                      <td><span className={`selo ${STATUS[d.status].tom}`}>
                        {d.status === 'PROCESSANDO' && <span className="pulso" aria-hidden />}
                        {STATUS[d.status].rotulo}
                      </span></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>

            <section className="cartao" aria-labelledby="titulo-atencao">
              <h2 id="titulo-atencao">Pontos de atenção</h2>
              <ul className="atencao">
                {pontosAtencaoExemplo.map((p) => {
                  const Icone = p.nivel === 'danger' ? AlertOctagon : p.nivel === 'warning' ? AlertTriangle : Info;
                  return <li key={p.texto} className={p.nivel}><Icone size={18} aria-hidden /> {p.texto}</li>;
                })}
              </ul>
            </section>
          </div>
        </motion.main>
      </div>
    </div>
  );
}

function NumeroAnimado({ valor, formato }: { valor: number; formato: 'inteiro' | 'moeda' }) {
  const reduzido = useReducedMotion();
  const atual = useMotionValue(reduzido ? valor : 0);
  const texto = useTransform(atual, (v) => (formato === 'moeda' ? moeda.format(v) : inteiro.format(Math.round(v))));
  useEffect(() => {
    if (reduzido) return;
    const controle = animate(atual, valor, { duration: 0.8, ease: EASE_STANDARD });
    return () => controle.stop();
  }, [atual, valor, reduzido]);
  return <motion.strong className="kpi-valor num">{texto}</motion.strong>;
}
