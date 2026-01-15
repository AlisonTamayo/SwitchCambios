import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './layouts/Layout';
import Dashboard from './pages/Dashboard';
import Bancos from './pages/Bancos';
import Transacciones from './pages/Transacciones';
import Contabilidad from './pages/Contabilidad';
import Compensacion from './pages/Compensacion';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="bancos" element={<Bancos />} />
          <Route path="transacciones" element={<Transacciones />} />
          <Route path="contabilidad" element={<Contabilidad />} />
          <Route path="compensacion" element={<Compensacion />} />
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
