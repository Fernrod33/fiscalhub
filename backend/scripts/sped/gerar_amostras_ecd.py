"""
Gera as ECDs sintéticas de src/test/resources/amostras/ (ISO-8859-1, contagens 9900/9999 calculadas).
Empresa fictícia; valores pequenos e coerentes: ativo 6.000 D = passivo 1.000 C + PL 5.000 C; lucro 4.500 C.
Exercício 2021 (o mais recente com tabela oficial do plano referencial carregada) e 2020 para o comparativo. Os
códigos referenciais (I051) existem nas tabelas oficiais L100_A/L300_A de 2018 a 2021. ecd-2024-l9.txt é a mesma ECD em
2024, exercício sem tabela referencial: só a demonstração publicada fica disponível.

Uso (na pasta backend/): python -I scripts/sped/gerar_amostras_ecd.py
"""
import re
from pathlib import Path

SAIDA = Path('src/test/resources/amostras')
CNPJ = '11222333000181'

CONTAS = [  # código, superior, nível, natureza, S/A, nome, referencial
    ('1', '', 1, '01', 'S', 'ATIVO', None),
    ('1.1', '1', 2, '01', 'S', 'ATIVO CIRCULANTE', None),
    ('1.1.01', '1.1', 3, '01', 'A', 'Caixa', '1.01.01.01.01'),
    ('1.1.02', '1.1', 3, '01', 'A', 'Bancos conta movimento', '1.01.01.02.01'),
    ('2', '', 1, '02', 'S', 'PASSIVO', None),
    ('2.1', '2', 2, '02', 'A', 'Fornecedores', '2.01.01.03.01'),
    ('3', '', 1, '03', 'S', 'PATRIMÔNIO LÍQUIDO', None),
    ('3.1', '3', 2, '03', 'A', 'Capital social', '2.03.01.01.01'),
    ('3.2', '3', 2, '03', 'A', 'Lucros acumulados', '2.03.04.01.01'),
    ('4', '', 1, '04', 'S', 'RESULTADO', None),
    ('4.1', '4', 2, '04', 'A', 'Receita de vendas de mercadorias', '3.01.01.01.01.05'),
    ('4.2', '4', 2, '04', 'A', 'Custo das mercadorias vendidas', '3.01.01.03.01.02'),
]
SALDOS = [  # conta, inicial, D/C, débitos, créditos, final, D/C
    ('1.1.01', '1000,00', 'D', '6000,00', '3000,00', '4000,00', 'D'),
    ('1.1.02', '0,00', 'D', '2000,00', '0,00', '2000,00', 'D'),
    ('2.1', '500,00', 'C', '1000,00', '1500,00', '1000,00', 'C'),
    ('3.1', '500,00', 'C', '0,00', '0,00', '500,00', 'C'),
    ('3.2', '0,00', 'C', '0,00', '4500,00', '4500,00', 'C'),
    ('4.1', '0,00', 'C', '8000,00', '8000,00', '0,00', 'C'),
    ('4.2', '0,00', 'D', '3500,00', '3500,00', '0,00', 'D'),
]
J100 = [  # código, T/D, nível, superior, grupo, descrição, inicial, D/C, final, D/C
    ('1', 'T', 1, '', 'A', 'ATIVO', '1000,00', 'D', '6000,00', 'D'),
    ('1.1', 'D', 2, '1', 'A', 'Disponibilidades', '1000,00', 'D', '6000,00', 'D'),
    ('2', 'T', 1, '', 'P', 'PASSIVO E PATRIMÔNIO LÍQUIDO', '1000,00', 'C', '6000,00', 'C'),
    ('2.1', 'D', 2, '2', 'P', 'Fornecedores', '500,00', 'C', '1000,00', 'C'),
    ('2.2', 'D', 2, '2', 'P', 'Patrimônio líquido', '500,00', 'C', '5000,00', 'C'),
]
J150 = [  # código, T/D, nível, superior, descrição, final, D/C, grupo
    ('3', 'T', 1, '', 'Lucro líquido do exercício', '4500,00', 'C', 'R'),
    ('3.1', 'D', 2, '3', 'Receita de vendas', '8000,00', 'C', 'R'),
    ('3.2', 'D', 2, '3', 'Custo das mercadorias vendidas', '3500,00', 'D', 'D'),
]


