# Integrações externas

## OpenSubtitles
Implementar como adaptador isolado. Buscar por identificador público (IMDb/TMDb quando disponível), idioma e tipo de conteúdo. Aplicar tamanho, fundo e sincronização no renderer de legendas. Tratar limite de API, falhas de rede e mudanças nos termos do serviço sem bloquear reprodução.

## TheIntroDB
Consultar tempos de abertura/recapitulação por ID compatível. Cache local limitado e fallback para indisponibilidade ou ausência de correspondência. Mostrar botão de pular apenas quando o intervalo for conhecido.

## Segurança
Não embutir tokens em código, commits ou catálogo. Não contornar DRM, autenticação ou controles de acesso. Respeitar termos, licenças e atribuição das APIs e bibliotecas.
