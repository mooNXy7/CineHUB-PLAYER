# Contrato de integração — CineHUB TV → CineHUB PLAYER

## Objetivo

Este contrato documenta os dados aceitos pelo player e a fronteira segura para chamadas explícitas entre apps. O engine ainda não foi extraído como biblioteca reutilizável.

## Estado atual

- Package: `com.cinehub.player`
- Activity de teste: `com.cinehub.player.PlayerActivity`
- A Activity é exportada apenas para chamadas explícitas e protegida pela permissão de assinatura `com.cinehub.player.permission.PLAYBACK`.
- O engine Media3 continua dentro do app Android atual; a extração para um módulo reutilizável ainda está pendente.

## Lançamento seguro entre aplicativos

O APK chamador (CineHUB TV) precisa declarar a permissão abaixo e ser assinado com o mesmo certificado do CineHUB PLAYER:

```xml
<uses-permission android:name="com.cinehub.player.permission.PLAYBACK" />
```

Use um Intent explícito para `com.cinehub.player/.PlayerActivity`. As URLs recebidas continuam passando pela validação HTTP(S). Não remova a permissão de assinatura nem adicione filtros de Intent públicos para produção.

## Extras aceitos pelo player de teste

| Extra | Tipo | Uso |
|---|---|---|
| `url` | String | Fonte única HTTP(S) |
| `sources` | ArrayList<String> | Fontes alternativas em ordem de preferência |
| `source_referers` | ArrayList<String> | Referer opcional alinhado por índice a cada fonte; usado para enviar Referer/Origin ao servidor |
| `live` | Boolean | Ativa comportamento LIVE; não pausa por PLAY/PAUSE |
| `radio` | Boolean | Ativa a tela de áudio dedicada |
| `content_type` | String | `radio` ou `live` |
| `content_id` | String | Chave estável de progresso VOD |
| `subtitle_url` | String | Legenda externa HTTP(S) opcional |
| `tmdb_id` | Int | Identificador usado por OpenSubtitles/TheIntroDB |
| `series_id` | String | Agrupa progresso/preferência de fonte da série |
| `catalog_category` | String | Categoria de origem para recomendações (`movies`, `series`, `anime`, `doramas`) |
| `catalog_item_id` | String | ID exato do item no catálogo, usado para excluir o título atual das recomendações |
| `catalog_genres` | ArrayList<String> | Gêneros usados na tela “Mais como este” |
| `season_number`, `episode_number` | Int | Contexto do episódio |
| `episode_queue` | String JSON | Fila limitada de episódios, cada item com `title`, `content_id`, `sources`, `tmdb_id`, `season`, `episode` e `poster_url` opcional |
| `episode_queue_current_index` | Int | Índice do item atual na fila |

## Regras obrigatórias do chamador

1. Passar apenas URLs HTTP(S) absolutas, sem credenciais embutidas.
2. Para VOD, usar IDs estáveis por filme/episódio para que a retomada não se misture.
3. Marcar canais como `live=true`; marcar rádio também como `radio=true` e passar `content_type=radio`.
4. Enviar uma lista limitada de fontes; o player deduplica as URLs e limita a fila de episódios.
5. Não enviar catálogo inteiro em um Intent. Carregar páginas pequenas e buscar detalhes sob demanda.
6. Não assumir que URL publicada funciona: erros de preparação, buffering e encerramento acionam tentativa de fonte alternativa.
7. Não passar credenciais de serviços em texto claro dentro de extras ou logs.

## Próximos passos para integração real

- Extrair o mecanismo Media3 de `PlayerActivity` para um módulo Android reutilizável e manter o app atual como harness de teste.
- Extrair o mecanismo Media3 para um módulo Android reutilizável e versionar a API de lançamento.
- Versionar os modelos de fonte com URL, tipo de mídia, idioma, prioridade e cabeçalhos permitidos.
- Testar navegação, retomada e reprodução em Android TV/TVBox antes de migrar o CineHUB TV.
