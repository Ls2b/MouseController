import socket
import pyautogui
import threading
import os
import sys
import winreg
from PIL import Image, ImageDraw
import pystray

pyautogui.PAUSE = 0
pyautogui.FAILSAFE = False

UDP_IP = "0.0.0.0"
UDP_PORT = 5005

# Имя программы, которое будет отображаться в Диспетчере задач
APP_NAME = "MouseControllerServer"
# Путь к текущему запускаемому файлу
REG_PATH = r"Software\Microsoft\Windows\CurrentVersion\Run"

# Флаг для остановки сервера при выходе
server_running = True
sock = None

def run_udp_server():
    global server_running, sock
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.bind((UDP_IP, UDP_PORT))
    
    while server_running:
        try:
            # Устанавливаем таймаут, чтобы поток мог проверять флаг server_running
            sock.settimeout(1.0)
            data, addr = sock.recvfrom(1024)
            message = data.decode('utf-8')
            
            try:
                # Движение мыши
                dx, dy = map(int, message.split(','))
                pyautogui.moveRel(dx, dy)
            except ValueError:
                # Клики
                if message == "click":
                    pyautogui.click()
                elif message == "r_click":
                    pyautogui.rightClick()
        except socket.timeout:
            continue
        except Exception:
            break

# Функции для работы с автозагрузкой Windows
def is_autorun_enabled():
    try:
        key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, REG_PATH, 0, winreg.KEY_READ)
        winreg.QueryValueEx(key, APP_NAME)
        winreg.CloseKey(key)
        return True
    except FileNotFoundError:
        return False

def toggle_autorun(icon, item):
    state = not item.checked
    key = winreg.OpenKey(winreg.HKEY_CURRENT_USER, REG_PATH, 0, winreg.KEY_SET_VALUE)
    if state:
        # Если запускаем как .exe (после компиляции), берем sys.executable
        # Если как .py, берем полный путь к скрипту
        script_path = os.path.abspath(sys.argv[0])
        if script_path.endswith('.py'):
            cmd = f'"{sys.executable}" "{script_path}"'
        else:
            cmd = f'"{script_path}"'
        winreg.SetValueEx(key, APP_NAME, 0, winreg.REG_SZ, cmd)
    else:
        try:
            winreg.DeleteValue(key, APP_NAME)
        except FileNotFoundError:
            pass
    winreg.CloseKey(key)

def exit_action(icon, item):
    global server_running, sock
    server_running = False
    if sock:
        sock.close()
    icon.stop()

# Создаем простую квадратную иконку на лету (зеленый квадрат с точкой)
def create_image():
    width = 64
    height = 64
    image = Image.new('RGB', (width, height), color='#2D2D2D')
    dc = ImageDraw.Draw(image)
    dc.ellipse([(16, 16), (48, 48)], fill='#4CAF50')
    return image

def setup_tray():
    icon = pystray.Icon(APP_NAME, create_image(), title="Mouse Controller Server")
    
    # Создаем меню в трее
    icon.menu = pystray.Menu(
        pystray.MenuItem(
            "Запускать при старте Windows", 
            toggle_autorun, 
            checked=lambda item: is_autorun_enabled()
        ),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Выход", exit_action)
    )
    
    # Запускаем сервер мыши в фоновом потоке
    server_thread = threading.Thread(target=run_udp_server, daemon=True)
    server_thread.start()
    
    # Запускаем иконку в трее (это заблокирует основной поток)
    icon.run()

if __name__ == "__main__":
    setup_tray()