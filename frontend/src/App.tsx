import { QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter } from "react-router-dom";
import { SessionProvider } from "./features/auth/session";
import { queryClient } from "./shared/api/queryClient";
import { AppRoutes } from "./routes/AppRoutes";
import { SimulatedClockBanner } from "./shared/components/SimulatedClockBanner";

function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <SessionProvider>
          {/* Fora de qualquer rota: aparece também no /login, que não usa o AppShell. */}
          <SimulatedClockBanner />
          <AppRoutes />
        </SessionProvider>
      </BrowserRouter>
    </QueryClientProvider>
  );
}

export default App;
