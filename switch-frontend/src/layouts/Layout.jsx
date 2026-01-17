import { useState } from 'react';
import { Outlet, Link, useLocation } from 'react-router-dom';
import { LayoutDashboard, Building2, BookOpen, Menu, X, Bell, Activity } from 'lucide-react';
import { clsx } from 'clsx';
import { twMerge } from 'tailwind-merge';

function cn(...inputs) {
    return twMerge(clsx(inputs));
}

export default function Layout() {
    const [sidebarOpen, setSidebarOpen] = useState(true);
    const location = useLocation();

    const navigation = [
        { name: 'Torre de Control', href: '/dashboard', icon: LayoutDashboard },
        { name: 'Bancos', href: '/bancos', icon: Building2 },
        { name: 'Transacciones', href: '/transacciones', icon: Activity },
        { name: 'Contabilidad', href: '/contabilidad', icon: BookOpen },
        { name: 'Compensación', href: '/compensacion', icon: Activity },
    ];

    return (
        <div className="min-h-screen bg-gray-50 flex">
            <aside
                className={cn(
                    "fixed inset-y-0 left-0 z-50 w-64 bg-white border-r border-gray-200 transition-transform duration-300 ease-in-out transform",
                    sidebarOpen ? "translate-x-0" : "-translate-x-full",
                    "lg:relative lg:translate-x-0"
                )}
            >
                <div className="flex items-center justify-between h-16 px-6 border-b border-gray-100">
                    <span className="text-xl font-bold text-blue-600 tracking-tight">SWITCH<span className="text-gray-900">ADMIN</span></span>
                    <button onClick={() => setSidebarOpen(false)} className="lg:hidden p-1 text-gray-500 hover:text-gray-700">
                        <X size={20} />
                    </button>
                </div>

                <nav className="p-4 space-y-1">
                    {navigation.map((item) => {
                        const isActive = location.pathname.startsWith(item.href);
                        return (
                            <Link
                                key={item.name}
                                to={item.href}
                                className={cn(
                                    "flex items-center gap-3 px-4 py-3 rounded-lg text-sm font-medium transition-colors",
                                    isActive
                                        ? "bg-blue-50 text-blue-700"
                                        : "text-gray-600 hover:bg-gray-50 hover:text-gray-900"
                                )}
                            >
                                <item.icon size={20} />
                                {item.name}
                            </Link>
                        );
                    })}
                </nav>
            </aside>

            <div className="flex-1 flex flex-col min-w-0">
                <header className="h-16 bg-white border-b border-gray-200 flex items-center justify-between px-4 lg:px-8">
                    <button
                        onClick={() => setSidebarOpen(true)}
                        className={cn("p-2 text-gray-500 hover:text-gray-700 lg:hidden", sidebarOpen && "hidden")}
                    >
                        <Menu size={20} />
                    </button>

                    <div className="flex items-center gap-4 ml-auto">
                        <div className="relative">
                            <Bell size={20} className="text-gray-400 hover:text-gray-600 cursor-pointer" />
                            <span className="absolute top-0 right-0 block h-2 w-2 rounded-full bg-red-500 ring-2 ring-white" />
                        </div>
                        <div className="h-8 w-8 rounded-full bg-gradient-to-tr from-blue-500 to-indigo-500 flex items-center justify-center text-white text-xs font-bold">
                            AD
                        </div>
                    </div>
                </header>

                <main className="flex-1 p-4 lg:p-8 overflow-auto">
                    <div className="max-w-7xl mx-auto">
                        <Outlet />
                    </div>
                </main>
            </div>
        </div>
    );
}
