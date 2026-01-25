import axios from 'axios';

const API_URL = '/api';


const headers = { 'apikey': 'SWITCH_ADMIN_SUPER_SECRET_KEY' };

export const nucleoApi = axios.create({ baseURL: `${API_URL}/transacciones`, headers });
export const directorioApi = axios.create({ baseURL: `${API_URL}/directorio`, headers });
export const contabilidadApi = axios.create({ baseURL: `${API_URL}/contabilidad`, headers });
export const compensacionApi = axios.create({ baseURL: `${API_URL}/compensacion`, headers });


[nucleoApi, directorioApi, contabilidadApi, compensacionApi].forEach(api => {
    api.interceptors.response.use(
        response => response,
        error => {
            console.error('API Error:', error.response?.data || error.message);
            return Promise.reject(error);
        }
    );
});
