import { useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { setRedirectToLogin } from "../shared/api/sessionStore";

/**
 * Liga o "expulsa para /login" do cliente HTTP (refresh falho) à navegação do
 * React Router, para não recarregar a página inteira em uma SPA.
 */
export function SessionExpiredRedirect() {
  const navigate = useNavigate();

  useEffect(() => {
    setRedirectToLogin(() => navigate("/login", { replace: true }));
  }, [navigate]);

  return null;
}
