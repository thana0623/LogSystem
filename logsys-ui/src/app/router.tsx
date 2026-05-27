import { Routes, Route, Navigate } from "react-router-dom";

export function AppRouter() {
  return (
    <Routes>
      <Route path="/" element={<Navigate to="/dashboard" replace />} />
      <Route
        path="/dashboard"
        element={<div className="p-6 text-foreground">Dashboard — TODO</div>}
      />
      <Route
        path="/logs"
        element={<div className="p-6 text-foreground">Log Explorer — TODO</div>}
      />
      <Route
        path="/errors"
        element={
          <div className="p-6 text-foreground">Error Cluster — TODO</div>
        }
      />
      <Route
        path="/services/:name"
        element={
          <div className="p-6 text-foreground">Service Detail — TODO</div>
        }
      />
    </Routes>
  );
}
