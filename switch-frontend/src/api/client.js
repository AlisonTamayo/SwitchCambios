import axios from 'axios';

// En Producción (Docker/AWS), usamos Nginx como Proxy, así que la URL es relativa '/api'.
// En desarrollo local (npm run dev), Vite puede necesitar proxy o apuntar directo a Kong.
const API_URL = import.meta.env.VITE_API_URL || '/api';

// Todas las peticiones pasan por el Proxy Nginx -> Kong -> Microservicios
export const nucleoApi = axios.create({ baseURL: `${API_URL}/transacciones` });
export const directorioApi = axios.create({ baseURL: `${API_URL}/directorio` });
export const contabilidadApi = axios.create({ baseURL: `${API_URL}/contabilidad` });
export const compensacionApi = axios.create({ baseURL: `${API_URL}/compensacion` });


[nucleoApi, directorioApi, contabilidadApi, compensacionApi].forEach(api => {
    api.interceptors.response.use(
        response => response,
        error => {
            console.error('API Error:', error.response?.data || error.message);
            return Promise.reject(error);
        }
    );
});
