import { useState, useEffect } from 'react';
import { Wallet, ShieldCheck, ShieldAlert, RefreshCw, ArrowUpRight, ArrowDownLeft } from 'lucide-react';
import { directorioApi, contabilidadApi } from '../api/client';

export default function Contabilidad() {
    const [cuentas, setCuentas] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        loadData();
    }, []);

    const loadData = async () => {
        setLoading(true);
        try {
            const banksRes = await directorioApi.get('/instituciones');
            const banks = banksRes.data;

            const ledgerPromises = banks.map(async (bank) => {
                const bic = bank.codigoBic || bank.id || bank._id;
                if (!bic) return null;

                try {


                    const res = await contabilidadApi.get(`/ledger/cuentas/${bic}`);
                    return { ...bank, codigoBic: bic, cuenta: res.data, hasAccount: true };
                } catch (err) {
                    return { ...bank, codigoBic: bic, cuenta: null, hasAccount: false };
                }
            });

            const results = (await Promise.all(ledgerPromises)).filter(Boolean);
            setCuentas(results);

        } catch (error) {
            console.error("Error loading ledger:", error);
        } finally {
            setLoading(false);
        }
    };

    const handleCreateAccount = async (bic) => {
        try {
            await contabilidadApi.post('/ledger/cuentas', { codigoBic: bic });
            alert("Cuenta técnica creada exitosamente.");
            loadData();
        } catch (error) {
            alert("Error creando cuenta: " + (error.response?.data?.message || error.message));
        }
    };

    const handleDeposit = async (bic) => {
        const amount = prompt(`Ingrese monto a recargar para ${bic}:`, "1000000");
        if (!amount) return;

        try {
            await contabilidadApi.post('/ledger/movimientos', {
                codigoBic: bic,
                tipo: 'CREDIT',
                monto: parseFloat(amount),
                idInstruccion: crypto.randomUUID()
            });
            alert("Recarga exitosa.");
            loadData();
        } catch (error) {
            alert("Error en recarga: " + (error.response?.data?.error || error.message));
        }
    };

    return (
        <div className="space-y-6">
            <div className="flex justify-between items-center">
                <h1 className="text-2xl font-bold text-gray-900">Libro Mayor (Ledger)</h1>
                <button onClick={loadData} className="text-gray-500 hover:text-indigo-600">
                    <RefreshCw size={20} />
                </button>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                {loading ? (
                    [1, 2, 3].map(i => <div key={i} className="h-48 bg-gray-100 rounded-xl animate-pulse"></div>)
                ) : cuentas.map((item) => (
                    <div key={item.codigoBic} className="bg-white rounded-xl border border-gray-100 shadow-sm overflow-hidden flex flex-col">
                        <div className="p-6 flex-1">
                            <div className="flex justify-between items-start mb-4">
                                <div className="flex items-center gap-3">
                                    <div className="h-10 w-10 rounded-full bg-indigo-50 flex items-center justify-center text-xs font-bold text-indigo-700">
                                        {item.codigoBic.substring(0, 2)}
                                    </div>
                                    <div>
                                        <h3 className="font-bold text-gray-900">{item.nombre}</h3>
                                        <p className="text-xs text-gray-500 font-mono">{item.codigoBic}</p>
                                    </div>
                                </div>
                                {item.hasAccount ? (
                                    <span className="text-xs bg-green-50 text-green-700 px-2 py-1 rounded-full border border-green-100 flex items-center gap-1">
                                        <ShieldCheck size={12} /> Hash OK
                                    </span>
                                ) : (
                                    <span className="text-xs bg-gray-100 text-gray-500 px-2 py-1 rounded-full">Sin Cuenta</span>
                                )}
                            </div>

                            {item.hasAccount ? (
                                <div className="space-y-4">
                                    <div>
                                        <p className="text-sm text-gray-500 mb-1">Saldo Disponible</p>
                                        <h2 className="text-3xl font-bold text-gray-900 tracking-tight">
                                            $ {item.cuenta.saldoDisponible.toLocaleString('en-US', { minimumFractionDigits: 2 })}
                                        </h2>
                                    </div>
                                    <div className="pt-4 border-t border-gray-50 flex justify-between items-center">
                                        <div className="flex items-center justify-between text-xs text-gray-400 font-mono">
                                            <span title={item.cuenta.firmaIntegridad}>SHA-256: {item.cuenta.firmaIntegridad.substring(0, 8)}...</span>
                                        </div>
                                        <div className="text-right">
                                            <p className="text-xs text-gray-500">En Reserva (Bloqueado)</p>
                                            <span className="text-sm font-semibold text-orange-600">
                                                $ {item.cuenta.fondosBloqueados ? item.cuenta.fondosBloqueados.toLocaleString('en-US', { minimumFractionDigits: 2 }) : '0.00'}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            ) : (
                                <div className="h-24 flex items-center justify-center text-sm text-gray-400 bg-gray-50 rounded-lg border border-dashed border-gray-200">
                                    No hay cuenta técnica activa
                                </div>
                            )}
                        </div>

                        <div className="bg-gray-50 px-6 py-3 flex justify-between border-t border-gray-100 items-center">
                            {item.hasAccount ? (
                                <>
                                    <button className="text-xs font-medium text-gray-600 hover:text-indigo-600 flex items-center gap-1">
                                        <ArrowUpRight size={14} /> Audit Log
                                    </button>
                                    <button
                                        onClick={() => handleDeposit(item.codigoBic)}
                                        className="text-xs font-bold text-green-600 hover:text-green-800 flex items-center gap-1"
                                    >
                                        <ArrowDownLeft size={14} /> Recargar Fondos
                                    </button>
                                </>
                            ) : (
                                <button
                                    onClick={() => handleCreateAccount(item.codigoBic)}
                                    className="w-full text-center text-xs font-bold text-indigo-600 hover:text-indigo-800 flex justify-center items-center gap-2"
                                >
                                    <Wallet size={14} /> Activar Cuenta Técnica
                                </button>
                            )}
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}
