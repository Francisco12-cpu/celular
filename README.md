# QUADRO

Launcher Android leve, quadrado e personalizável, inspirado no Niagara.

- Barra de letras com efeito de onda e quadrado mostrando a letra
- Papel de parede real do celular por trás
- Ícones quadrados com 7 filtros (normal, preto, preto + cor, branco, branco + cor, cinza, cor total), com cor do papel de parede ou escolhida, e ajuste por app
- 13 relógios com fonte, tamanho, peso e cor
- Amiguinho que reage a bateria, horário e notificações, com reações por contato
- Data, semana, treino do dia, favoritos, apps ocultos, busca com calculadora
- Modo edição: mover, alinhar, aumentar e esconder cada bloco
- "Enviar pro PC" no menu Compartilhar de qualquer app

## Como instalar

1. Crie um repositório no GitHub chamado `quadro-launcher`.
2. Envie todos os arquivos desta pasta, incluindo a pasta `.github`.
3. Abra a aba **Actions**. O processo "Gerar APK" roda sozinho e leva uns 5 minutos.
4. Quando ficar verde, abra a aba **Releases** pelo navegador do celular e baixe o `QUADRO-vN.apk`.
5. Instale. O Android vai pedir pra liberar a instalação de apps pelo navegador.
6. Aperte o botão home e escolha **QUADRO → Sempre**.
7. Libere as notificações: ⚙ (embaixo da barra de letras) → Amiguinho → Abrir permissão.

Cada mudança enviada ao GitHub gera um APK novo. Ele instala por cima do anterior e mantém suas configurações.

## Enviar arquivos pro PC

```
python pc/recebe_arquivos.py minha_senha
```

O script mostra o IP do PC. Coloque esse IP e a senha em ⚙ → PC. Depois é só usar, em qualquer app, Compartilhar → **Enviar pro PC**.

## Usando no dia a dia

| Ação | Como |
|---|---|
| Ver apps por letra | Deslize o dedo na barra de letras |
| Buscar ou fazer conta | ◎ no topo da barra |
| Ajustes | ⚙ no fim da barra |
| Editar a tela | Segure num espaço vazio |
| Favoritar, ocultar, ícone do app | Segure o app |
| Ver apps ocultos | Busque `oculto` |
