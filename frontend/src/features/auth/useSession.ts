import { useContext } from "react";
import { SessionContext, type SessionContextValue } from "./sessionContext";

export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);
  if (!context) {
    throw new Error("useSession precisa estar dentro de <SessionProvider>");
  }
  return context;
}
