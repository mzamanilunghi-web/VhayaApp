import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  onAuthStateChanged,
  sendEmailVerification,
  signInWithEmailAndPassword,
  signOut as firebaseSignOut,
  type User,
} from "firebase/auth";
import { useQueryClient } from "@tanstack/react-query";

import {
  claimVhayaSession,
  getAdminSession,
  getVhayaSessionStorageKey,
  releaseVhayaSession,
  VhayaApiError,
  type AdminSession,
} from "./vhaya-api";
import { firebaseAuth, firebaseConfigReady } from "./firebase";

export type AuthStatus =
  | "loading"
  | "signed_out"
  | "unverified"
  | "not_admin"
  | "ready"
  | "error";

interface AuthState {
  status: AuthStatus;
  user: User | null;
  admin: AdminSession | null;
  error: string | null;
}

interface AuthContextValue extends AuthState {
  configurationReady: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  resendVerification: () => Promise<void>;
  signOut: () => Promise<void>;
  clearError: () => void;
}

const initialState: AuthState = {
  status: firebaseConfigReady ? "loading" : "error",
  user: null,
  admin: null,
  error: firebaseConfigReady
    ? null
    : "Firebase web app settings are missing. Add the VITE_FIREBASE_* values to this project.",
};

const AuthContext = createContext<AuthContextValue | null>(null);

function errorMessage(error: unknown): string {
  if (error instanceof VhayaApiError) {
    if (error.status === 401) {
      return "Your Vhaya session is no longer valid. Sign out and sign in again to start a new session.";
    }
    if (error.status === 403) {
      return "This verified account does not have the Vhaya admin role.";
    }
    if (error.status === 429) {
      return "Too many requests. Wait a moment and try again.";
    }
    if (error.status >= 500) {
      return "The Vhaya service is temporarily unavailable. Try again shortly.";
    }
    return error.message;
  }

  if (error instanceof Error && "code" in error) {
    const code = String((error as Error & { code?: string }).code);
    if (code.includes("invalid-credential") || code.includes("wrong-password")) {
      return "The email or password is incorrect.";
    }
    if (code.includes("too-many-requests")) {
      return "Too many sign-in attempts. Try again later.";
    }
    if (code.includes("network-request-failed")) {
      return "Could not reach Firebase. Check your connection and try again.";
    }
  }

  return error instanceof Error ? error.message : "Something went wrong. Try again.";
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [state, setState] = useState<AuthState>(initialState);

  useEffect(() => {
    if (!firebaseAuth) return;

    return onAuthStateChanged(firebaseAuth, async (user) => {
      queryClient.clear();
      if (!user) {
        window.sessionStorage.removeItem(getVhayaSessionStorageKey());
        setState({ status: "signed_out", user: null, admin: null, error: null });
        return;
      }

      setState({ status: "loading", user, admin: null, error: null });

      if (!user.emailVerified) {
        window.sessionStorage.removeItem(getVhayaSessionStorageKey());
        setState({
          status: "unverified",
          user,
          admin: null,
          error: "Verify this email address before using the admin console.",
        });
        return;
      }

      try {
        let sessionId = window.sessionStorage.getItem(
          getVhayaSessionStorageKey(),
        );
        if (!sessionId) {
          sessionId = await claimVhayaSession(user);
        }
        const admin = await getAdminSession(user, sessionId);
        setState({ status: "ready", user, admin, error: null });
      } catch (error) {
        if (error instanceof VhayaApiError && error.status === 403) {
          try {
            await releaseVhayaSession(user);
          } catch {
            window.sessionStorage.removeItem(getVhayaSessionStorageKey());
          }
          setState({
            status: "not_admin",
            user,
            admin: null,
            error: "This verified account is not provisioned as a Vhaya admin.",
          });
          return;
        }
        setState({
          status: "error",
          user,
          admin: null,
          error: errorMessage(error),
        });
      }
    });
  }, [queryClient]);

  const signIn = useCallback(async (email: string, password: string) => {
    if (!firebaseAuth) {
      throw new Error("Firebase is not configured for this admin app.");
    }
    setState({ status: "loading", user: null, admin: null, error: null });
    try {
      await signInWithEmailAndPassword(firebaseAuth, email.trim(), password);
    } catch (error) {
      const message = errorMessage(error);
      setState({ status: "signed_out", user: null, admin: null, error: message });
      throw new Error(message);
    }
  }, []);

  const resendVerification = useCallback(async () => {
    if (!state.user) {
      throw new Error("Sign in to request a verification email.");
    }
    await sendEmailVerification(state.user);
  }, [state.user]);

  const signOut = useCallback(async () => {
    const user = firebaseAuth?.currentUser;
    try {
      if (user) await releaseVhayaSession(user);
    } catch {
      window.sessionStorage.removeItem(getVhayaSessionStorageKey());
    } finally {
      if (firebaseAuth) await firebaseSignOut(firebaseAuth);
      queryClient.clear();
      setState({ status: "signed_out", user: null, admin: null, error: null });
    }
  }, [queryClient]);

  const clearError = useCallback(() => {
    setState((current) => ({ ...current, error: null }));
  }, []);

  const value = useMemo(
    () => ({
      ...state,
      configurationReady: firebaseConfigReady,
      signIn,
      resendVerification,
      signOut,
      clearError,
    }),
    [state, signIn, resendVerification, signOut, clearError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) throw new Error("useAuth must be used inside AuthProvider.");
  return context;
}
