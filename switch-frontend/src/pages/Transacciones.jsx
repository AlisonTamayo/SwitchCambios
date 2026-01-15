import { useState } from 'react';
import { Search, RefreshCw, Clock, CheckCircle, XCircle, AlertCircle, ArrowRight } from 'lucide-react';
import { nucleoApi } from '../api/client';

export default function Transacciones() {
    const [filters, setFilters] = useState({ id: '', bic: '', estado: '' });
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);

    const handleSearch = async (e) => {
        e.preventDefault();
        setLoading(true);
        setSearched(true);
        try {
            const params = {};
            if (filters.id) params.id = filters.id;
            if (filters.bic) params.bic = filters.bic;
            if (filters.estado) params.estado = filters.estado;

            // Use the new endpoint created in backend step 529
            const response = await nucleoApi.get('/transacciones/busqueda', { params });
            setResults(response.data);
        } catch (error) {
            console.error("Error buscando transacciones:", error);
            setResults([]);
        } finally {
            setLoading(false);
        }
    };

    const handleManualPoll = async (id) => {
        try {
            await nucleoApi.get(`/transacciones/${id}`); // Values RF-04 Active Polling internally
            alert("Polling manual ejecutado. Verifique si el estado cambió.");
            handleSearch({ preventDefault: () => { } }); // Refresh
        } catch (error) {
            alert("Error en polling: " + error.message);
        }
    };

    const getStatusBadge = (status) => {
        switch (status) {
            case 'COMPLETED': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800"><CheckCircle size={12} className="mr-1" /> Completada</span>;
            case 'FAILED': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800"><XCircle size={12} className="mr-1" /> Fallida</span>;
            case 'PENDING': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800"><Clock size={12} className="mr-1" /> Pendiente</span>;
            default: return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800"><AlertCircle size={12} className="mr-1" /> {status}</span>;
        }
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-gray-900">Monitor de Transacciones (Traceability)</h1>

            {/* Filters */}
            <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
                <form onSubmit={handleSearch} className="grid grid-cols-1 md:grid-cols-4 gap-4">
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">ID Instrucción</label>
                        <input
                            type="text"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                            placeholder="e.g. 550e8400..."
                            value={filters.id}
                            onChange={e => setFilters({ ...filters, id: e.target.value })}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">BIC (Origen/Destino)</label>
                        <input
                            type="text"
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                            placeholder="e.g. NEXUS_BANK"
                            value={filters.bic}
                            onChange={e => setFilters({ ...filters, bic: e.target.value })}
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-gray-700 mb-1">Estado</label>
                        <select
                            className="w-full px-3 py-2 border border-gray-300 rounded-lg focus:outline-none focus:ring-2 focus:ring-indigo-500"
                            value={filters.estado}
                            onChange={e => setFilters({ ...filters, estado: e.target.value })}
                        >
                            <option value="">Todos</option>
                            <option value="COMPLETED">COMPLETED</option>
                            <option value="FAILED">FAILED</option>
                            <option value="PENDING">PENDING</option>
                            <option value="TIMEOUT">TIMEOUT</option>
                        </select>
                    </div>
                    <div className="flex items-end">
                        <button
                            type="submit"
                            disabled={loading}
                            className="w-full bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 transition flex items-center justify-center gap-2"
                        >
                            {loading ? <RefreshCw className="animate-spin" size={18} /> : <Search size={18} />}
                            Buscar
                        </button>
                    </div>
                </form>
            </div>

            {/* Results Table */}
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-gray-600">
                        <thead className="bg-gray-50 text-gray-900 font-medium">
                            <tr>
                                <th className="px-6 py-4">Fecha</th>
                                <th className="px-6 py-4">ID Instrucción</th>
                                <th className="px-6 py-4">Origen &rarr; Destino</th>
                                <th className="px-6 py-4">Monto</th>
                                <th className="px-6 py-4">Estado</th>
                                <th className="px-6 py-4">Ref. Red</th>
                                <th className="px-6 py-4">Acciones</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {results.length === 0 && searched && (
                                <tr>
                                    <td colSpan="7" className="px-6 py-8 text-center text-gray-400">
                                        No se encontraron transacciones con los filtros aplicados.
                                    </td>
                                </tr>
                            )}
                            {results.map((tx) => (
                                <tr key={tx.idInstruccion} className="hover:bg-gray-50 transition">
                                    <td className="px-6 py-4 whitespace-nowrap">
                                        {new Date(tx.fechaCreacion).toLocaleString()}
                                    </td>
                                    <td className="px-6 py-4 font-mono text-xs">{tx.idInstruccion}</td>
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-2">
                                            <span className="font-semibold">{tx.codigoBicOrigen}</span>
                                            <ArrowRight size={14} className="text-gray-400" />
                                            <span className="font-semibold">{tx.codigoBicDestino}</span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4 font-medium text-gray-900">
                                        {tx.moneda} {tx.monto}
                                    </td>
                                    <td className="px-6 py-4">
                                        {getStatusBadge(tx.estado)}
                                    </td>
                                    <td className="px-6 py-4 font-mono text-xs text-gray-500 truncate max-w-[100px]" title={tx.referenciaRed}>
                                        {tx.referenciaRed}
                                    </td>
                                    <td className="px-6 py-4">
                                        {(tx.estado === 'PENDING' || tx.estado === 'TIMEOUT' || tx.estado === 'RECEIVED') && (
                                            <button
                                                onClick={() => handleManualPoll(tx.idInstruccion)}
                                                className="text-indigo-600 hover:text-indigo-900 text-xs font-semibold flex items-center gap-1 border border-indigo-200 px-2 py-1 rounded hover:bg-indigo-50"
                                            >
                                                <RefreshCw size={12} /> Polling
                                            </button>
                                        )}
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>
            </div>
        </div>
    );
}
