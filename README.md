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

O repositório agora contém um projeto Android Gradle inicial com Kotlin, Media3/ExoPlayer, um módulo `player-core`, tela de teste para streams, tentativa de fontes alternativas, retomada local VOD, seleção de faixas, velocidade e cliente incremental do CineHUB-ACERVO, prévias de categorias, detalhes e episódios, seleção de rádios e tentativa de reprodução das fontes diretas publicadas. O workflow de CI foi adicionado para gerar um APK de depuração.

**Validação pendente:** ainda não há confirmação de build verde nem testes em TV Box/Google TV/Fire TV. OpenSubtitles, sincronização fina de legendas, TheIntroDB, painel de episódios e resolução automática por conteúdo ainda não estão completos.

## Arquitetura proposta

- `player-core/`: estado de reprodução, fontes, progresso e políticas de buffer.
- `player-android/`: integração Android com Media3/ExoPlayer e controles de TV.
- `catalog/`: cliente do CineHUB-ACERVO e normalização dos metadados.
- `integrations/`: OpenSubtitles e TheIntroDB por adaptadores.
- `docs/`: decisões de arquitetura, contratos e plano de testes.

Consulte `docs/ARCHITECTURE.md`, `docs/CATALOG-CONTRACT.md` e `docs/ROADMAP.md`.
