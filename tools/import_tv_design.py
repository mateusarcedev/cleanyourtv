#!/usr/bin/env python3
"""Import the owner's exact Claude Design TV Launcher.html into Android assets.

No network is needed; CSS, JS, SVG and fonts are decoded locally from the
exported bundle. This source export remains with the owner, outside Git.
"""
import argparse
import base64
import gzip
import json
from pathlib import Path
import re

RECORDS = re.compile(r'<script type="__bundler/(manifest|template|ext_resources)">\\s*(.*?)\\s*</script>', re.S)
EXT = {'application/javascript':'js', 'text/javascript':'js', 'image/svg+xml':'svg',
       'font/woff':'woff', 'font/woff2':'woff2', 'text/css':'css'}

def inject(source):
    # Retain the original preview arrays for browser preview only.
    def change(before, after):
        nonlocal source
        if before not in source:
            raise ValueError('Export changed; missing: ' + before[:80])
        source = source.replace(before, after, 1)

    change('const APPS = [', 'const PREVIEW_APPS = [')
    change('const INPUTS = [', 'const PREVIEW_INPUTS = [')
    change('const SAVER_POS =', """const TV_NATIVE = typeof TvLauncherBridge !== 'undefined';
function tvData(method, fallback) {
  if (!TV_NATIVE) return fallback;
  try { const data = JSON.parse(TvLauncherBridge[method]());
    return Array.isArray(data) && data.length ? data : fallback;
  } catch (e) { return fallback; }
}
const APPS = tvData('getApps', PREVIEW_APPS);
const INPUTS = tvData('getInputs', PREVIEW_INPUTS);
function tvAction(action) { if (TV_NATIVE) TvLauncherBridge.openSettings(action); }
const SAVER_POS =""")
    change("launch(i) { this.toast('am start -n ' + APPS[i].pkg + '  → abrindo ' + APPS[i].name); }",
           "launch(i) { if (TV_NATIVE) TvLauncherBridge.launchApp(APPS[i].pkg); else this.toast('prévia · ' + APPS[i].name); }")
    change("pickInput(i) { this.setState({ zone: 'top' }); this.toast('entrada → ' + INPUTS[i].port + ' · ' + INPUTS[i].title); }",
           "pickInput(i) { this.setState({ zone: 'top' }); if (TV_NATIVE) TvLauncherBridge.openInput(INPUTS[i].id || ''); else this.toast('prévia · ' + INPUTS[i].port); }")
    change("if (i === 1) return this.toast('wi-fi · casa-5g · conectado, 5 GHz');",
           "if (i === 1) return TV_NATIVE ? tvAction('wifi') : this.toast('prévia · rede');")
    change("if (i === 2) return this.toast('reordenar: OK pega um app, setas movem, voltar solta');",
           "if (i === 2) return this.toast('Organização dos apps: em desenvolvimento.');")
    change("state = { ov: {},",
           "state = { ov: (() => { try { return TV_NATIVE ? JSON.parse(TvLauncherBridge.getOptions()) : {}; } catch(e) { return {}; } })(),")
    change("setOv(k, v) { this.setState(s => ({ ov: { ...s.ov, [k]: v } })); }",
           "setOv(k, v) { const next = {...this.state.ov, [k]: v}; if(TV_NATIVE) TvLauncherBridge.saveOptions(JSON.stringify(next)); this.setState({ov:next}); }")
    change("this.setState({ ov: {} }); this.toast('ajustes restaurados para o tema');",
           "this.setState({ ov: {} }); if(TV_NATIVE) TvLauncherBridge.saveOptions('{}'); this.toast('ajustes restaurados');")
    change("this.ro = new ResizeObserver(this.fit);\n    this.ro.observe(document.documentElement);",
           "if (typeof ResizeObserver !== 'undefined') { this.ro = new ResizeObserver(this.fit); this.ro.observe(document.documentElement); }")
    change("noIcon: !a.icon,", "noIcon: !a.icon && !a.iconData,")
    change("mask: a.icon ? `url(${(window.__resources && window.__resources['icon_' + a.icon]) || 'https://cdn.simpleicons.org/' + a.icon}) center / contain no-repeat` : '',",
           "mask: a.iconData ? `url(${a.iconData}) center / contain no-repeat` : a.icon ? `url(${(window.__resources && window.__resources['icon_' + a.icon]) || ''}) center / contain no-repeat` : '',")
    change("const pillLabels = ['Entradas', 'Wi-Fi · casa-5g', '⇅', 'Configurações'];",
           "const pillLabels = ['Entradas', TV_NATIVE ? TvLauncherBridge.getNetworkLabel() : 'Wi-Fi · prévia', '⇅', 'Configurações'];")
    change("action('Configurações da TV', 'android.settings.SETTINGS', () => this.toast('am start -a android.settings.SETTINGS'))",
           "action('Configurações da TV', 'Android TV', () => tvAction('system'))")
    change("action('Imagem e som', 'android.settings.DISPLAY_SETTINGS', () => this.toast('am start -a android.settings.DISPLAY_SETTINGS'))",
           "action('Imagem e som', 'Android TV', () => tvAction('display'))")
    change("action('Apps', 'android.settings.APPLICATION_SETTINGS', () => this.toast('am start -a android.settings.APPLICATION_SETTINGS'))",
           "action('Apps', 'Aplicativos instalados', () => tvAction('apps'))")
    change("action('Tela inicial padrão', 'Escolha uma tela inicial e pressione Home.', () => this.toast('am start -a android.settings.HOME_SETTINGS'))",
           "action('Tela inicial padrão', 'Escolha uma tela inicial', () => tvAction('home'))")
    change("bool('Depuração ADB', 'adb', false, 'Porta 5555 em texto puro. Desligue quando terminar.')",
           "info('Depuração ADB', 'Gerenciar nas opções do desenvolvedor')")
    change("info('DNS privado', 'dns.adguard-dns.com', 'Bloqueia domínios de anúncio em todos os apps.')",
           "info('DNS privado', 'Consultar nas configurações da TV')")
    change("info('Wi-Fi', 'casa-5g', 'Conectado · 5 GHz · sinal forte', 'var(--mint)')",
           "info('Rede', TV_NATIVE ? TvLauncherBridge.getNetworkLabel() : 'prévia', '', 'var(--mint)')")
    change("info('Endereço IP', '192.168.1.50')",
           "info('Endereço IP', TV_NATIVE ? TvLauncherBridge.getIpAddress() : 'prévia')")
    change("info('Launcher', 'v0.1.0', 'com.mateusarce.tvhome')",
           "info('Launcher', 'mateusarce.dev', 'Base CleanYourTV')")
    change("info('Cartões de módulo', 'nenhum instalado')",
           "info('Cartões de módulo', 'Gerenciar no launcher nativo')")
    change("const evTitles = [['19:00', 'Reunião de sprint'], ['14:30', 'Dentista'], ['16:00', 'Flamengo × Vasco'], ['09:00', 'Revisão do carro']];",
           "const evTitles = [['—', 'Agenda demonstrativa'], ['—', 'Conecte sua agenda'], ['—', 'Sem sincronização'], ['—', 'Dados de exemplo']];")
    change("label: fc.label, meta: fc.meta, metaColor:",
           "label: (TV_NATIVE ? '[prévia] ' : '') + fc.label, meta: fc.meta, metaColor:")
    change("league: 'Brasileirão'", "league: 'Exemplo · Brasileirão'")
    return source

