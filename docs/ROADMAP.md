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
- [ ] Confirmar reprodução de HLS, DASH e MP4 em aparelhos reais (a compilação/empacotamento já passou em CI).
- [ ] Validar D-pad, voltar e restauração correta de foco em controle físico.

## Fase 2 — Resiliência
- [x] Timeout configurável de início (padrão 15 s; configurável de 10 a 45 s pela tela de teste), troca por erro e watchdog de buffering. Falta validar falhas reais em streams e dispositivos.
- [x] Buffer por perfil automático/economia/desempenho, com valores reduzidos para aparelhos de pouca memória.
- [x] Retomada persistente por item/episódio via armazenamento local.
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
- [x] Ajuste visual básico de legenda (tamanho/fundo).
- [ ] Sincronização fina do atraso de legendas (exige camada de temporização para legendas externas; não marcar como concluído só com estilo visual).

## Fase 4 — Pós-reprodução e catálogo
- [ ] Cartão visual do próximo episódio e recomendações reais por metadados (o aviso/autoplay de próximo episódio já existe, mas ainda não é o cartão visual).
- [x] Confirmação “Ainda está assistindo?” após duas horas sem interação em VOD; pausa o vídeo e permite continuar ou manter pausado.
- [ ] Cliente CineHUB-ACERVO com cache persistente e atualização incremental. Já existem prévias limitadas de VOD, rádios e canais com URLs diretas; o cache ainda é majoritariamente em memória/sem persistência para os catálogos.
- [ ] Testes em TV Box 1 GB, Google TV e Fire TV (perfil de memória já configurável; falta validar em hardware).

## Critérios de aceite
- Compila em CI.
- Testes unitários de política live/VOD e failover.
- Sem travamento quando catálogo ou integrações externas falham.
- Sem carregar catálogo completo na memória.
- Navegação testada com controle remoto real.
- Testes de reprodução só com streams de teste autorizados.
