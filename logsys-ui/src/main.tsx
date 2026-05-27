import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { App } from "./app/app";
import "./shared/styles/globals.css";

async function enableMocking() {
  if (import.meta.env.VITE_ENABLE_MOCK !== "true") return;

  const { worker } = await import("./mocks/server");
  return worker.start({ onUnhandledRequest: "bypass" });
}

enableMocking().then(() => {
  createRoot(document.getElementById("root")!).render(
    <StrictMode>
      <App />
    </StrictMode>
  );
});
