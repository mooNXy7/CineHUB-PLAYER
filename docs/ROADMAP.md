# Roadmap de implementação

## Fase 0 — Estrutura
- [x] Criar repositório e documentar arquitetura.
- [x] Definir contrato de consumo do CineHUB-ACERVO.
- [ ] Mapear arquivos exatos dos projetos de referência e suas licenças.

## Fase 1 — Playback mínimo
- [ ] Projeto Android Gradle compilável com Media3.
- [ ] Reprodução de HLS, DASH e MP4 compatíveis com o dispositivo.
- [ ] Controles Play/Pause, seek, faixa de áudio/legenda e velocidade.
- [ ] D-pad, voltar e restauração correta de foco.

## Fase 2 — Resiliência
- [ ] Timeout configurável de início (padrão 15 s) e troca de fonte.
- [ ] Buffer por perfil de memória e modo de baixo consumo.
- [ ] Retomada persistente por item e episódio.
- [ ] Política LIVE separada de VOD; live nunca pausa.

## Fase 3 — Séries e integrações
- [ ] PainelEpisodios com temporada/episódio.
- [ ] Próximo episódio e preferência pelo mesmo provedor/fonte.
- [ ] TheIntroDB para intro/recap, com fallback manual.
- [ ] OpenSubtitles: idioma, tamanho, fundo e sincronização.

## Fase 4 — Pós-reprodução e catálogo
- [ ] Cartão do próximo episódio e tela Mais como este.
- [ ] Confirmação “Ainda está assistindo?” baseada em inatividade/configuração.
- [ ] Cliente CineHUB-ACERVO com cache e atualização incremental.
- [ ] Testes em TV Box 1 GB, Google TV e Fire TV.

## Critérios de aceite
- Compila em CI.
- Testes unitários de política live/VOD e failover.
- Sem travamento quando catálogo ou integrações externas falham.
- Sem carregar catálogo completo na memória.
- Navegação testada com controle remoto real.
- Testes de reprodução só com streams de teste autorizados.
