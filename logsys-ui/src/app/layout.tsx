import type { Metadata } from "next";
import { Providers } from "./providers";
import "@/shared/styles/globals.css";

export const metadata: Metadata = {
  title: "LogSystem",
  description: "Lightweight log collection and analysis platform",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="zh-CN" className="dark" suppressHydrationWarning>
      <body>
        <Providers>{children}</Providers>
      </body>
    </html>
  );
}
