-- So no perfil dev. Antes, valores APROXIMADOS da UFPR para desenvolvimento; desde a Fase 5 nao ha atualizacao
-- monetaria pela UFPR no PR (Lei 15.610/2007) e a UPF/PR oficial vem de resources/parcelamento/upf-pr.csv. O arquivo
-- fica (repetivel ja aplicado nos bancos de dev) so para apagar o que ele mesmo semeou.
DELETE FROM indice_monetario WHERE tipo = 'UFPR' AND origem = 'SEED_DEV';
