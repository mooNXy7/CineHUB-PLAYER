# Contrato de integração — CineHUB-ACERVO

## Princípio
O player consome dados do acervo por URLs públicas estáveis configuradas em um único lugar. Não deve depender de caminhos internos de outro app nem copiar o catálogo inteiro para o código do player.

## Modelo normalizado sugerido
```json
{
  "schema_version": 1,
  "id": "stable-content-id",
  "type": "movie",
  "title": "Título",
  "poster_url": "https://...",
  "sources": [
    {
      "id": "source-id",
      "url": "https://...",
      "provider": "provider-name",
      "label": "Dublado",
      "priority": 1,
      "kind": "hls"
    }
  ],
  "audio_languages": [],
  "subtitle_languages": [],
  "metadata": {
    "year": 2026,
    "genres": [],
    "similar_ids": []
  }
}
```

Esse JSON é um contrato de referência, não uma afirmação de que o repositório já publica exatamente esse arquivo. O adaptador deve ser ajustado após mapear os arquivos reais do CineHUB-ACERVO.

## Tipos
- `live`: canal/transmissão ao vivo; sem pausa e sem retomada.
- `movie`: filme; salva progresso.
- `series_episode`: episódio com `series_id`, `season_number` e `episode_number`.
- `radio`: stream de áudio; controles apropriados a rádio.

## Requisitos do cliente
- Timeout, limite de bytes e User-Agent identificável.
- Cache persistente com tamanho máximo e expiração.
- Cache stale-while-revalidate: se a atualização falhar, manter a última cópia válida.
- Validar esquema e campos antes de expor itens à UI.
- Não registrar URLs completas se contiverem tokens.
- Deduplicar por ID estável; fallback por título normalizado + ano apenas para dados sem ID.
- Nunca considerar um stream funcional apenas por existir no catálogo: erros de reprodução são tratados no player.
