import { useState, useEffect } from 'react';
import { compensacionApi } from '../api/client';
import { ArrowDownCircle, CheckCircle, Clock, FileText, PlayCircle } from 'lucide-react';

export default function Compensacion() {
    const [ciclos, setCiclos] = useState([]);
    const [cicloActivo, setCicloActivo] = useState(null);
    const [posiciones, setPosiciones] = useState([]);
    const [loading, setLoading] = useState(true);
    const [uptime, setUptime] = useState('');
    const [duracionProximo, setDuracionProximo] = useState(10);

    useEffect(() => {
        loadData();
        const interval = setInterval(loadData, 10000);
        return () => clearInterval(interval);
    }, []);

    useEffect(() => {
        if (cicloActivo) {
            const timer = setInterval(() => {
                const start = new Date(cicloActivo.fechaApertura).getTime();
                const now = new Date().getTime();
                const diff = Math.floor((now - start) / 1000);
                const mm = Math.floor(diff / 60).toString().padStart(2, '0');
                const ss = (diff % 60).toString().padStart(2, '0');
                setUptime(`${mm}:${ss}`);
            }, 1000);
            return () => clearInterval(timer);
        }
    }, [cicloActivo]);

    const loadData = async () => {
        try {
            const res = await compensacionApi.get('/compensacion/ciclos');
            const lista = res.data;
            setCiclos(lista);

            const activo = lista.find(c => c.estado === 'ABIERTO');
            if (activo) {
                if (!cicloActivo || cicloActivo.id !== activo.id) {
                    setCicloActivo(activo);
                    loadPosiciones(activo.id);
                }
            } else {
                setCicloActivo(null);
            }
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

        const minInt = 10;

        if (window.confirm(`¿Seguro que deseas cerrar el Ciclo #${cicloActivo.numeroCiclo} ahora?`)) {
            try {
                await compensacionApi.post(`/compensacion/ciclos/${cicloActivo.id}/cierre?proximoCicloEnMinutos=${minInt}`);
                alert(`✅ Ciclo #${cicloActivo.numeroCiclo} CERRADO. Siguiente en ${minInt} min.`);
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

    return (
        <div className="space-y-6">
            <h1 className="text-2xl font-bold text-gray-900">Compensación y Liquidación (Clearing)</h1>

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
                            Apertura: {cicloActivo ? new Date(cicloActivo.fechaApertura).toLocaleTimeString() : '--:--'}
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

            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
                <div className="p-6 border-b border-gray-100">
                    <h3 className="text-lg font-bold text-gray-900">Historial de Cortes</h3>
                </div>
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
                        {ciclos.map((c) => (
                            <tr key={c.id}>
                                <td className="px-6 py-4 font-bold">#{c.numeroCiclo}</td>
                                <td className="px-6 py-4">{new Date(c.fechaApertura).toLocaleString()}</td>
                                <td className="px-6 py-4">
                                    {c.fechaCierre ? new Date(c.fechaCierre).toLocaleString() : '-'}
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
                                    {/* PDF BUTTON */}
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
        </div>
    );
}
