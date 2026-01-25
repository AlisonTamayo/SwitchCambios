# Plan de Despliegue en Google Cloud Platform (GCP) - Arquitectura Multi-Repo

Este documento detalla paso a paso cómo montar la infraestructura en Google Cloud, configurar los 7 repositorios independientes y automatizar el despliegue mediante GitHub Actions.

## 1. Estructura de Repositorios (GitHub)
Debes crear los siguientes 7 repositorios en tu cuenta `AlisonTamayo`:

1.  **`switch-transaccional`** (Orquestador): Contiene `docker-compose-full.yml`, `kong.yml` y el Workflow de GitHub Actions.
2.  **`ms-nucleo`**: Código Java del Núcleo.
3.  **`ms-directorio`**: Código Java de Directorio.
4.  **`ms-contabilidad`**: Código Java de Contabilidad.
5.  **`ms-compensacion`**: Código Java de Compensación.
6.  **`ms-devolucion`**: Código Java de Devoluciones.
7.  **`switch-frontend`**: Código React del Frontend.

---

## 2. Creación de la Máquina Virtual en GCP

1.  Ve a **Google Cloud Console** > **Compute Engine** > **Instancias de VM**.
2.  **Crear Instancia:**
    *   **Nombre:** `switch-vm-prod`
    *   **Región:** `us-central1` (o la más cercana).
    *   **Tipo de Máquina:** **e2-standard-2** (2 vCPU, 8 GB de memoria). *Crítico: No usar micro/small.*
    *   **Disco de Arranque:** Debian 11 o Ubuntu 22.04 LTS (SSD, 20 GB mínimo).
    *   **Firewall:** Marcar "Permitir tráfico HTTP" y "Permitir tráfico HTTPS".
3.  **Configurar IP Estática:**
    *   En "Redes VPC" > "Direcciones IP", reserva una IP estática externa y asígnala a la VM.

---

## 3. Preparación del Servidor (Por SSH)

Conéctate a la VM (`Haga clic en SSH` en la consola de GCP) y ejecuta:

### A. Instalar Docker y Docker Compose
```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg lsb-release git

# Instalar Docker Engine
sudo mkdir -m 0755 -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin docker-compose

# Permisos de usuario (para no usar sudo en docker)
sudo usermod -aG docker $USER
# (Cierra sesión y vuelve a entrar para aplicar cambios)
```

### B. Preparar Carpetas y Clonar Repositorios
Usaremos la carpeta `/opt/switch` como base.

```bash
# Crear directorio y asignar permisos a tu usuario
sudo mkdir -p /opt/switch
sudo chown -R $USER:$USER /opt/switch
cd /opt/switch

# CLONAR LOS 7 REPOSITORIOS (Lado a Lado)
# Nota: Usa HTTPS para clonar inicialmente o configura SSH keys en la VM.
git clone https://github.com/AlisonTamayo/switch-transaccional.git
git clone https://github.com/AlisonTamayo/ms-nucleo.git
git clone https://github.com/AlisonTamayo/ms-directorio.git
git clone https://github.com/AlisonTamayo/ms-contabilidad.git
git clone https://github.com/AlisonTamayo/ms-compensacion.git
git clone https://github.com/AlisonTamayo/ms-devolucion.git
git clone https://github.com/AlisonTamayo/switch-frontend.git
```

El resultado debe ser:
```text
/opt/switch/
├── ms-compensacion/
├── ms-contabilidad/
├── ms-devolucion/
├── ms-directorio/
├── ms-nucleo/
├── switch-frontend/
└── switch-transaccional/  <-- Aquí está docker-compose-full.yml
```

---

## 4. Configurar GitHub Actions (Deploy Automático)

Para que GitHub pueda entrar a tu VM y actualizar el código:

1.  **Generar claves SSH en tu PC Local:**
    *   `ssh-keygen -t rsa -b 4096 -C "github-actions"`
    *   Tendrás `id_rsa` (privada) y `id_rsa.pub` (pública).
2.  **Instalar clave pública en la VM GCP:**
    *   Copia el contenido de `id_rsa.pub`.
    *   En la VM GCP: `nano ~/.ssh/authorized_keys` y pega el contenido al final.
3.  **Configurar Secretos en GitHub:**
    *   Ve al repositorio **`switch-transaccional`** > Settings > Secrets and variables > Actions.
    *   Crea estos secretos:
        *   `GCP_VM_HOST`: La IP Externa de tu VM (ej. `34.123.45.67`).
        *   `GCP_VM_USERNAME`: Tu usuario de linux en la VM (ej. `alison`).
        *   `GCP_VM_SSH_KEY`: Copia **todo** el contenido de tu clave privada `id_rsa`.

---

## 5. Primer Despliegue Manual
Antes de confiar en la automatización, levanta todo una vez manualmente en la VM:

```bash
cd /opt/switch
docker-compose -f switch-transaccional/docker-compose-full.yml up -d --build
```
Verifica que todo esté corriendo con `docker ps`.

---

## 6. ¡Listo! 🚀
A partir de ahora:
1.  Haces un cambio en (por ejemplo) `ms-nucleo` en tu PC.
2.  Haces Push a `ms-nucleo`.
3.  Vas al repo `switch-transaccional` y disparas el Workflow (o lo configuras para dispararse periodicamente, o manualmente).
4.  GitHub se conectará a tu VM, hará `git pull` en todas las carpetas y recargará los contenedores.
