import { useState, useEffect } from 'react';
import { Search, RefreshCw, Clock, CheckCircle, XCircle, AlertCircle, ArrowRight, ChevronLeft, ChevronRight } from 'lucide-react';
import { nucleoApi } from '../api/client';

export default function Transacciones() {
    const [filters, setFilters] = useState({ id: '', bic: '', estado: '' });
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searched, setSearched] = useState(false);

    const ITEMS_PER_PAGE = 15;
    const [currentPage, setCurrentPage] = useState(1);

    const handleSearch = async (e) => {
        if (e) e.preventDefault();
        setLoading(true);
        setSearched(true);
        try {
            const params = {};
            if (filters.id) params.id = filters.id;
            if (filters.bic) params.bic = filters.bic;
            if (filters.estado) params.estado = filters.estado;


            const response = await nucleoApi.get('/busqueda', { params });
            const sortedData = response.data.sort((a, b) => new Date(b.fechaCreacion) - new Date(a.fechaCreacion));
            setResults(sortedData);
            setCurrentPage(1);
        } catch (error) {
            console.error("Error buscando transacciones:", error);
            setResults([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        handleSearch();
    }, []);

    const handleManualPoll = async (id) => {
        try {
            await nucleoApi.get(`/transacciones/${id}`);
            alert("Polling manual ejecutado. Verifique si el estado cambió.");
            handleSearch();
        } catch (error) {
            alert("Error en polling: " + error.message);
        }
    };

    const getStatusBadge = (status) => {
        switch (status) {
            case 'COMPLETED': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800"><CheckCircle size={12} className="mr-1" /> Completada</span>;
            case 'FAILED': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-red-100 text-red-800"><XCircle size={12} className="mr-1" /> Fallida</span>;
            case 'PENDING': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-yellow-100 text-yellow-800"><Clock size={12} className="mr-1" /> Pendiente</span>;
            case 'REVERSED': return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-purple-100 text-purple-800"><RefreshCw size={12} className="mr-1" /> Reversada</span>;
            default: return <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800"><AlertCircle size={12} className="mr-1" /> {status}</span>;
        }
    };

    const formatDate = (dateString) => {
        if (!dateString) return "-";
        const utcDate = dateString.endsWith('Z') ? dateString : dateString + 'Z';
        return new Date(utcDate).toLocaleString('es-EC', {
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        });
    };

    const indexOfLastItem = currentPage * ITEMS_PER_PAGE;
    const indexOfFirstItem = indexOfLastItem - ITEMS_PER_PAGE;
    const currentItems = results.slice(indexOfFirstItem, indexOfLastItem);
    const totalPages = Math.ceil(results.length / ITEMS_PER_PAGE);

    const paginate = (pageNumber) => setCurrentPage(pageNumber);

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-gray-900">Monitor de Transacciones (Traceability)</h1>


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
                            <option value="REVERSED">REVERSED</option>
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
                            {currentItems.map((tx) => (
                                <tr key={tx.idInstruccion} className="hover:bg-gray-50 transition">
                                    <td className="px-6 py-4 whitespace-nowrap text-gray-700 font-medium">
                                        {formatDate(tx.fechaCreacion)}
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

                {results.length > 0 && (
                    <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between">
                        <div className="text-sm text-gray-500">
                            Mostrando <span className="font-medium">{indexOfFirstItem + 1}</span> a <span className="font-medium">{Math.min(indexOfLastItem, results.length)}</span> de <span className="font-medium">{results.length}</span> resultados
                        </div>
                        <div className="flex items-center space-x-2">
                            <button
                                onClick={() => paginate(currentPage - 1)}
                                disabled={currentPage === 1}
                                className={`p-2 rounded-lg border ${currentPage === 1 ? 'text-gray-300 border-gray-200 cursor-not-allowed' : 'text-gray-600 border-gray-300 hover:bg-gray-50'}`}
                            >
                                <ChevronLeft size={16} />
                            </button>

                            <span className="px-4 py-2 text-sm font-medium text-gray-700">
                                Página {currentPage} de {totalPages}
                            </span>

                            <button
                                onClick={() => paginate(currentPage + 1)}
                                disabled={currentPage === totalPages}
                                className={`p-2 rounded-lg border ${currentPage === totalPages ? 'text-gray-300 border-gray-200 cursor-not-allowed' : 'text-gray-600 border-gray-300 hover:bg-gray-50'}`}
                            >
                                <ChevronRight size={16} />
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
