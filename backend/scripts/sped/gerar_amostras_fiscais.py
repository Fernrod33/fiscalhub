"""
Gera as amostras sintéticas de ECF, EFD-Contribuições e EFD ICMS/IPI de src/test/resources/amostras/ (ISO-8859-1,
CRLF, contagens 9900/9999 calculadas). Cada linha é montada pelo nome dos campos no leiaute gerado dos manuais
(src/main/resources/sped/*-leiautes.properties): campo fora do leiaute é erro. Empresa fictícia (a mesma das ECDs).

ECF Lucro Real 2021 (leiaute 0008): lucro antes do IR 4.500 (igual à DRE referencial da ecd-minima-l9), adição de 500,
compensação de prejuízo de 1.500 (30%), lucro real 3.500; estimativa de janeiro e ajuste anual. ECF Lucro Presumido
2024 (0011): dois trimestres. EFD-Contribuições jan/2025 não cumulativa e cumulativa. EFD ICMS/IPI jan/2025 com
ICMS próprio, ST (SP), DIFAL/FCP (SP) e IPI; fev/2025 com saldo credor. Variantes: retificadora, fórmula que não
fecha e contagem errada.

Uso (na pasta backend/): python -I scripts/sped/gerar_amostras_fiscais.py
"""
from pathlib import Path

SAIDA = Path('src/test/resources/amostras')
LEIAUTES = Path('src/main/resources/sped')
CNPJ = '11222333000181'
NOME = 'EMPRESA EXEMPLO LTDA'


def leiaute(tipo):
    campos = {}
    for linha in (LEIAUTES / f'{tipo}-leiautes.properties').read_text(encoding='utf-8').splitlines():
        if linha and not linha.startswith('#'):
            chave, valor = linha.split('=', 1)
            campos[chave] = valor.split(',')
    return campos


class Arquivo:
    def __init__(self, tipo, versao):
        self.campos = leiaute(tipo)
        self.versao = versao
        self.linhas = []

    def reg(self, registro, **valores):
        nomes = self.campos.get(f'{self.versao}.{registro}')
        if nomes is None:  # registro fora do leiaute lido (blocos pulados): só o código e os valores na ordem dada
            self.linhas.append('|' + '|'.join([registro] + [str(v) for v in valores.values()]) + '|')
            return
        desconhecidos = set(valores) - set(nomes)
        if desconhecidos:
            raise SystemExit(f'{registro}: campos fora do leiaute {self.versao}: {desconhecidos}')
        self.linhas.append('|' + '|'.join([registro] + [str(valores.get(n, '')) for n in nomes[1:]]) + '|')

    def bloco(self, letra, gerar):
        inicio = len(self.linhas)
        self.linhas.append(f'|{letra}001|0|')
        gerar()
        self.linhas.append(f'|{letra}990|{len(self.linhas) - inicio + 1}|')

    def texto(self, ajuste_9900=None):
        contagem = {}
        for l in self.linhas:
            r = l.split('|')[1]
            contagem[r] = contagem.get(r, 0) + 1
        registros = list(contagem) + ['9001', '9900', '9990', '9999']
        contagem.update({'9001': 1, '9990': 1, '9999': 1, '9900': len(registros)})
        if ajuste_9900:
            contagem[ajuste_9900[0]] += ajuste_9900[1]
        bloco_9 = ['|9001|0|'] + [f'|9900|{r}|{contagem[r]}|' for r in registros]
        bloco_9.append(f'|9990|{len(bloco_9) + 2}|')
        linhas = self.linhas + bloco_9
        linhas.append(f'|9999|{len(linhas) + 1}|')
        return '\r\n'.join(linhas) + '\r\n'


def gravar(nome, conteudo):
    SAIDA.mkdir(parents=True, exist_ok=True)
    (SAIDA / nome).write_bytes(conteudo.encode('iso-8859-1'))
    print(SAIDA / nome)


def tabela(a, registro, linhas):
    for codigo, descricao, valor in linhas:
        a.reg(registro, CODIGO=codigo, DESCRICAO=descricao, VALOR=valor)


# --- ECF ------------------------------------------------------------------------------------------------------------

