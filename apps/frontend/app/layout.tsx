import type { Metadata } from "next";
import "./globals.css";
import "./theme.css";
import "./academic-ui.css";
import "./project-library.css";
import "./template-library.css";
export const metadata: Metadata = {
    title: "Latex",
    description: "Task Writing, courses and collaborative LaTeX publishing in one academic workspace.",
};
export default function RootLayout({
    children,
}: Readonly<{ children: React.ReactNode }>) {
    return (
        <html lang="en">
            <body>{children}</body>
        </html>
    );
}
