import { useState, useEffect } from 'react';
import { Plus, Power, Key, Link as LinkIcon, Save, X, Server } from 'lucide-react';
import { directorioApi, contabilidadApi } from '../api/client';

export default function Bancos() {
    const [bancos, setBancos] = useState([]);
    const [loading, setLoading] = useState(true);
    const [showModal, setShowModal] = useState(false);
    const [formData, setFormData] = useState({
        codigoBic: '',
        nombre: '',
        urlDestino: '',
        llavePublica: '',
        estadoOperativo: 'ONLINE'
    });

    useEffect(() => {
        loadBancos();
    }, []);

    const loadBancos = async () => {
        try {
            const response = await directorioApi.get('/instituciones');
            setBancos(response.data);
        } catch (error) {
            console.error("Error cargando bancos:", error);
        } finally {
            setLoading(false);
        }
    };

    const handleStatusChange = async (bic, newStatus) => {
        if (!window.confirm(`¿Cambiar estado de ${bic} a ${newStatus}?`)) return;

        try {
            await directorioApi.patch(`/instituciones/${bic}/operaciones?nuevoEstado=${newStatus}`);
            loadBancos();
        } catch (error) {
            alert("Error cambiando estado: " + error.message);
        }
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        try {
            // Construct payload: FLAT structure matching new Java Model
            const payload = {
                codigoBic: formData.codigoBic,
                nombre: formData.nombre,
                urlDestino: formData.urlDestino,
                llavePublica: formData.llavePublica,
                estadoOperativo: formData.estadoOperativo
            };

            // 1. Register in Directory (MongoDB)
            // Using POST to register
            await directorioApi.post('/instituciones', payload);

            // 2. Create Technical Account in Ledger (Postgres)
            try {
                await contabilidadApi.post('/ledger/cuentas', { codigoBic: formData.codigoBic });
            } catch (ledgerError) {
                console.warn("Ledger account creation failed (maybe exists?):", ledgerError);
            }

            setShowModal(false);
            loadBancos();
            setFormData({
                codigoBic: '',
                nombre: '',
                urlDestino: '',
                llavePublica: '',
                estadoOperativo: 'ONLINE'
            });
            alert("Banco registrado exitosamente");
        } catch (error) {
            console.error(error);
            alert("Error registrando banco: " + (error.response?.data?.message || error.message));
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold text-gray-900">Gestión de Bancos</h1>
                <button
                    onClick={() => setShowModal(true)}
                    className="bg-indigo-600 text-white px-4 py-2 rounded-lg hover:bg-indigo-700 flex items-center gap-2"
                >
                    <Plus size={18} /> Nuevo Banco
                </button>
            </div>

            <div className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden">
                <table className="w-full text-left text-sm text-gray-600">
                    <thead className="bg-gray-50 text-gray-900 font-medium">
                        <tr>
                            <th className="px-6 py-4">Banco</th>
                            <th className="px-6 py-4">Webhook URL</th>
                            <th className="px-6 py-4">Estado</th>
                            <th className="px-6 py-4 text-right">Acciones</th>
                        </tr>
                    </thead>
                    <tbody className="divide-y divide-gray-100">
                        {loading ? (
                            <tr><td colSpan="4" className="px-6 py-8 text-center text-gray-400">Cargando directorio...</td></tr>
                        ) : bancos.map((bank) => {
                            // Backend returns _id (mongo) but frontend logic uses codigoBic
                            // Backend returns datosTecnicos object, but table uses flat structure
                            const bic = bank._id || bank.codigoBic || 'UNKNOWN';
                            const url = bank.datosTecnicos?.urlDestino || bank.urlDestino || 'N/A';

                            return (
                                <tr key={bic} className="hover:bg-gray-50 transition">
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-3">
                                            <div className="h-10 w-10 rounded-full bg-indigo-50 flex items-center justify-center text-xs font-bold text-indigo-700">
                                                {bic.substring(0, 2)}
                                            </div>
                                            <div>
                                                <p className="font-semibold text-gray-900">{bank.nombre}</p>
                                                <p className="text-xs text-gray-400 font-mono">{bic}</p>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4">
                                        <div className="flex items-center gap-2 max-w-xs truncate" title={url}>
                                            <LinkIcon size={14} className="text-gray-400 shrink-0" />
                                            <span className="truncate">{url}</span>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4">
                                        <span className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium ${bank.estadoOperativo === 'ONLINE' ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'
                                            }`}>
                                            <span className={`h-1.5 w-1.5 rounded-full mr-1.5 ${bank.estadoOperativo === 'ONLINE' ? 'bg-green-500' : 'bg-gray-500'
                                                }`}></span>
                                            {bank.estadoOperativo}
                                        </span>
                                    </td>
                                    <td className="px-6 py-4 text-right">
                                        <select
                                            value={bank.estadoOperativo}
                                            onChange={(e) => handleStatusChange(bic, e.target.value)}
                                            className={`text-xs font-medium border-0 rounded-md py-1.5 pl-2 pr-8 ring-1 ring-inset focus:ring-2 sm:text-sm sm:leading-6 ${bank.estadoOperativo === 'ONLINE' ? 'text-green-700 bg-green-50 ring-green-600/20' :
                                                bank.estadoOperativo === 'SOLO_RECIBIR' ? 'text-blue-700 bg-blue-50 ring-blue-600/20' :
                                                    'text-red-700 bg-red-50 ring-red-600/20'
                                                }`}
                                        >
                                            <option value="ONLINE">ONLINE</option>
                                            <option value="OFFLINE">OFFLINE</option>
                                            <option value="SUSPENDIDO">SUSPENDIDO</option>
                                            <option value="MANT">MANTENIMIENTO</option>
                                            <option value="SOLO_RECIBIR">SOLO RECIBIR</option>
                                        </select>
                                    </td>
                                </tr>
                            );
                        })}
                    </tbody>
                </table>
            </div>

            {/* Modal */}
            {showModal && (
                <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
                    <div className="bg-white rounded-xl shadow-xl w-full max-w-lg mx-4 overflow-hidden">
                        <div className="flex justify-between items-center p-6 border-b border-gray-100">
                            <h3 className="text-lg font-bold text-gray-900">Registrar Banco</h3>
                            <button onClick={() => setShowModal(false)} className="text-gray-400 hover:text-gray-600">
                                <X size={20} />
                            </button>
                        </div>
                        <form onSubmit={handleSubmit} className="p-6 space-y-4">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">BIC</label>
                                    <input
                                        required
                                        value={formData.codigoBic}
                                        onChange={e => setFormData({ ...formData, codigoBic: e.target.value })}
                                        className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500 bg-gray-50 font-mono text-sm"
                                        placeholder="Ej: ARCBANK"
                                    />
                                </div>
                                <div>
                                    <label className="block text-sm font-medium text-gray-700 mb-1">Nombre</label>
                                    <input
                                        required
                                        value={formData.nombre}
                                        onChange={e => setFormData({ ...formData, nombre: e.target.value })}
                                        className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500"
                                        placeholder="Ej: ArcBank Internacional"
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">URL Webhook</label>
                                <div className="relative">
                                    <Server size={16} className="absolute left-3 top-3 text-gray-400" />
                                    <input
                                        required
                                        value={formData.urlDestino}
                                        onChange={e => setFormData({ ...formData, urlDestino: e.target.value })}
                                        className="w-full pl-9 pr-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500 font-mono text-xs"
                                        placeholder="http://..."
                                    />
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-medium text-gray-700 mb-1">Llave Pública / Secreta</label>
                                <div className="relative">
                                    <Key size={16} className="absolute left-3 top-3 text-gray-400" />
                                    <input
                                        required
                                        value={formData.llavePublica}
                                        onChange={e => setFormData({ ...formData, llavePublica: e.target.value })}
                                        className="w-full pl-9 pr-3 py-2 border rounded-lg focus:ring-2 focus:ring-indigo-500 font-mono text-xs"
                                        placeholder="KEY_..."
                                    />
                                </div>
                            </div>

                            <div className="pt-4 flex justify-end gap-3">
                                <button type="button" onClick={() => setShowModal(false)} className="px-4 py-2 text-gray-700 hover:bg-gray-100 rounded-lg">Cancelar</button>
                                <button type="submit" className="px-4 py-2 bg-indigo-600 text-white rounded-lg hover:bg-indigo-700 flex items-center gap-2">
                                    <Save size={18} /> Registrar
                                </button>
                            </div>
                        </form>
                    </div>
                </div>
            )}
        </div>
    );
}
