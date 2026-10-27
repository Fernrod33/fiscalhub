"""
Converte as abas do Plano de Contas Referencial da planilha oficial "Tabelas Dinâmicas e Planos de Contas Referenciais"
da ECF (sped.rfb.gov.br, Manuais da ECF) para o formato das tabelas do Sped Tabelas que a ImportacaoPlanoReferencial lê
(CODIGO|DESCRICAO|DT_INI|DT_FIM|ORDEM|TIPO|COD_SUP|NIVEL|NATUREZA, ISO-8859-1). Só biblioteca padrão.

Uso (na pasta backend/):
    python -I scripts/sped/converter_tabelas_dinamicas_ecf.py <planilha.xlsx> <ano-calendário> <leiaute> <id> <atualização>

Grava SPEDECF_DINAMICA_{L100_A,L300_A,P100,P150}_<ano>.txt em src/main/resources/plano-referencial/ e imprime as
linhas para o tabelas.txt (com o SHA-256 da planilha e do .txt). ORDEM é a ordem da linha na aba; NATUREZA vira dois
dígitos, como no Sped Tabelas.
Baixar: http://sped.rfb.gov.br/arquivo/download/<id>; o id e a data de atualização estão no link "Tabelas Dinâmicas ...
- Leiaute N (Atualização: dd/mm/aaaa)" da página http://sped.rfb.gov.br/pasta/show/1644.
"""
import hashlib
import re
import sys
import xml.etree.ElementTree as ET
import zipfile

M = '{http://schemas.openxmlformats.org/spreadsheetml/2006/main}'
R = '{http://schemas.openxmlformats.org/officeDocument/2006/relationships}'
ABAS = {'L100A': ('L100_A', '1', 'BP'), 'L300A': ('L300_A', '1', 'DRE'), 'P100A': ('P100', '2', 'BP'),
        'P150A': ('P150', '2', 'DRE')}
DESTINO = 'src/main/resources/plano-referencial/'


def linhas(z, aba):
    wb = ET.fromstring(z.read('xl/workbook.xml'))
    rels = {r.get('Id'): r.get('Target') for r in ET.fromstring(z.read('xl/_rels/workbook.xml.rels'))}
    alvo = next(rels[s.get(R + 'id')] for s in wb.find(M + 'sheets') if s.get('name') == aba).lstrip('/')
    textos = [''.join(t.text or '' for t in si.iter(M + 't'))
              for si in ET.fromstring(z.read('xl/sharedStrings.xml')).findall(M + 'si')]
    for row in ET.fromstring(z.read(alvo if alvo.startswith('xl/') else 'xl/' + alvo)).iter(M + 'row'):
        celulas = {}
        for c in row.findall(M + 'c'):
            v = c.find(M + 'v')
            col = 0
            for ch in re.match(r'[A-Z]+', c.get('r')).group():
                col = col * 26 + ord(ch) - 64
            texto = '' if v is None else textos[int(v.text)] if c.get('t') == 's' else v.text
            celulas[col - 1] = (texto or '').strip()
        yield [celulas.get(i, '') for i in range(max(celulas, default=-1) + 1)]


def main(planilha, ano, leiaute, item, atualizacao):
    bruto = open(planilha, 'rb').read()
    sha_planilha = hashlib.sha256(bruto).hexdigest()
    z = zipfile.ZipFile(planilha)
    for aba, (sufixo, plano, demonstracao) in ABAS.items():
        saida = [f'versão={leiaute} CODIGO, DESCRICAO, DT_INI, DT_FIM, ORDEM, TIPO, COD_SUP, NIVEL, NATUREZA']
        ordem = 1
        for r in list(linhas(z, aba))[1:]:  # cabeçalho: CÓDIGO|DESCRIÇÃO|DT_INI|DT_FIM|TIPO|CONTA SUPERIOR|NÍVEL|NATUREZA|...
            if not r or not r[0]:
                continue
            r += [''] * (8 - len(r))
            ordem += 1
            # '|' é o separador; a planilha oficial tem descrição terminando em '|' (1.02.03.05.92)
            descricao = ' '.join(r[1].replace('|', ' ').split())
            saida.append('|'.join([r[0], descricao, r[2], r[3], str(ordem), r[4], r[5], r[6],
                                   r[7].zfill(2) if r[7] else '']))
        nome = f'SPEDECF_DINAMICA_{sufixo}_{ano}'
        texto = '\r\n'.join(saida) + '\r\n'
        for tipografico, ascii_ in (('–', '-'), ('—', '-'), ('“', '"'), ('”', '"'), ('‘', "'"), ('’', "'")):
            texto = texto.replace(tipografico, ascii_)
        conteudo = texto.encode('iso-8859-1')  # caractere fora do Latin-1 que sobrar: erro, nunca '?'
        with open(DESTINO + nome + '.txt', 'wb') as f:
            f.write(conteudo)
        print(f'{nome}|{ano}|{plano}|{demonstracao}|http://sped.rfb.gov.br/arquivo/download/{item}|{atualizacao}|'
              f'planilha {sha_planilha} aba {aba} leiaute {leiaute}|{hashlib.sha256(conteudo).hexdigest()}')


main(*sys.argv[1:6])
