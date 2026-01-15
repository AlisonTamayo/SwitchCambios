import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './layouts/Layout';
import Dashboard from './pages/Dashboard';
import Bancos from './pages/Bancos';
import Contabilidad from './pages/Contabilidad';

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<Dashboard />} />
          <Route path="bancos" element={<Bancos />} />
          <Route path="contabilidad" element={<Contabilidad />} />
        </Route>
      </Routes>
    </Router>
  );
}

export default App;
