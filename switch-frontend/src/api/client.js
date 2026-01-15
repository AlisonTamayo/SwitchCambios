import axios from 'axios';

const API_URL = 'http://localhost';

export const nucleoApi = axios.create({ baseURL: `${API_URL}:8082/api/v1` });
export const directorioApi = axios.create({ baseURL: `${API_URL}:8081/api/v1` });
export const contabilidadApi = axios.create({ baseURL: `${API_URL}:8083/api/v1` });
export const compensacionApi = axios.create({ baseURL: `${API_URL}:8084/api/v1` });


[nucleoApi, directorioApi, contabilidadApi, compensacionApi].forEach(api => {
    api.interceptors.response.use(
        response => response,
        error => {
            console.error('API Error:', error.response?.data || error.message);
            return Promise.reject(error);
        }
    );
});