def ecd(versao, finalidade='0', cabecalho='Demonstrações contábeis de 2021', saldos=SALDOS, j100=J100,
        contas=CONTAS, ajuste_9900=None):
    l7 = versao == '7.00'
    plano = '' if l7 else '1'
    campos0000 = ['0000', 'LECD', '01012021', '31122021', 'EMPRESA EXEMPLO LTDA', CNPJ, 'PR', '', '4106902', '', '0', '0',
                  '1', finalidade, 'A' * 40 if finalidade == '1' else '', '0', '0', '', 'N', 'N']
    if not l7:
        campos0000 += ['0', '0', plano]
    linhas = ['|' + '|'.join(campos0000) + '|', '|0001|0|', '|0990|3|', '|I001|0|', f'|I010|G|{versao}|']
    for cod, sup, nivel, nat, ind, nome, ref in contas:
        linhas.append(f'|I050|01012021|{nat}|{ind}|{nivel}|{cod}|{sup}|{nome}|')
        if ref:
            linhas.append(f'|I051|1||{ref}|' if l7 else f'|I051||{ref}|')
    linhas.append('|I150|01012021|31122021|')
    for cod, si, dci, deb, cred, sf, dcf in saldos:
        linhas.append(f'|I155|{cod}||{si}|{dci}|{deb}|{cred}|{sf}|{dcf}|||||||')
    # lançamentos: a leitura pula, mas eles contam no 9900
    linhas += ['|I200|1|15032021|2000,00|N||', '|I250|1.1.02||2000,00|D||||', '|I250|1.1.01||2000,00|C||||',
               '|I200|2|31122021|8000,00|E||', '|I250|4.1||8000,00|D||||', '|I250|3.2||8000,00|C||||']
    linhas += ['|I350|31122021|', '|I355|4.1||8000,00|C|||', '|I355|4.2||3500,00|D|||']
    linhas.append(f'|I990|{sum(1 for l in linhas if l.startswith("|I")) + 1}|')
    bloco_j = ['|J001|0|', f'|J005|01012021|31122021|1|{cabecalho}|']
    for cod, ind, nivel, sup, grp, desc, vi, dci, vf, dcf in j100:
        bloco_j.append(f'|J100|{cod}|{ind}|{nivel}|{sup}|{grp}|{desc}|{vi}|{dci}|{vf}|{dcf}||')
    for ordem, (cod, ind, nivel, sup, desc, vf, dcf, grp) in enumerate(J150, start=1):
        if l7:
            bloco_j.append(f'|J150|{cod}|{ind}|{nivel}|{sup}|{desc}|{vf}|{dcf}|{grp}||')
        else:
            bloco_j.append(f'|J150|{ordem}|{cod}|{ind}|{nivel}|{sup}|{desc}|0,00|D|{vf}|{dcf}|{grp}||')
    bloco_j.append(f'|J990|{len(bloco_j) + 1}|')
    linhas += bloco_j

    contagem = {}
    for l in linhas:
        contagem[l.split('|')[1]] = contagem.get(l.split('|')[1], 0) + 1
    registros = list(contagem) + ['9001', '9900', '9990', '9999']
    contagem.update({'9001': 1, '9990': 1, '9999': 1, '9900': len(registros)})
    if ajuste_9900:
        contagem[ajuste_9900[0]] += ajuste_9900[1]
    bloco_9 = ['|9001|0|'] + [f'|9900|{r}|{contagem[r]}|' for r in registros]
    bloco_9.append(f'|9990|{len(bloco_9) + 2}|')
    linhas += bloco_9
    linhas.append(f'|9999|{len(linhas) + 1}|')
    return '\r\n'.join(linhas) + '\r\n'


def gravar(nome, conteudo):
    SAIDA.mkdir(parents=True, exist_ok=True)
    (SAIDA / nome).write_bytes(conteudo.encode('iso-8859-1'))
    print(SAIDA / nome)


for v in ('9.00', '8.00', '7.00'):
    gravar(f'ecd-minima-l{v[0]}.txt', ecd(v))
gravar('ecd-retificadora-l9.txt', ecd('9.00', finalidade='1', cabecalho='Demonstrações contábeis de 2021 (substituta)'))
gravar('ecd-balanco-nao-fecha-l9.txt', ecd('9.00',
       saldos=[('1.1.01', '1000,00', 'D', '6000,00', '3200,00', '3800,00', 'D')] + SALDOS[1:],
       j100=J100[:2] + [('2', 'T', 1, '', 'P', 'PASSIVO E PATRIMÔNIO LÍQUIDO', '1000,00', 'C', '5800,00', 'C'), J100[3],
                        ('2.2', 'D', 2, '2', 'P', 'Patrimônio líquido', '500,00', 'C', '4800,00', 'C')]))
gravar('ecd-sem-i051-l9.txt', ecd('9.00', contas=[c if c[0] != '1.1.02' else c[:6] + (None,) for c in CONTAS]))
gravar('ecd-contagem-errada-l9.txt', ecd('9.00', ajuste_9900=('I155', 1)))


def metade(m):
    centavos = (int(m.group(1)) * 100 + int(m.group(2))) // 2
    return f'{centavos // 100},{centavos % 100:02d}'


# exercício anterior para o comparativo (AH): mesmos lançamentos com metade dos valores (relações lineares: D = C e
# balanço continuam fechando)
gravar('ecd-2020-l9.txt', re.sub(r'(\d+),(\d\d)', metade, ecd('9.00')).replace('2021', '2020'))
gravar('ecd-2024-l9.txt', ecd('9.00').replace('2021', '2024'))
