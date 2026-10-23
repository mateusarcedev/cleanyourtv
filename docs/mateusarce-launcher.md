# Launcher mateusarce.dev para Android TV / Google TV

Esta implementação incorpora o **mesmo HTML, CSS, fontes, ícones e animações**
exportados do protótipo `TV Launcher.html`, em um WebView **offline** via `WebViewAssetLoader` (origem local HTTPS),
e usa uma ponte Java restrita para funções do Android.

Design system: `mateusarce.dev` (tema escuro, menta `#15F5BA`,
tipografia Geist e Geist Mono). O projeto base continua sendo CleanYourTV.
Não é um site remoto exibido na TV: os assets são empacotados no APK.

## Importar o seu design

O export HTML e as fontes pertencem ao proprietário e não são versionados no
fork. O HTML da tela, inclusive sua fonte de dados de demonstração, é
convertido localmente, sem download e sem API.

No diretório raiz do repositório:

```bash
python tools/import_tv_design.py "/caminho/TV Launcher.html"
```

Saída: `launcher/app/src/main/assets/design_launcher/index.html` +
arquivos JS/SVG/WOFF extraídos. Importar novamente quando alterar o artefato.
O arquivo `mateusarce.dev.zip` é a referência dos tokens e componentes,
mas a exportação da tela já contém o CSS compilado do design system.

Para gerar APK:

```bash
cd launcher
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -a android.settings.HOME_SETTINGS
```

No Windows: use `gradlew.bat :app:assembleDebug`. Conecte o ADB via rede
conforme `README.md` do projeto; não desabilite pacotes da TV sem revisar o
backup, plano de restauração e as listas específicas do modelo.

Se os assets não forem importados, `DesignLauncherActivity` abre o launcher
nativo original, sem tela preta. É possível continuar abrindo o original
pelo seu atalho Leanback (quando disponível).

## Funções reais no Android

- A home usa **o layout original de 1920×1080** escalado para a resolução
  atual: cartões, animações, destaques, gradientes, espaço e tokens visuais.
- **Aplicativos instalados:** são descobertos no Android, com seus nomes,
  ícones e launch intents, em vez da lista fixa do mockup.
- **D-pad / OK / Voltar:** eventos físicos do controle são traduzidos para
  a navegação já implementada no design.
- **HDMI / entradas:** usa TvInputManager quando o firmware expõe os dados.
  Caso não exponha, tenta abrir as configurações de entradas do fabricante.
- **Rede, IP, opções e sistema:** status básico real; atalhos de
  configurações abrem Activities do sistema. As escolhas de tema, colunas
  e efeitos são armazenadas em SharedPreferences.
- **Checklist:** persiste em localStorage do próprio WebView.

## Ainda é protótipo (não sincronizado)

O placar, o feed de PRs/e-mail/CI e os eventos do calendário vêm com
registros *ilustrativos* herdados do HTML, não dados reais. O importador
marca o feed como `[prévia]` e não declara ter feito sincronização.
Reordenar apps, provedores de placar e integração com contas requerem
implementações específicas antes de serem apresentados como recursos prontos.
Ativar `Estatísticas sob o relógio` ainda exibe números de exemplo do design.

## Compatibilidade e segurança

- Android TV / Google TV, API 26+ (conforme app original).
- A fidelidade depende da versão do Android System WebView. Firmware muito
  antigo pode não suportar recursos modernos do HTML exportado.
- O WebView bloqueia navegação externa e downloads HTTP; carrega arquivos somente da origem `appassets.androidplatform.net`; a ponte Java é
  exposta somente à página local empacotada.
- As permissões/ADB do CleanYourTV original continuam inalteradas.
- Testar numa TV real antes de definir como home principal.
- Samsung Tizen e LG webOS não aceitam este APK. Fire TV também tem
  restrições para substituir a home.

## Testes de aceitação na TV

1. Instalar o APK e configurar como home sem remover a launcher anterior.
2. Confirmar contraste, fontes, margens, foco menta e escala em 1080p/4K.
3. Navegar somente com setas/OK/Voltar; abrir 3 apps e voltar ao Home.
4. Instalar/remover um app e verificar a atualização ao reabrir a home.
5. Verificar entradas HDMI e opção Configurações, que variam por marca.
6. Alterar tema e opções, reiniciar a home e verificar persistência.
7. Desconectar rede e garantir que a interface continua funcionando offline.
8. Testar fallback removendo temporariamente a importação dos assets.

O mecanismo de *debloat*, backup e restauração é separado desta interface.
Não execute desativações automáticas só para instalar um launcher.
