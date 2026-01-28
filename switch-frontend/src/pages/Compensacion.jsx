import { useState, useEffect } from 'react';
import { compensacionApi } from '../api/client';
import { ArrowDownCircle, CheckCircle, Clock, FileText, PlayCircle, ChevronLeft, ChevronRight } from 'lucide-react';

const ITEMS_PER_PAGE = 10;

export default function Compensacion() {
    const [ciclos, setCiclos] = useState([]);
    const [cicloActivo, setCicloActivo] = useState(null);
    const [posiciones, setPosiciones] = useState([]);
    const [loading, setLoading] = useState(true);
    const [uptime, setUptime] = useState('');
    const [currentPage, setCurrentPage] = useState(1);

    const parseUTC = (dateString) => {
        if (!dateString) return null;
        // La fecha viene del backend (Java LocalDateTime) usualmente sin Z.
        // Asumimos SIEMPRE que es UTC.
        if (dateString.endsWith('Z') || dateString.includes('+00:00') || dateString.includes('-00:00')) {
            return new Date(dateString);
        }
        // Agregamos Z para forzar interpretación UTC
        return new Date(dateString + 'Z');
    };

    useEffect(() => {
        loadData();
        const interval = setInterval(loadData, 10000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        if (cicloActivo) {
            const timer = setInterval(() => {
                const fecha = parseUTC(cicloActivo.fechaApertura);
                if (!fecha) return;

                const start = fecha.getTime();
                const now = new Date().getTime();
                const diff = Math.floor((now - start) / 1000);

                if (diff < 0) {
                    setUptime("Sincronizando...");
                    return;
                }

                const hh = Math.floor(diff / 3600).toString().padStart(2, '0');
                const mm = Math.floor((diff % 3600) / 60).toString().padStart(2, '0');
                const ss = (diff % 60).toString().padStart(2, '0');
                setUptime(`${hh}:${mm}:${ss}`);
            }, 1000);
            return () => clearInterval(timer);
        }
    }, [cicloActivo]);

    const loadData = async () => {
        try {
            const res = await compensacionApi.get('/compensacion/ciclos');
            let lista = res.data;

            // 1. Encontrar activo para el Panel Superior
            const activo = lista.find(c => c.estado === 'ABIERTO');
            if (activo) {
                if (!cicloActivo || cicloActivo.id !== activo.id) {
                    setCicloActivo(activo);
                    loadPosiciones(activo.id);
                }
            } else {
                setCicloActivo(null);
            }

            // 2. Ordenar Lista para la Tabla (Abierto Primero, luego Fechas Descendentes)
            lista.sort((a, b) => {
                if (a.estado === 'ABIERTO') return -1; // a va primero
                if (b.estado === 'ABIERTO') return 1;  // b va primero

                // Comparar fechas UTC
                const dateA = parseUTC(a.fechaApertura);
                const dateB = parseUTC(b.fechaApertura);
                return dateB - dateA; // Descendente (más nuevo a más viejo)
            });

            setCiclos(lista);

        } catch (error) {
            console.error("Error cargando ciclos:", error);
        } finally {
            setLoading(false);
        }
    };

    const loadPosiciones = async (cicloId) => {
        try {
            const res = await compensacionApi.get(`/compensacion/ciclos/${cicloId}/posiciones`);
            setPosiciones(res.data);
        } catch (error) {
            console.error("Error cargando posiciones:", error);
        }
    };

    const handleCierre = async () => {
        if (!cicloActivo) return;
        const minInt = 90;
        if (window.confirm(`¿Seguro que deseas cerrar el Ciclo #${cicloActivo.numeroCiclo} ahora?`)) {
            try {
                await compensacionApi.post(`/compensacion/ciclos/${cicloActivo.id}/cierre?proximoCicloEnMinutos=${minInt}`);
                alert(`✅ Ciclo #${cicloActivo.numeroCiclo} CERRADO.`);
                loadData();
            } catch (error) {
                const msg = error.response?.data || error.message;
                alert("Error al cerrar ciclo: " + msg);
            }
        }
    };

    const descargarPDF = (cicloId) => {
        window.open(`/api/compensacion/compensacion/reporte/pdf/${cicloId}`, '_blank');
    };

    const formatDate = (dateStr) => {
        const d = parseUTC(dateStr);
        // toLocaleString usará la zona horaria del navegador (Ecuador UTC-5)
        return d ? d.toLocaleString() : '-';
    };

    const formatTime = (dateStr) => {
        const d = parseUTC(dateStr);
        return d ? d.toLocaleTimeString() : '--:--';
    };

    // Pagination Logic
    const totalPages = Math.ceil(ciclos.length / ITEMS_PER_PAGE);
    const paginatedCiclos = ciclos.slice(
        (currentPage - 1) * ITEMS_PER_PAGE,
        currentPage * ITEMS_PER_PAGE
    );

    const goToPage = (p) => {
        if (p >= 1 && p <= totalPages) setCurrentPage(p);
    };

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-gray-900">Compensación y Liquidación (Clearing)</h1>

            {/* PANEL CICLO ACTIVO */}
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
                <div className="p-6 border-b border-gray-100 flex justify-between items-center bg-indigo-50">
                    <div>
                        <h2 className="text-lg font-bold text-indigo-900 flex items-center gap-2">
                            {cicloActivo ? (
                                <><PlayCircle className="text-green-500 animate-pulse" /> Ciclo Activo #{cicloActivo.numeroCiclo}</>
                            ) : (
                                <><Clock className="text-gray-400" /> Sin Ciclo Activo</>
                            )}
                        </h2>
                        <span className="text-xs font-mono text-indigo-600 block mt-1">
                            {cicloActivo ? `⏱️ Tiempo Abierto: ${uptime}` : 'Esperando inicio...'}
                        </span>
                        <span className="text-xs text-gray-500">
                            Apertura: {cicloActivo ? formatTime(cicloActivo.fechaApertura) : '--:--'}
                        </span>
                    </div>

                    {cicloActivo && (
                        <div className="flex gap-2">
                            <button
                                onClick={() => loadPosiciones(cicloActivo.id)}
                                className="bg-white text-indigo-600 px-3 py-2 rounded-lg border border-indigo-200 hover:bg-indigo-50 text-sm font-semibold"
                            >
                                🔄 Refrescar
                            </button>
                            <button
                                onClick={handleCierre}
                                className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 shadow-sm text-sm font-semibold flex items-center gap-2"
                            >
                                <CheckCircle size={16} /> CERRAR CICLO
                            </button>
                        </div>
                    )}
                </div>

                <div className="p-6">
                    <h3 className="text-sm font-semibold text-gray-500 mb-4 uppercase tracking-wider">Posiciones Netas Multilaterales</h3>
                    <div className="overflow-x-auto">
                        <table className="w-full text-left text-sm text-gray-600">
                            <thead className="bg-gray-50 font-medium text-gray-900">
                                <tr>
                                    <th className="px-4 py-3">Banco</th>
                                    <th className="px-4 py-3 text-right">Créditos (Recibido)</th>
                                    <th className="px-4 py-3 text-right">Débitos (Enviado)</th>
                                    <th className="px-4 py-3 text-right">NETO</th>
                                </tr>
                            </thead>
                            <tbody className="divide-y divide-gray-100">
                                {posiciones.length === 0 ? (
                                    <tr><td colSpan="4" className="px-4 py-8 text-center text-gray-400">Sin movimientos en este ciclo</td></tr>
                                ) : posiciones.map(p => (
                                    <tr key={p.codigoBic}>
                                        <td className="px-4 py-3 font-medium text-gray-900">{p.codigoBic}</td>
                                        <td className="px-4 py-3 text-right text-green-600 font-mono">
                                            $ {p.totalCreditos.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </td>
                                        <td className="px-4 py-3 text-right text-red-600 font-mono">
                                            $ {p.totalDebitos.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </td>
                                        <td className={`px-4 py-3 text-right font-bold text-base ${p.neto >= 0 ? 'text-blue-600' : 'text-orange-600'
                                            }`}>
                                            $ {p.neto.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </td>
                                    </tr>
                                ))}
                            </tbody>
                        </table>
                    </div>
                </div>
            </div>

            {/* HISTORIAL: TABLA CON PAGINACIÓN */}
            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden flex flex-col">
                <div className="p-6 border-b border-gray-100">
                    <h3 className="text-lg font-bold text-gray-900">Historial de Cortes</h3>
                </div>

                <div className="overflow-x-auto">
                    <table className="w-full text-left text-sm text-gray-600">
                        <thead className="bg-gray-50 font-medium text-gray-900">
                            <tr>
                                <th className="px-6 py-4">Ciclo</th>
                                <th className="px-6 py-4">Apertura</th>
                                <th className="px-6 py-4">Cierre</th>
                                <th className="px-6 py-4">Estado</th>
                                <th className="px-6 py-4 text-right">Reportes</th>
                            </tr>
                        </thead>
                        <tbody className="divide-y divide-gray-100">
                            {paginatedCiclos.map((c) => (
                                <tr key={c.id} className={c.estado === 'ABIERTO' ? 'bg-indigo-50/50' : ''}>
                                    <td className="px-6 py-4 font-bold flex items-center gap-2">
                                        #{c.numeroCiclo}
                                        {c.estado === 'ABIERTO' && <span className="w-2 h-2 rounded-full bg-green-500 animate-pulse"></span>}
                                    </td>
                                    <td className="px-6 py-4">{formatDate(c.fechaApertura)}</td>
                                    <td className="px-6 py-4">
                                        {c.fechaCierre ? formatDate(c.fechaCierre) : '-'}
                                    </td>
                                    <td className="px-6 py-4">
                                        {c.estado === 'ABIERTO' ? (
                                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-green-100 text-green-800">
                                                <Clock size={12} className="mr-1" /> Abierto
                                            </span>
                                        ) : (
                                            <span className="inline-flex items-center px-2 py-1 rounded-full text-xs font-medium bg-gray-100 text-gray-800">
                                                <CheckCircle size={12} className="mr-1" /> Cerrado
                                            </span>
                                        )}
                                    </td>
                                    <td className="px-6 py-4 text-right flex justify-end gap-2">
                                        <button
                                            onClick={() => descargarPDF(c.id)}
                                            className="text-red-600 hover:text-red-800 flex items-center gap-1 bg-red-50 px-2 py-1 rounded"
                                            title="Descargar PDF"
                                        >
                                            <FileText size={16} /> PDF
                                        </button>
                                    </td>
                                </tr>
                            ))}
                        </tbody>
                    </table>
                </div>

                {/* CONTROLES DE PAGINACIÓN */}
                {totalPages > 1 && (
                    <div className="p-4 border-t border-gray-100 flex justify-between items-center bg-gray-50">
                        <span className="text-sm text-gray-500">
                            Página <b>{currentPage}</b> de <b>{totalPages}</b>
                        </span>
                        <div className="flex gap-2">
                            <button
                                onClick={() => goToPage(currentPage - 1)}
                                disabled={currentPage === 1}
                                className="px-3 py-1 rounded border bg-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100 text-gray-700 flex items-center"
                            >
                                <ChevronLeft size={16} /> Anterior
                            </button>
                            <button
                                onClick={() => goToPage(currentPage + 1)}
                                disabled={currentPage === totalPages}
                                className="px-3 py-1 rounded border bg-white disabled:opacity-50 disabled:cursor-not-allowed hover:bg-gray-100 text-gray-700 flex items-center"
                            >
                                Siguiente <ChevronRight size={16} />
                            </button>
                        </div>
                    </div>
                )}
            </div>
        </div>
    );
}
