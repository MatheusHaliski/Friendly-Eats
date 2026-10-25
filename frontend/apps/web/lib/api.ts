import { createApi } from '@friendly-eats/api';

/** Cliente único do navegador: mesma origem, cookie de sessão HttpOnly e CSRF no cabeçalho. */
export const api = createApi();