def ecf_real(retificadora='N', base_irpj='3500,00', receita='8000,00', ajuste_9900=None):
    a = Arquivo('ecf', '0008')
    a.reg('0000', NOME_ESC='LECF', COD_VER='0008', CNPJ=CNPJ, NOME=NOME, IND_SIT_INI_PER='0', SIT_ESPECIAL='0',
          DT_INI='01012021', DT_FIN='31122021', RETIFICADORA=retificadora, NUM_REC='A' * 40 if retificadora == 'S' else '',
          TIP_ECF='0')
    a.reg('0001', IND_DAD='0')
    a.reg('0010', FORMA_TRIB='1', FORMA_APUR='A', COD_QUALIF_PJ='01', FORMA_TRIB_PER='RRRR', MES_BAL_RED='BBBBBBBBBBBB',
          TIP_ESC_PRE='', APUR_CSLL='A')
    a.linhas.append('|0990|4|')

    def bloco_l():
        a.reg('L030', DT_INI='01012021', DT_FIN='31122021', PER_APUR='A00')
        for codigo, descricao, tipo, nivel, superior, valor, dc in [
            ('3', 'RESULTADO LÍQUIDO DO PERÍODO', 'S', 1, '', '4500,00', 'C'),
            ('3.01', 'RESULTADO LÍQUIDO DO PERÍODO ANTES DO IRPJ E DA CSLL - ATIVIDADE GERAL', 'S', 2, '3', '4500,00', 'C'),
            ('3.01.01.01.01', 'RECEITA BRUTA', 'S', 5, '3.01.01.01', receita, 'C'),
            ('3.01.01.01.01.05', 'Receita da Revenda de Mercadorias no Mercado Interno', 'A', 6, '3.01.01.01.01', receita, 'C'),
            ('3.01.01.03.01.02', 'Custo das Mercadorias Revendidas', 'A', 6, '3.01.01.03.01', '3500,00', 'D'),
        ]:
            a.reg('L300', CODIGO=codigo, DESCRICAO=descricao, TIPO=tipo, NIVEL=nivel, COD_NAT='04', COD_CTA_SUP=superior,
                  VALOR=valor, IND_VALOR=dc)
    a.bloco('L', bloco_l)

    def bloco_m():
        a.reg('M010', COD_CTA_B='PF01', DESC_CTA_LAL='Prejuízo fiscal de períodos anteriores', DT_AP_LAL='31122019',
              COD_PB_RFB='1000', COD_TRIBUTO='I', VL_SALDO_INI='10000,00', IND_VL_SALDO_INI='D')
        a.reg('M010', COD_CTA_B='BN01', DESC_CTA_LAL='Base negativa da CSLL de períodos anteriores', DT_AP_LAL='31122019',
              COD_PB_RFB='1003', COD_TRIBUTO='C', VL_SALDO_INI='10000,00', IND_VL_SALDO_INI='D')
        a.reg('M030', DT_INI='01012021', DT_FIN='31122021', PER_APUR='A00')
        for registro, conta, nome in [('M300', 'PF01', 'Lucro Líquido Antes do IRPJ'), ('M350', 'BN01', 'Lucro Antes da CSLL')]:
            for codigo, descricao, tipo, valor in [
                ('2', nome, 'L', '4500,00'),
                ('6', 'Provisões ou perdas estimadas não dedutíveis', 'A', '500,00'),
                ('93', 'SOMA DAS ADIÇÕES', 'L', '500,00'),
                ('168', 'SOMA DAS EXCLUSÕES', 'L', '0,00'),
                ('169', 'LUCRO ANTES DA COMPENSAÇÃO DE PREJUÍZOS', 'L', '5000,00'),
                ('171', 'LUCRO APÓS A COMPENSAÇÃO DOS PREJUÍZOS DO PRÓPRIO PERÍODO', 'L', '5000,00'),
                ('173', 'Compensação de prejuízos de períodos anteriores', 'P', '1500,00'),
                ('175', 'LUCRO REAL' if registro == 'M300' else 'BASE DE CÁLCULO DA CSLL', 'L', '3500,00'),
            ]:
                a.reg(registro, CODIGO=codigo, DESCRICAO=descricao, TIPO_LANCAMENTO=tipo, IND_RELACAO='1' if tipo == 'P' else '4',
                      VALOR=valor, HIST_LAN_LAL='')
        for conta, tributo in [('PF01', 'I'), ('BN01', 'C')]:
            a.reg('M500', COD_CTA_B=conta, COD_TRIBUTO=tributo, SD_INI_LAL='10000,00', IND_SD_INI_LAL='D',
                  VL_LCTO_PARTE_A='1500,00', IND_VL_LCTO_PARTE_A='C', VL_LCTO_PARTE_B='0,00', IND_VL_LCTO_PARTE_B='D',
                  SD_FIM_LAL='8500,00', IND_SD_FIM_LAL='D')
    a.bloco('M', bloco_m)

    def bloco_n():
        a.reg('N030', DT_INI='01012021', DT_FIN='31012021', PER_APUR='A01')
        tabela(a, 'N620', [('1', 'Base de Cálculo do Imposto de Renda', '300,00'), ('3', 'A Alíquota de 15%', '45,00'),
                           ('4', 'Adicional', '0,00'), ('26', 'IMPOSTO DE RENDA DEVIDO NO MÊS', '45,00')])
        tabela(a, 'N660', [('2', 'Base de Cálculo da CSLL', '300,00'), ('3', 'CSLL Apurada', '27,00'),
                           ('18', 'CSLL DEVIDA NO MÊS', '27,00')])
        a.reg('N030', DT_INI='01012021', DT_FIN='31122021', PER_APUR='A00')
        tabela(a, 'N500', [('1', 'Valor da base de cálculo do IRPJ', base_irpj)])
        tabela(a, 'N630', [('1', 'BASE DE CÁLCULO DO IRPJ', '3500,00'), ('3', 'À Alíquota de 15%', '525,00'),
                           ('4', 'Adicional', '0,00'),
                           ('24', '(-) Imposto de Renda Mensal Efetivamente Pago por Estimativa', '45,00'),
                           ('26', 'IMPOSTO DE RENDA A PAGAR', '480,00')])
        tabela(a, 'N650', [('1', 'Valor da Base de Cálculo da CSLL', '3500,00')])
        tabela(a, 'N670', [('1', 'BASE DE CÁLCULO DA CSLL', '3500,00'),
                           ('4', 'TOTAL DA CONTRIBUIÇÃO SOCIAL SOBRE O LUCRO LÍQUIDO', '315,00'),
                           ('19', '(-) CSLL Mensal Efetivamente Paga por Estimativa', '27,00'),
                           ('21', 'CSLL A PAGAR', '288,00')])
    a.bloco('N', bloco_n)
    return a.texto(ajuste_9900)