def main():
    cli = argparse.ArgumentParser(description=__doc__)
    cli.add_argument('export', type=Path, help='Arquivo TV Launcher.html do Claude')
    cli.add_argument('--output', type=Path, default=Path('launcher/app/src/main/assets/design_launcher'))
    args = cli.parse_args()
    html = args.export.read_text(encoding='utf-8')
    data = dict(RECORDS.findall(html))
    if 'manifest' not in data or 'template' not in data:
        raise SystemExit('Não é o export HTML esperado (manifest/template ausente)')
    template = json.loads(data['template'])
    manifest = json.loads(data['manifest'])
    output = args.output
    output.mkdir(parents=True, exist_ok=True)
    names = {}
    for i, (key, asset) in enumerate(manifest.items()):
        if asset['mime'] not in EXT:
            raise ValueError('MIME desconhecido: ' + asset['mime'])
        name = 'asset_%02d.%s' % (i, EXT[asset['mime']])
        payload = base64.b64decode(asset['data'])
        if asset.get('compressed'):
            payload = gzip.decompress(payload)
        (output / name).write_bytes(payload)
        names[key] = name
        template = template.replace(key, name)
    resources = {r['id']: names[r['uuid']] for r in json.loads(data.get('ext_resources', '[]'))
                 if r['uuid'] in names}
    template = template.replace('<head>', '<head>\n<script>window.__resources=' +
                                json.dumps(resources) + ';</script>', 1)
    pattern = re.compile(r'(<script\\s+type="text/x-dc"[^>]*>)(.*?)(</script>)', re.S)
    template, count = pattern.subn(lambda m: m.group(1)+inject(m.group(2))+m.group(3),
                                   template, count=1)
    if count != 1:
        raise ValueError('Script declarativo do Claude não encontrado')
    template = template.replace('https://cdn.simpleicons.org/', 'about:blank#icon-')
    (output / 'index.html').write_text(template, encoding='utf-8')
    print('Importados', len(names) + 1, 'arquivos para', output)

if __name__ == '__main__':
    main()
