import { useEffect, useState } from 'react';
import { BarChart3, TrendingUp, Users, Activity, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';
import { nucleoApi, directorioApi } from '../api/client';

function StatCard({ title, value, change, icon: Icon, color }) {
    return (
        <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm transition hover:shadow-md">
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm font-medium text-gray-500">{title}</p>
                    <h3 className="text-2xl font-bold text-gray-900 mt-1">{value}</h3>
                </div>
                <div className={`p-3 rounded-lg ${color}`}>
                    <Icon size={24} className="text-white" />
                </div>
            </div>
            {change && (
                <div className="mt-4 flex items-center text-sm">
                    <span className="text-green-500 font-medium flex items-center">
                        <TrendingUp size={16} className="mr-1" />
                        {change}
                    </span>
                    <span className="text-gray-400 ml-2">vs 24h</span>
                </div>
            )}
        </div>
    );
}

export default function Dashboard() {
    const [stats, setStats] = useState(null);
    const [bancos, setBancos] = useState([]);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        async function loadData() {
            try {
                const [statsRes, banksRes] = await Promise.all([
                    nucleoApi.get('/transacciones/stats'),
                    directorioApi.get('/instituciones')
                ]);
                setStats(statsRes.data);
                setBancos(banksRes.data);
            } catch (error) {
                console.error("Error cargando dashboard:", error);
            } finally {
                setLoading(false);
            }
        }
        loadData();
    }, []);

    if (loading) return <div className="p-10 text-center text-gray-400">Cargando métricas...</div>;

    // Chart Data Preparation (Static hourly distribution for demo, or based on stats if available)
    // For now we map status counts to simple chart
    const chartData = [
        { name: 'Completed', val: stats?.count_COMPLETED || 0 },
        { name: 'Failed', val: stats?.count_FAILED || 0 },
        { name: 'Pending', val: stats?.count_PENDING || 0 },
        { name: 'Timeout', val: stats?.count_TIMEOUT || 0 },
    ];

    return (
        <div className="space-y-6 animate-in fade-in duration-500">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-bold text-gray-900">Torre de Control</h1>
                <div className="flex items-center gap-2 px-3 py-1 bg-green-50 rounded-full border border-green-200">
                    <span className="h-2 w-2 rounded-full bg-green-500 animate-pulse"></span>
                    <span className="text-xs font-bold text-green-700">SISTEMA ONLINE</span>
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <StatCard
                    title="Volumen (24h)"
                    value={`$${stats?.totalVolumeExample || 0}`}
                    change=""
                    icon={Activity}
                    color="bg-blue-600"
                />
                <StatCard
                    title="Transacciones"
                    value={stats?.totalTransactions24h || 0}
                    change=""
                    icon={BarChart3}
                    color="bg-indigo-600"
                />
                <StatCard
                    title="Bancos Activos"
                    value={`${bancos.filter(b => b.estadoOperativo === 'ONLINE').length}/${bancos.length}`}
                    change=""
                    icon={Users}
                    color="bg-violet-600"
                />
                <StatCard
                    title="TPS (Promedio)"
                    value={stats?.tps || 0}
                    change=""
                    icon={TrendingUp}
                    color="bg-emerald-600"
                />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
                    <h3 className="text-lg font-bold text-gray-900 mb-6">Distribución de Estados</h3>
                    <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={chartData}>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#6B7280' }} />
                                <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6B7280' }} />
                                <Tooltip
                                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                                    cursor={{ fill: '#F3F4F6' }}
                                />
                                <Bar dataKey="val" fill="#4F46E5" radius={[4, 4, 0, 0]} barSize={50} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm flex flex-col">
                    <h3 className="text-lg font-bold text-gray-900 mb-4">Estado de Bancos ({bancos.length})</h3>
                    <div className="space-y-3 overflow-y-auto max-h-[320px] pr-2">
                        {bancos.length === 0 && <p className="text-gray-400 text-sm text-center">No hay bancos registrados</p>}
                        {bancos.length === 0 && <p className="text-gray-400 text-sm text-center">No hay bancos registrados</p>}
                        {bancos.map((bank) => {
                            // Safe extraction of BIC and Name
                            const bic = bank.codigoBic || bank.id || bank._id || 'UNKNOWN';
                            const name = bank.nombre || 'Banco Desconocido';
                            const status = bank.estadoOperativo || 'OFFLINE';

                            return (
                                <div key={bic} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg group hover:bg-gray-100 transition">
                                    <div className="flex items-center gap-3">
                                        <div className="h-9 w-9 rounded-full bg-white border border-gray-200 flex items-center justify-center text-xs font-bold text-gray-700 shadow-sm">
                                            {bic.substring(0, 2)}
                                        </div>
                                        <div className="flex flex-col">
                                            <span className="font-semibold text-gray-800 text-sm">{name}</span>
                                            <span className="text-[10px] text-gray-400">{bic}</span>
                                        </div>
                                    </div>
                                    <span className={`text-[10px] px-2 py-1 rounded-full font-bold border ${status === 'ONLINE'
                                        ? 'bg-green-100 text-green-700 border-green-200'
                                        : 'bg-red-100 text-red-700 border-red-200'
                                        }`}>
                                        {status}
                                    </span>
                                </div>
                            );
                        })}
                    </div>
                    {/* Alerts Section */}
                    {stats?.successRate < 90 && (
                        <div className="mt-4 p-3 bg-red-50 border border-red-100 rounded-lg flex gap-3 items-start animate-pulse">
                            <AlertTriangle className="text-red-500 shrink-0" size={18} />
                            <div>
                                <h4 className="text-sm font-bold text-red-800">Alerta de Calidad</h4>
                                <p className="text-xs text-red-600 mt-1">
                                    Tasa de éxito ({stats?.successRate}%) por debajo del umbral (90%). Revise logs.
                                </p>
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
}