def ecf_presumido():
    a = Arquivo('ecf', '0011')
    a.reg('0000', NOME_ESC='LECF', COD_VER='0011', CNPJ=CNPJ, NOME=NOME, IND_SIT_INI_PER='0', SIT_ESPECIAL='0',
          DT_INI='01012024', DT_FIN='31122024', RETIFICADORA='N', TIP_ECF='0')
    a.reg('0001', IND_DAD='0')
    a.reg('0010', FORMA_TRIB='5', FORMA_APUR='T', COD_QUALIF_PJ='01', FORMA_TRIB_PER='PPPP', TIP_ESC_PRE='C', APUR_CSLL='T')
    a.linhas.append('|0990|4|')

    def bloco_p():
        for tri, ini, fim, receita, base, irpj, base_csll, csll in [
            ('T01', '01012024', '31032024', '100000,00', '8000,00', '1200,00', '12000,00', '1080,00'),
            ('T02', '01042024', '30062024', '300000,00', '24000,00', '3600,00', '36000,00', '3240,00'),
        ]:
            a.reg('P030', DT_INI=ini, DT_FIN=fim, PER_APUR=tri)
            tabela(a, 'P200', [('4', 'Receita Bruta Sujeita ao Percentual de 8%', receita),
                               ('26', 'BASE DE CÁLCULO DO IMPOSTO SOBRE O LUCRO PRESUMIDO', base)])
            tabela(a, 'P300', [('1', 'BASE DE CÁLCULO DO IMPOSTO SOBRE O LUCRO PRESUMIDO', base), ('3', 'À Alíquota de 15%', irpj),
                               ('4', 'Adicional', '0,00'), ('15', 'IMPOSTO DE RENDA A PAGAR', irpj)])
            tabela(a, 'P400', [('21', 'BASE DE CÁLCULO DA CSLL', base_csll)])
            tabela(a, 'P500', [('1', 'BASE DE CÁLCULO DA CSLL', base_csll),
                               ('4', 'TOTAL DA CONTRIBUIÇÃO SOCIAL SOBRE O LUCRO LÍQUIDO', csll), ('13', 'CSLL A PAGAR', csll)])
        # DRE anual: resultado líquido de 4.500 credor, igual à soma do I355 da ecd-2024-l9 (conferência sem referencial)
        a.reg('P030', DT_INI='01012024', DT_FIN='31122024', PER_APUR='A00')
        a.reg('P150', CODIGO='3', DESCRICAO='RESULTADO LÍQUIDO DO PERÍODO', TIPO='S', NIVEL=1, COD_NAT='04', COD_CTA_SUP='',
              VALOR='4500,00', IND_VALOR='C')
    a.bloco('P', bloco_p)
    return a.texto()


