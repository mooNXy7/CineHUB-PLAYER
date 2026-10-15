# Roadmap de implementação

## Fase 0 — Estrutura
- [x] Criar repositório e documentar arquitetura.
- [x] Definir contrato de consumo do CineHUB-ACERVO.
- [x] Mapear pontos de integração Android Media3 e arquivos de referência relevantes.
- [ ] Registrar inventário de licenças arquivo a arquivo antes de copiar módulos completos.

## Fase 1 — Playback mínimo
- [x] Criar projeto Android Gradle com módulo Media3 e módulo Kotlin core (build ainda pendente de CI confirmado).
- [x] Criar tela de teste com URL HTTP(S), reprodução e fontes alternativas.
- [x] Implementar controles básicos do Media3, seleção de faixas de áudio/legendas e seletor de velocidade para VOD.
- [x] Implementar política para ignorar pausa em LIVE e restauração inicial de posição VOD.
- [ ] Confirmar compilação e reprodução de HLS, DASH e MP4 em aparelhos reais.
- [ ] Validar D-pad, voltar e restauração correta de foco.

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
