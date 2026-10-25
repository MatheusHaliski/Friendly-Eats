import type { Metadata } from 'next';
import { RegisterView } from '@/components/auth/RegisterView';

export const metadata: Metadata = { title: 'Criar conta' };

export default function CadastroPage() {
  return <RegisterView />;
}
