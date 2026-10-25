import type { Metadata } from 'next';
import { AccountView } from '@/components/account/AccountView';

export const metadata: Metadata = { title: 'Minha conta' };

export default function ContaPage() {
  return <AccountView />;
}
