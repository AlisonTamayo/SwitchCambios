import { BarChart3, TrendingUp, Users, Activity } from 'lucide-react';
import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer } from 'recharts';

const data = [
    { name: '00:00', txs: 400 },
    { name: '04:00', txs: 300 },
    { name: '08:00', txs: 2000 },
    { name: '12:00', txs: 2780 },
    { name: '16:00', txs: 1890 },
    { name: '20:00', txs: 2390 },
];

function StatCard({ title, value, change, icon: Icon, color }) {
    return (
        <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm font-medium text-gray-500">{title}</p>
                    <h3 className="text-2xl font-bold text-gray-900 mt-1">{value}</h3>
                </div>
                <div className={`p-3 rounded-lg ${color}`}>
                    <Icon size={24} className="text-white" />
                </div>
            </div>
            <div className="mt-4 flex items-center text-sm">
                <span className="text-green-500 font-medium flex items-center">
                    <TrendingUp size={16} className="mr-1" />
                    {change}
                </span>
                <span className="text-gray-400 ml-2">vs último mes</span>
            </div>
        </div>
    );
}

export default function Dashboard() {
    return (
        <div className="space-y-6">
            <div className="flex items-center justify-between">
                <h1 className="text-2xl font-bold text-gray-900">Torre de Control</h1>
                <div className="flex items-center gap-2">
                    <span className="h-3 w-3 rounded-full bg-green-500 animate-pulse"></span>
                    <span className="text-sm font-medium text-green-600">Sistema Operativo</span>
                </div>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <StatCard title="Volumen Total" value="$4.2M" change="+12.5%" icon={Activity} color="bg-blue-600" />
                <StatCard title="Transacciones" value="145.2K" change="+8.2%" icon={BarChart3} color="bg-indigo-600" />
                <StatCard title="Bancos Activos" value="8/8" change="+0.0%" icon={Users} color="bg-violet-600" />
                <StatCard title="Tasa de Éxito" value="99.9%" change="+0.4%" icon={TrendingUp} color="bg-emerald-600" />
            </div>

            <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div className="lg:col-span-2 bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
                    <h3 className="text-lg font-bold text-gray-900 mb-6">Volumen Transaccional (24h)</h3>
                    <div className="h-80">
                        <ResponsiveContainer width="100%" height="100%">
                            <BarChart data={data}>
                                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" />
                                <XAxis dataKey="name" axisLine={false} tickLine={false} tick={{ fill: '#6B7280' }} />
                                <YAxis axisLine={false} tickLine={false} tick={{ fill: '#6B7280' }} />
                                <Tooltip
                                    contentStyle={{ borderRadius: '8px', border: 'none', boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)' }}
                                    cursor={{ fill: '#F3F4F6' }}
                                />
                                <Bar dataKey="txs" fill="#4F46E5" radius={[4, 4, 0, 0]} barSize={40} />
                            </BarChart>
                        </ResponsiveContainer>
                    </div>
                </div>

                <div className="bg-white p-6 rounded-xl border border-gray-100 shadow-sm">
                    <h3 className="text-lg font-bold text-gray-900 mb-6">Estado de Bancos</h3>
                    <div className="space-y-4">
                        {['Nexus Bank', 'ArcBank', 'EcuSol', 'BanTec', 'TrustCorp'].map((bank, i) => (
                            <div key={bank} className="flex items-center justify-between p-3 bg-gray-50 rounded-lg">
                                <div className="flex items-center gap-3">
                                    <div className="h-8 w-8 rounded-full bg-gray-200 flex items-center justify-center text-xs font-bold text-gray-600">
                                        {bank.substring(0, 2).toUpperCase()}
                                    </div>
                                    <span className="font-medium text-gray-700">{bank}</span>
                                </div>
                                <span className="text-xs px-2 py-1 bg-green-100 text-green-700 rounded-full font-medium">Online</span>
                            </div>
                        ))}
                    </div>
                </div>
            </div>
        </div>
    );
}