# --- EFD-Contribuições -------------------------------------------------------------------------------------------------

def contribuicoes(regime='1', tipo_escrit='0', pis_rec='825,00', ajuste_9900=None):
    a = Arquivo('efd-contribuicoes', '006')
    a.reg('0000', COD_VER='006', TIPO_ESCRIT=tipo_escrit, IND_SIT_ESP='', NUM_REC_ANTERIOR='A' * 41 if tipo_escrit == '1' else '',
          DT_INI='01012025', DT_FIN='31012025', NOME=NOME, CNPJ=CNPJ, UF='PR', COD_MUN='4106902', SUFRAMA='',
          IND_NAT_PJ='00', IND_ATIV='2')
    a.linhas.append('|0001|0|')
    a.reg('0110', COD_INC_TRIB=regime, IND_APRO_CRED='1' if regime == '1' else '', COD_TIPO_CONT='1', IND_REG_CUM='')
    a.linhas.append('|0990|4|')
    # bloco C: documentos (pulados pela leitura, mas contados no 9900)
    a.linhas += ['|C001|0|', '|C010|11222333000181|2|', '|C100|1|0|F1|55|00|1|123||01012025|01012025|100000,00|',
                 '|C170|1|P1||1|UN|100000,00|', '|C990|5|']
    nc = regime == '1'
    linhas_m = ['|M001|0|']
    if nc:
        a.linhas += linhas_m
        a.reg('M100', COD_CRED='101', IND_CRED_ORI='0', VL_BC_PIS='50000,00', ALIQ_PIS='1,6500', VL_CRED='825,00',
              VL_AJUS_ACRES='0,00', VL_AJUS_REDUC='0,00', VL_CRED_DIF='0,00', VL_CRED_DISP='825,00', IND_DESC_CRED='0',
              VL_CRED_DESC='825,00', SLD_CRED='0,00')
        a.reg('M200', VL_TOT_CONT_NC_PER='1650,00', VL_TOT_CRED_DESC='825,00', VL_TOT_CRED_DESC_ANT='0,00',
              VL_TOT_CONT_NC_DEV='825,00', VL_RET_NC='0,00', VL_OUT_DED_NC='0,00', VL_CONT_NC_REC=pis_rec,
              VL_TOT_CONT_CUM_PER='0,00', VL_RET_CUM='0,00', VL_OUT_DED_CUM='0,00', VL_CONT_CUM_REC='0,00', VL_TOT_CONT_REC=pis_rec)
        m210(a, 'M210', '01', '1,6500', '1650,00', 'PIS')
        a.reg('M500', COD_CRED='101', IND_CRED_ORI='0', VL_BC_COFINS='50000,00', ALIQ_COFINS='7,6000', VL_CRED='3800,00',
              VL_AJUS_ACRES='0,00', VL_AJUS_REDUC='0,00', VL_CRED_DIFER='0,00', VL_CRED_DISP='3800,00', IND_DESC_CRED='0',
              VL_CRED_DESC='3800,00', SLD_CRED='0,00')
        a.reg('M600', VL_TOT_CONT_NC_PER='7600,00', VL_TOT_CRED_DESC='3800,00', VL_TOT_CRED_DESC_ANT='0,00',
              VL_TOT_CONT_NC_DEV='3800,00', VL_RET_NC='0,00', VL_OUT_DED_NC='0,00', VL_CONT_NC_REC='3800,00',
              VL_TOT_CONT_CUM_PER='0,00', VL_RET_CUM='0,00', VL_OUT_DED_CUM='0,00', VL_CONT_CUM_REC='0,00', VL_TOT_CONT_REC='3800,00')
        m210(a, 'M610', '01', '7,6000', '7600,00', 'COFINS')
    else:
        a.linhas += linhas_m
        a.reg('M200', VL_TOT_CONT_NC_PER='0,00', VL_TOT_CRED_DESC='0,00', VL_TOT_CRED_DESC_ANT='0,00', VL_TOT_CONT_NC_DEV='0,00',
              VL_RET_NC='0,00', VL_OUT_DED_NC='0,00', VL_CONT_NC_REC='0,00', VL_TOT_CONT_CUM_PER='650,00', VL_RET_CUM='0,00',
              VL_OUT_DED_CUM='0,00', VL_CONT_CUM_REC='650,00', VL_TOT_CONT_REC='650,00')
        m210(a, 'M210', '51', '0,6500', '650,00', 'PIS')
        a.reg('M600', VL_TOT_CONT_NC_PER='0,00', VL_TOT_CRED_DESC='0,00', VL_TOT_CRED_DESC_ANT='0,00', VL_TOT_CONT_NC_DEV='0,00',
              VL_RET_NC='0,00', VL_OUT_DED_NC='0,00', VL_CONT_NC_REC='0,00', VL_TOT_CONT_CUM_PER='3000,00', VL_RET_CUM='0,00',
              VL_OUT_DED_CUM='0,00', VL_CONT_CUM_REC='3000,00', VL_TOT_CONT_REC='3000,00')
        m210(a, 'M610', '51', '3,0000', '3000,00', 'COFINS')
    inicio_m = a.linhas.index('|M001|0|')
    a.linhas.append(f'|M990|{len(a.linhas) - inicio_m + 1}|')
    if nc:
        inicio_1 = len(a.linhas)
        a.linhas.append('|1001|0|')
        for registro, saldo in [('1100', '1000,00'), ('1500', '4000,00')]:
            a.reg(registro, PER_APU_CRED='122024', ORIG_CRED='01', CNPJ_SUC='', COD_CRED='101', VL_CRED_APU=saldo,
                  VL_CRED_EXT_APU='0,00', VL_TOT_CRED_APU=saldo, VL_CRED_DESC_PA_ANT='0,00', VL_CRED_PER_PA_ANT='0,00',
                  VL_CRED_DCOMP_PA_ANT='0,00', SD_CRED_DISP_EFD=saldo, VL_CRED_DESC_EFD='0,00', VL_CRED_PER_EFD='0,00',
                  VL_CRED_DCOMP_EFD='0,00', VL_CRED_TRANS='0,00', VL_CRED_OUT='0,00', SLD_CRED_FIM=saldo)
        a.linhas.append(f'|1990|{len(a.linhas) - inicio_1 + 1}|')
    return a.texto(ajuste_9900)


