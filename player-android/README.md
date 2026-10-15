# Player Android

Aplicativo Android nativo direcionado a Android TV, Google TV, Fire TV e TV Boxes.

## Base técnica planejada
- Kotlin + AndroidX Media3 ExoPlayer.
- Activity/Compose ou Views com layout 10-foot; escolha final após comparar com a estrutura real do SaimoTV-Android.
- MediaSession para integração com teclas de mídia.
- Persistência local leve para progresso e preferências.
- Perfis de dispositivo por memória disponível e capacidade de decoder.

## Controles
- D-pad: navegar por controles, episódios, faixas e legendas; foco visível em vermelho CineHUB.
- LIVE: PLAY/PAUSE não pausa a transmissão.
- VOD: play/pause, seek, velocidade, áudio e legendas.
- Voltar fecha painel secundário antes de sair do player.

Este diretório é uma especificação inicial. Não declarar build/APK funcional até existir Gradle completo e a compilação passar em CI.
