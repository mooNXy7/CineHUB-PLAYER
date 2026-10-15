# Player Core

Lógica independente da UI Android. Implementar aqui políticas testáveis antes de conectá-las aos componentes de tela.

Componentes planejados:
- `SourceFailoverPolicy`: lista de fontes ordenada, timeout de 15 s configurável, tentativas sem loop infinito.
- `PlaybackProgress`: posição/duração, retomada e conclusão.
- `PlaybackMode`: LIVE, VOD, RADIO.
- `BufferProfile`: padrão, baixo consumo (TV Box 1 GB) e qualidade alta.
- `EpisodeQueue`: sequência de episódios e preferência de provedor.
- `PlaybackPreferences`: velocidade, resolução máxima e opções de legenda.

Não duplicar o engine Media3 neste módulo. O core decide políticas; o adaptador Android executa operações reais.
