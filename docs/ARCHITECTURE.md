# Arquitetura do CineHUB PLAYER

## Princípio
Separar mecanismo de reprodução, interface de TV, catálogo e integrações externas. A falha de um provedor de metadados nunca deve impedir a reprodução de uma URL já recebida.

## Módulos
- `player-android`: aplicação Android nativa Kotlin, Media3/ExoPlayer, sessão de mídia e foco D-pad.
- `player-core`: políticas puras testáveis: troca de fonte, retomada, buffer, resolução e regras live/VOD.
- `catalog`: cliente HTTP com cache local, normalização e modelos compatíveis com CineHUB-ACERVO.
- `integrations/opensubtitles`: pesquisa e seleção de legendas, tratamento de credenciais se a API exigir.
- `integrations/theintrodb`: obtenção e cache dos intervalos de intro/recap.
- `docs`: decisões e matriz de compatibilidade.

## Regras invariáveis
1. Ao vivo não pausa: o comando PLAY/PAUSE é ignorado para itens marcados como LIVE.
2. VOD salva posição periodicamente e ao sair; posições próximas ao fim são marcadas como concluídas.
3. Fonte alternativa só é tentada após erro de preparação/reprodução ou timeout de início; padrão 15 s, configurável.
4. A troca de fonte mantém posição quando o protocolo permite seek; caso contrário inicia do começo e registra o motivo.
5. Nunca tentar baixar o catálogo inteiro para a RAM. Usar índices, paginação, cache limitado e leitura sob demanda.
6. Em perfil de 1 GB RAM, desativar pré-carregamento de cards/vídeos e limitar buffer, bitmaps e concorrência de rede.
7. Falhas em OpenSubtitles, TheIntroDB ou catálogo são não fatais e não bloqueiam o player.

## Fontes de referência
- SaimoPlayer: lógica de catálogo, prioridade de fontes, legendas, TheIntroDB e progresso.
- SaimoTV-Android: integração Android TV, Media3 e navegação por controle remoto.
- CineHUB-ACERVO: contrato de dados independente dos aplicativos.

A adaptação deve registrar a origem de cada módulo e preservar os avisos/licenças originais e de dependências.
