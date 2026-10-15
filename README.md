# CineHUB PLAYER

Player modular do CineHUB, com foco em Android TV/Google TV/Fire TV e TV Boxes com pouca memória.

## Objetivos

- Reprodução de VOD e transmissões ao vivo com políticas separadas.
- Recuperação de fontes de VOD com timeout configurável (padrão de 15 s).
- Retomada de reprodução e progresso por item/episódio.
- Velocidade, seleção de áudio, legendas externas e sincronização.
- Painel de episódios, próximo episódio e opção de pular abertura/recapitulação.
- Navegação integral por D-pad, teclado e mouse.
- Limites de buffer e resolução configuráveis por perfil de dispositivo.
- Integração desacoplada com o catálogo publicado em `CineHUB-ACERVO`.

## Repositórios de referência autorizados pelo mantenedor

- [SaimoPlayer](https://github.com/gabrielsaimo/SaimoPlayer) — catálogo, fontes alternativas, progresso, legendas, TheIntroDB e regras de VOD.
- [SaimoTV-Android](https://github.com/gabrielsaimo/SaimoTV-Android) — referência Android TV e integração nativa.
- [CineHUB-ACERVO](https://github.com/mooNXy7/CineHUB-ACERVO) — fonte de metadados e itens do catálogo.

O código desses projetos será adaptado por módulo, preservando os avisos de licença e atribuição de cada dependência. A autorização do mantenedor para reutilizar código não substitui licenças de terceiros.

## Estado atual

- Android debug APK compilado e empacotado com sucesso em GitHub Actions; testes unitários do módulo core e tarefa de teste Android também passaram numa execução recente.
- Prévia incremental do acervo para filmes, séries, animes e doramas; prévia de rádios.
- Fluxo de seleção por temporada, episódio e versão de áudio, com fila, painel de próximos episódios acessível durante a reprodução, autoplay de 10 segundos quando o catálogo contém URLs diretas e opção de pular para o próximo item se todas as fontes falharem.
- Perfil de buffer selecionável: automático, economia de memória ou desempenho; no modo automático considera dispositivos Android de pouca RAM.
- Interface de teste e controles de reprodução adaptáveis a telas menores: menu vertical rolável, categorias horizontais roláveis e barra de ações do player com rolagem horizontal para não esconder opções em TV Boxes 720p.
- As referências indexadas de provedores são convertidas em URLs candidatas usando `SaimoPlayer/vod/indice.txt`; páginas embed continuam excluídas e ainda precisam de resolvedores próprios. O player permite HTTP legado para fontes antigas, preferindo HTTPS quando disponível.
- As políticas de fontes, URLs seguras, persistência de progresso e normalização da fila de episódios têm testes unitários. A fila de autoplay remove IDs duplicados, descarta URLs inválidas e limita o tamanho a 100 episódios e 12 fontes por episódio para evitar payloads excessivos em TV Boxes de pouca memória. Se o player sair de cena enquanto ainda está carregando ou falhou antes de ficar pronto, o progresso salvo anteriormente é preservado; ao concluir o conteúdo ou chegar perto do fim, a posição é zerada para não retomar no último quadro. URLs de reprodução e legendas recebidas por intents são validadas como HTTP(S), com host e sem credenciais. Ainda faltam testes em dispositivos reais e integrações OpenSubtitles/TheIntroDB; o painel atual lista os próximos episódios, enquanto um seletor completo de temporada/episódio sobreposto ao vídeo ainda está pendente.

O repositório agora contém um projeto Android Gradle inicial com Kotlin, Media3/ExoPlayer, um módulo `player-core`, tela de teste para streams, tentativa de fontes alternativas, retomada local VOD, seleção de faixas, velocidade e cliente incremental do CineHUB-ACERVO, prévias de categorias, detalhes e episódios, seleção de rádios e tentativa de reprodução das fontes diretas publicadas. O workflow de CI foi adicionado para gerar um APK de depuração.

**Validação atual:** a execução mais recente do GitHub Actions concluiu com sucesso os testes e a compilação do APK de depuração. Isso valida o código em CI, mas não substitui testes de reprodução em TV Box/Google TV/Fire TV. OpenSubtitles, sincronização fina de legendas, TheIntroDB, seletor completo de temporadas/episódios sobreposto ao vídeo e resolução automática de páginas embed ainda não estão completos.

## Arquitetura proposta

- `player-core/`: estado de reprodução, fontes, progresso e políticas de buffer.
- `player-android/`: integração Android com Media3/ExoPlayer e controles de TV.
- `catalog/`: cliente do CineHUB-ACERVO e normalização dos metadados.
- `integrations/`: OpenSubtitles e TheIntroDB por adaptadores.
- `docs/`: decisões de arquitetura, contratos e plano de testes.

Consulte `docs/ARCHITECTURE.md`, `docs/CATALOG-CONTRACT.md` e `docs/ROADMAP.md`.
