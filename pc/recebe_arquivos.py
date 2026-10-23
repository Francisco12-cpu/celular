"""
QUADRO -> PC
Recebe arquivos enviados pelo celular ("Compartilhar -> Enviar pro PC").
Uso:  python recebe_arquivos.py SUA_SENHA
Os arquivos vão para a pasta Recebidos, dentro da sua pasta de usuário.
Só Python, sem instalar nada.
"""
import os
import socket
import sys
import urllib.parse
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

SENHA = sys.argv[1] if len(sys.argv) > 1 else "quadro"
PORTA = 5757
PASTA = os.path.join(os.path.expanduser("~"), "Recebidos")
os.makedirs(PASTA, exist_ok=True)


def meu_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # não envia nada, só descobre a interface
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


class Recebedor(BaseHTTPRequestHandler):
    def do_GET(self):
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"QUADRO ok")

    def do_POST(self):
        if self.headers.get("X-Senha") != SENHA:
            self.send_response(403)
            self.end_headers()
            print("! tentativa com senha errada de", self.client_address[0])
            return
        nome = os.path.basename(urllib.parse.unquote_plus(self.headers.get("X-Nome", "arquivo"))) or "arquivo"
        tamanho = int(self.headers.get("Content-Length", 0))
        destino = os.path.join(PASTA, nome)
        base, ext = os.path.splitext(destino)
        n = 1
        while os.path.exists(destino):
            destino = f"{base} ({n}){ext}"
            n += 1
        with open(destino, "wb") as f:
            falta = tamanho
            while falta > 0:
                bloco = self.rfile.read(min(65536, falta))
                if not bloco:
                    break
                f.write(bloco)
                falta -= len(bloco)
        print(f"✓ recebido: {os.path.basename(destino)} ({tamanho // 1024} KB)")
        self.send_response(200)
        self.end_headers()

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    print("QUADRO -> PC pronto")
    print(f"  IP pra colocar no celular: {meu_ip()}")
    print(f"  Senha: {SENHA}")
    print(f"  Pasta: {PASTA}")
    print("  (Ctrl+C pra fechar. Se o Windows perguntar do firewall, permita em rede privada.)")
    ThreadingHTTPServer(("0.0.0.0", PORTA), Recebedor).serve_forever()
