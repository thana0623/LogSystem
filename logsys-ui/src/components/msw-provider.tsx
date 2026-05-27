"use client";

import { useEffect, useState } from "react";

export function MSWProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (process.env.NEXT_PUBLIC_ENABLE_MOCK !== "true") {
      setReady(true);
      return;
    }

    import("@/mocks/server").then(({ worker }) => {
      worker.start({ onUnhandledRequest: "bypass" }).then(() => setReady(true));
    });
  }, []);

  if (!ready && process.env.NEXT_PUBLIC_ENABLE_MOCK === "true") {
    return null;
  }

  return <>{children}</>;
}