def m210(a, registro, codigo, aliquota, contribuicao, tributo):
    campos = dict(COD_CONT=codigo, VL_REC_BRT='100000,00', VL_BC_CONT='100000,00', VL_BC_CONT_AJUS='100000,00',
                  VL_CONT_APUR=contribuicao, VL_AJUS_ACRES='0,00', VL_AJUS_REDUC='0,00', VL_CONT_DIFER='0,00',
                  VL_CONT_DIFER_ANT='0,00', VL_CONT_PER=contribuicao)
    campos[f'VL_AJUS_ACRES_BC_{tributo}'] = '0,00'
    campos[f'VL_AJUS_REDUC_BC_{tributo}'] = '0,00'
    campos[f'ALIQ_{tributo}'] = aliquota
    a.reg(registro, **campos)


# --- EFD ICMS/IPI --------------------------------------------------------------------------------------------------

def icms_ipi(mes='01', cod_fin='0', recolher='5000,00', e116='5000,00', ajuste_9900=None, saldo_credor=False):
    a = Arquivo('efd-icms-ipi', '019')
    fim = '31' if mes == '01' else '28'
    a.reg('0000', COD_VER='019', COD_FIN=cod_fin, DT_INI=f'01{mes}2025', DT_FIN=f'{fim}{mes}2025', NOME=NOME, CNPJ=CNPJ,
          CPF='', UF='PR', IE='9012345678', COD_MUN='4106902', IM='', SUFRAMA='', IND_PERFIL='A', IND_ATIV='0')
    a.linhas += ['|0001|0|', '|0990|3|']
    # bloco C: documentos (pulados pela leitura, mas contados no 9900)
    a.linhas += ['|C001|0|', '|C100|1|0|F1|55|00|1|123||01012025|01012025|100000,00|',
                 '|C170|1|P1||1|UN|100000,00|', '|C190|000|5102|18,00|100000,00|18000,00|', '|C990|5|']
    inicio_e = len(a.linhas)
    a.linhas.append('|E001|0|')
    a.reg('E100', DT_INI=f'01{mes}2025', DT_FIN=f'{fim}{mes}2025')
    if saldo_credor:
        a.reg('E110', VL_TOT_DEBITOS='10000,00', VL_AJ_DEBITOS='0,00', VL_TOT_AJ_DEBITOS='0,00', VL_ESTORNOS_CRED='0,00',
              VL_TOT_CREDITOS='12000,00', VL_AJ_CREDITOS='0,00', VL_TOT_AJ_CREDITOS='0,00', VL_ESTORNOS_DEB='0,00',
              VL_SLD_CREDOR_ANT='0,00', VL_SLD_APURADO='0,00', VL_TOT_DED='0,00', VL_ICMS_RECOLHER='0,00',
              VL_SLD_CREDOR_TRANSPORTAR='2000,00', DEB_ESP='0,00')
    else:
        a.reg('E110', VL_TOT_DEBITOS='18000,00', VL_AJ_DEBITOS='0,00', VL_TOT_AJ_DEBITOS='500,00', VL_ESTORNOS_CRED='0,00',
              VL_TOT_CREDITOS='12000,00', VL_AJ_CREDITOS='0,00', VL_TOT_AJ_CREDITOS='300,00', VL_ESTORNOS_DEB='0,00',
              VL_SLD_CREDOR_ANT='1000,00', VL_SLD_APURADO='5200,00', VL_TOT_DED='200,00', VL_ICMS_RECOLHER=recolher,
              VL_SLD_CREDOR_TRANSPORTAR='0,00', DEB_ESP='0,00')
        a.reg('E111', COD_AJ_APUR='PR000001', DESCR_COMPL_AJ='Outros débitos: diferença de estoque', VL_AJ_APUR='500,00')
        a.reg('E111', COD_AJ_APUR='PR020001', DESCR_COMPL_AJ='Outros créditos: crédito presumido', VL_AJ_APUR='300,00')
        a.reg('E111', COD_AJ_APUR='PR040001', DESCR_COMPL_AJ='Dedução: incentivo cultural', VL_AJ_APUR='200,00')
        a.reg('E116', COD_OR='000', VL_OR=e116, DT_VCTO=f'10{int(mes) + 1:02d}2025', COD_REC='1015', NUM_PROC='', IND_PROC='',
              PROC='', TXT_COMPL='', MES_REF=f'{mes}2025')
        a.reg('E200', UF='SP', DT_INI=f'01{mes}2025', DT_FIN=f'{fim}{mes}2025')
        a.reg('E210', IND_MOV_ST='1', VL_SLD_CRED_ANT_ST='0,00', VL_DEVOL_ST='0,00', VL_RESSARC_ST='0,00', VL_OUT_CRED_ST='0,00',
              VL_AJ_CREDITOS_ST='0,00', VL_RETENCAO_ST='3000,00', VL_OUT_DEB_ST='0,00', VL_AJ_DEBITOS_ST='0,00',
              VL_SLD_DEV_ANT_ST='3000,00', VL_DEDUCOES_ST='0,00', VL_ICMS_RECOL_ST='3000,00', VL_SLD_CRED_ST_TRANSPORTAR='0,00',
              DEB_ESP_ST='0,00')
        a.reg('E250', COD_OR='002', VL_OR='3000,00', DT_VCTO=f'09{int(mes) + 1:02d}2025', COD_REC='100099', MES_REF=f'{mes}2025')
        a.reg('E300', UF='SP', DT_INI=f'01{mes}2025', DT_FIN=f'{fim}{mes}2025')
        a.reg('E310', IND_MOV_FCP_DIFAL='1', VL_SLD_CRED_ANT_DIFAL='0,00', VL_TOT_DEBITOS_DIFAL='400,00', VL_OUT_DEB_DIFAL='0,00',
              VL_TOT_CREDITOS_DIFAL='0,00', VL_OUT_CRED_DIFAL='0,00', VL_SLD_DEV_ANT_DIFAL='400,00', VL_DEDUCOES_DIFAL='0,00',
              VL_RECOL_DIFAL='400,00', VL_SLD_CRED_TRANSPORTAR_DIFAL='0,00', DEB_ESP_DIFAL='0,00', VL_SLD_CRED_ANT_FCP='0,00',
              VL_TOT_DEB_FCP='80,00', VL_OUT_DEB_FCP='0,00', VL_TOT_CRED_FCP='0,00', VL_OUT_CRED_FCP='0,00',
              VL_SLD_DEV_ANT_FCP='80,00', VL_DEDUCOES_FCP='0,00', VL_RECOL_FCP='80,00', VL_SLD_CRED_TRANSPORTAR_FCP='0,00',
              DEB_ESP_FCP='0,00')
        a.reg('E316', COD_OR='000', VL_OR='400,00', DT_VCTO=f'09{int(mes) + 1:02d}2025', COD_REC='100102', MES_REF=f'{mes}2025')
        a.reg('E316', COD_OR='006', VL_OR='80,00', DT_VCTO=f'09{int(mes) + 1:02d}2025', COD_REC='100129', MES_REF=f'{mes}2025')
    a.reg('E500', IND_APUR='0', DT_INI=f'01{mes}2025', DT_FIN=f'{fim}{mes}2025')
    if saldo_credor:
        a.reg('E520', VL_SD_ANT_IPI='0,00', VL_DEB_IPI='1000,00', VL_CRED_IPI='1500,00', VL_OD_IPI='0,00', VL_OC_IPI='0,00',
              VL_SC_IPI='500,00', VL_SD_IPI='0,00')
    else:
        a.reg('E520', VL_SD_ANT_IPI='0,00', VL_DEB_IPI='2000,00', VL_CRED_IPI='1500,00', VL_OD_IPI='100,00', VL_OC_IPI='0,00',
              VL_SC_IPI='0,00', VL_SD_IPI='600,00')
        a.reg('E530', IND_AJ='0', VL_AJ='100,00', COD_AJ='001', IND_DOC='0', NUM_DOC='', DESCR_AJ='Estorno de crédito')
    a.linhas.append(f'|E990|{len(a.linhas) - inicio_e + 1}|')
    return a.texto(ajuste_9900)


