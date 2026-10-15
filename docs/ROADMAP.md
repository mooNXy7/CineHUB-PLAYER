# Roadmap de implementação

## Fase 0 — Estrutura
- [x] Criar repositório e documentar arquitetura.
- [x] Definir contrato de consumo do CineHUB-ACERVO.
- [x] Mapear pontos de integração Android Media3 e arquivos de referência relevantes.
- [ ] Registrar inventário de licenças arquivo a arquivo antes de copiar módulos completos.

## Fase 1 — Playback mínimo
- [x] Criar projeto Android Gradle com módulo Media3 e módulo Kotlin core (build e testes unitários confirmados em CI; validar novamente após mudanças recentes).
- [x] Criar tela de teste com URL HTTP(S), reprodução e fontes alternativas.
- [x] Consultar índice VOD e carregar prévias limitadas de filmes/séries/animes/doramas sem ler o catálogo inteiro.
- [x] Abrir metadados de um título, extrair fontes/episódios e preservar referências de provedores de forma incremental.
- [x] Carregar prévia de rádios e permitir iniciar uma estação selecionada.
- [x] Criar tela de áudio dedicada para rádio com identidade CineHUB, estação/gênero, arte sonora animada, estado de reprodução, volume, pausar/retomar e voltar.
- [x] Resolver referências indexadas do SaimoPlayer por meio de `vod/indice.txt` e montar URLs candidatas de mídia.
- [ ] Implementar resolvedores específicos para páginas embed e formatos especiais. Cabeçalhos genéricos `Accept`/User-Agent e regras iniciais para hosts conhecidos já foram adicionados; ainda falta transportar metadados `Referer` por fonte.
- [x] Implementar controles básicos do Media3, seleção de faixas de áudio/legendas e seletor de velocidade para VOD.
- [x] Manter os controles do player acessíveis em telas estreitas usando rolagem horizontal e foco compatível com controle remoto.
- [x] Implementar política para ignorar pausa em LIVE e restauração inicial de posição VOD.
- [x] Navegar temporadas, episódios e versões de áudio antes de iniciar a reprodução.
- [ ] Confirmar reprodução de HLS, DASH e MP4 em aparelhos reais (CI cobre compilação, não reprodução real). Detecção MIME HLS inclui manifests `master.txt` do host `embedplayer2.xyz`.
- [ ] Validar D-pad, voltar e restauração correta de foco em controle físico.

## Fase 2 — Resiliência
- [x] Timeout configurável de início (padrão 15 s; configurável de 10 a 45 s pela tela de teste), troca por erro e watchdog de buffering. Falta validar falhas reais em streams e dispositivos.
- [x] Buffer por perfil automático/economia/desempenho, com valores reduzidos para aparelhos de pouca memória.
- [x] Retomada persistente por item/episódio via armazenamento local, com salvamento periódico a cada 15 s, ao pausar e ao sair.
- [x] Política LIVE separada de VOD; live nunca pausa.

## Fase 3 — Séries e integrações
- [x] Painel de temporadas/episódios acessível durante a reprodução, limitado a uma janela de até 100 episódios ao redor do selecionado.
- [x] Fila de próximos episódios com aviso de 10 s e autoplay quando existem URLs diretas.
- [x] Navegação durante a reprodução por temporadas e episódios disponíveis; fila limitada a 100 episódios e 3 fontes por episódio para reduzir o risco de payload excessivo.
- [x] Evitar diálogos duplicados quando múltiplas falhas de fonte ocorrem quase ao mesmo tempo.
- [x] Reutilizar a última fonte bem-sucedida no próximo episódio quando o host aparece entre as opções.
- [x] Normalizar a fila de episódios: remover IDs/URLs duplicados, filtrar URLs inseguras e limitar filas/fontes para proteger aparelhos com pouca memória.
- [x] TheIntroDB: consulta assíncrona por TMDB ID e prompt para pular abertura/recap/prévia quando existem marcações válidas; falha da API não interrompe o vídeo.
- [x] Busca de legendas OpenSubtitles por idioma, com mapeamento TMDB → IMDb por fragmento e fallback para URL manual.
- [x] Ajuste visual de legenda com tamanho pequeno/padrão/grande e fundo configurável separadamente.
- [x] Ajuste de sincronização real para legendas externas SRT/WebVTT (−5 s, −2 s, original, +2 s, +5 s), com reescrita de timestamps em arquivo local e testes unitários.
- [ ] Sincronização de faixas de legenda embutidas e valores de atraso mais finos.

## Fase 4 — Pós-reprodução e catálogo
- [ ] Cartão visual do próximo episódio e recomendações reais por metadados (o aviso/autoplay de próximo episódio já existe, mas ainda não é o cartão visual).
- [x] Confirmação “Ainda está assistindo?” após duas horas sem interação em VOD; pausa o vídeo e permite continuar ou manter pausado.
- [ ] Cache persistente em disco e atualização incremental. Já existe cache em memória com TTL de 5 min, limite de 32 entradas e fallback para dados antigos quando a rede falha; prévias de VOD, rádios e canais são limitadas.
- [ ] Testes em TV Box 1 GB, Google TV e Fire TV (perfil de memória já configurável; falta validar em hardware).

## Critérios de aceite
- Compila em CI.
- Testes unitários de política live/VOD e failover.
- Sem travamento quando catálogo ou integrações externas falham.
- Sem carregar catálogo completo na memória.
- Navegação testada com controle remoto real.
- Testes de reprodução só com streams de teste autorizados.

## Fase 5 — Pendências de integração e EPG
- [x] Implementar sincronização de timestamps em legendas externas SRT/VTT; falta suportar deslocamento de faixas embutidas e outros formatos.
- [ ] Completar resolvedores de páginas embed e transporte de cabeçalhos por fonte; há apenas cabeçalhos genéricos e regras iniciais para hosts conhecidos.
- [ ] Implementar serviço/sessão de mídia para controle de reprodução fora da Activity, se exigido pelo produto final.
- [ ] Publicar uma API de integração segura entre CineHUB TV e o player; atualmente a Activity de teste não é exportada.
- [ ] Configurar fontes EPG reais no CineHUB-ACERVO; `catalog/epg/sources.json` está vazio, então não há grade de programação para consumir.
- [ ] Validar com controle remoto e TVBox de 1 GB, Google TV e Fire TV físicos.
