import { createContext } from "react";
import { setSession } from "../../shared/api/sessionStore";
import type { SessionUser } from "../../shared/api/types";

export interface SessionContextValue {
  accessToken: string | null;
  user: SessionUser | null;
  /** `true` enquanto o bootstrap inicial (refresh silencioso) está em andamento. */
  isLoading: boolean;
  setSession: typeof setSession;
  logout: () => Promise<void>;
}

export const SessionContext = createContext<SessionContextValue | null>(null);