gravar('ecf-real-2021.txt', ecf_real())
gravar('ecf-real-2021-retificadora.txt', ecf_real(retificadora='S'))
gravar('ecf-real-2021-base-diverge.txt', ecf_real(base_irpj='4000,00'))
gravar('ecf-real-2021-receita-diverge.txt', ecf_real(receita='8500,00'))
gravar('ecf-contagem-errada.txt', ecf_real(ajuste_9900=('M300', 1)))
gravar('ecf-presumido-2024.txt', ecf_presumido())
gravar('efd-contribuicoes-nc-2025-01.txt', contribuicoes())
gravar('efd-contribuicoes-cum-2025-01.txt', contribuicoes(regime='2'))
gravar('efd-contribuicoes-retificadora-2025-01.txt', contribuicoes(tipo_escrit='1'))
gravar('efd-contribuicoes-formula-2025-01.txt', contribuicoes(pis_rec='900,00'))
gravar('efd-contribuicoes-contagem-errada.txt', contribuicoes(ajuste_9900=('C170', 2)))
gravar('efd-icms-ipi-2025-01.txt', icms_ipi())
gravar('efd-icms-ipi-saldo-credor-2025-02.txt', icms_ipi(mes='02', saldo_credor=True))
gravar('efd-icms-ipi-retificadora-2025-01.txt', icms_ipi(cod_fin='1'))
gravar('efd-icms-ipi-formula-2025-01.txt', icms_ipi(recolher='5100,00', e116='5100,00'))
gravar('efd-icms-ipi-contagem-errada.txt', icms_ipi(ajuste_9900=('E111', 1)))
