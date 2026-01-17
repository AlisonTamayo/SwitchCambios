# Frontend - Switch Transaccional (React + Vite) v3.0

Este repositorio contiene la interfaz de usuario para el monitoreo y gestión del Switch Transaccional Interbancario.

## 🚀 Arquitectura Frontend

*   **Framework:** React 18 + Vite (Alto rendimiento)
*   **Estilos:** TailwindCSS + Lucide Icons
*   **Conexión:** Consume la API Gateway (Kong) en el puerto `8000`.

## 🌐 Configuración de Despliegue (Nginx & CORS)

Para producción en AWS, utilizamos **Nginx** como Proxy Inverso. Esto resuelve dos problemas críticos:
1.  **Single Page Application (SPA):** Redirige todas las rutas desconocidas a `index.html` para que React Router funcione.
2.  **CORS (Cross-Origin Resource Sharing):** Al usar `/api` como proxy, el navegador ve que las peticiones van al "mismo origen" (mismo dominio), eliminando bloqueos de seguridad.

### `nginx.conf` (Producción)

```nginx
server {
    listen 80;
    
    # 1. Servir los archivos estáticos de React
    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        # Fix para React Router: Si no encuentra el archivo, devuelve index.html
        try_files $uri $uri/ /index.html;
    }

    # 2. Proxy Inverso hacia Kong (API Gateway)
    # Ejemplo: Petición a /api/transacciones -> Se envía a http://kong:8000/transacciones
    location /api/ {
        # Reescribimos la URL para quitar '/api' si Kong no lo espera, 
        # o lo dejamos si configuramos Kong con '/api' en upstream.
        # En nuestra arquitectura actual: Kong espera recibir '/transacciones'
        # Así que reescribimos:
        rewrite ^/api/(.*) /$1 break;
        
        proxy_pass http://kong-gateway:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 🛠️ Comandos Locales

```bash
# Instalar dependencias
npm install

# Correr en modo desarrollo
npm run dev

# Construir para producción
npm run build
```
