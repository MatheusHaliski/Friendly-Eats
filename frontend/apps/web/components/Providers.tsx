'use client';

import { isApiError } from '@friendly-eats/api';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import { SESSION_KEY } from '@/lib/queries';

export const SESSION_CHANNEL = 'fe-sessao';

function makeClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        // 4xx não melhora repetindo (401, 404, 422); 5xx e rede tentam mais uma vez
        retry: (count, error) => !(isApiError(error) && error.status < 500) && count < 1,
        refetchOnWindowFocus: false,
      },
      mutations: { retry: false },
    },
  });
}

/** Avisa as outras abas que a sessão acabou (logout): elas passam a se ver deslogadas. */
export function broadcastLogout() {
  try {
    const ch = new BroadcastChannel(SESSION_CHANNEL);
    ch.postMessage('logout');
    ch.close();
  } catch {
    // navegador sem BroadcastChannel: a outra aba descobre no próximo 401
  }
}

export function Providers({ children }: { children: ReactNode }) {
  const [client] = useState(makeClient);
  useEffect(() => {
    if (typeof BroadcastChannel === 'undefined') return;
    const ch = new BroadcastChannel(SESSION_CHANNEL);
    ch.onmessage = (ev) => {
      if (ev.data === 'logout') {
        client.setQueryData(SESSION_KEY, null);
        void client.invalidateQueries();
      }
    };
    return () => ch.close();
  }, [client]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
