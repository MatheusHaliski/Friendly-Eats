import type { Metadata } from 'next';
import Link from 'next/link';
import { IconBlock, IconMute, IconReport, IconSupport } from '@/components/icons';

export const metadata: Metadata = { title: 'Segurança e denúncias' };

/**
 * Política contra intimidação e apoio às vítimas (RF19 · ECA Digital, art. 6º, §2º; Lei 14.811/2024, que criou o
 * art. 146-A do Código Penal). Página pública, com os canais de denúncia e de apoio. ⚖️ Canais externos a confirmar.
 */
export default function Page() {
  return (
    <article className="grid max-w-3xl gap-8" aria-labelledby="titulo-seguranca">
      <header className="grid gap-2">
        <h1 id="titulo-seguranca" className="flex items-center gap-2 text-3xl font-extrabold">
          <IconSupport size={28} />
          Segurança e denúncias
        </h1>
        <p className="text-lg">
          No Friendly Eats a crítica à comida é bem-vinda; ataque a pessoas, não. Intimidação sistemática na internet (cyberbullying) é crime no Brasil
          (Código Penal, art. 146-A).
        </p>
      </header>

      <section aria-labelledby="sofreu" className="grid gap-3">
        <h2 id="sofreu" className="text-xl font-bold">
          Sofreu intimidação?
        </h2>
        <ol className="grid list-decimal gap-2 pl-6">
          <li>Você não tem culpa. Não responda no calor do momento: isso costuma piorar os ataques.</li>
          <li>
            Guarde provas: faça capturas de tela com a data e o link. A moderação também guarda uma cópia do que for denunciado.
          </li>
          <li>
            <span className="inline-flex items-center gap-1 font-semibold">
              <IconBlock size={16} />
              Bloqueie
            </span>{' '}
            quem ataca (a pessoa deixa de ver você e de comentar) ou{' '}
            <span className="inline-flex items-center gap-1 font-semibold">
              <IconMute size={16} />
              silencie
            </span>{' '}
            (você deixa de ver, sem que ela saiba). Os dois ficam no menu &quot;…&quot; do perfil dela.
          </li>
          <li>
            Ligue o filtro de ofensas e escolha palavras que escondem comentários nos seus posts em{' '}
            <Link href="/configuracoes/privacidade" className="font-semibold underline">
              Configurações → Privacidade
            </Link>
            . Para menores de 16 anos ele já vem ligado.
          </li>
          <li>
            <span className="inline-flex items-center gap-1 font-semibold">
              <IconReport size={16} />
              Denuncie
            </span>{' '}
            pelo menu &quot;…&quot; do post, comentário, avaliação ou perfil, com o motivo &quot;Assédio ou cyberbullying&quot;. Você recebe um protocolo e
            acompanha em{' '}
            <Link href="/configuracoes/denuncias" className="font-semibold underline">
              Minhas denúncias
            </Link>
            . Quem publicou não fica sabendo quem denunciou.
          </li>
          <li>Conte para alguém de confiança. Se você tem menos de 18 anos, fale com o seu responsável ou com alguém da escola.</li>
        </ol>
      </section>

      <section aria-labelledby="apoio" className="grid gap-3">
        <h2 id="apoio" className="text-xl font-bold">
          Onde buscar apoio
        </h2>
        <ul className="grid gap-2">
          <li>
            <strong>CVV, Centro de Valorização da Vida:</strong> ligue 188, de graça, 24 horas, para conversar sobre o que está sentindo.
          </li>
          <li>
            <strong>SaferNet, canal de ajuda:</strong> orientação sobre violência na internet em{' '}
            <a href="https://new.safernet.org.br/helpline" className="underline" target="_blank" rel="noopener noreferrer">
              safernet.org.br<span className="sr-only"> (abre em outra aba)</span>
            </a>
            .
          </li>
          <li>
            <strong>Disque 100:</strong> violação de direitos humanos, inclusive de crianças e adolescentes.
          </li>
          <li>
            <strong>Risco imediato:</strong> ligue 190 (Polícia Militar).
          </li>
        </ul>
      </section>

      <section aria-labelledby="regras" className="grid gap-3">
        <h2 id="regras" className="text-xl font-bold">
          Como a moderação decide
        </h2>
        <ul className="grid list-disc gap-2 pl-6">
          <li>Toda denúncia tem motivo de uma lista fechada e entra numa fila: risco a criança e desinformação sanitária vêm primeiro.</li>
          <li>Denúncia de risco a criança tira o conteúdo do ar na hora, até a análise humana.</li>
          <li>Quem publicou recebe a decisão com o motivo, a regra aplicada e o prazo para contestar. Outra pessoa da moderação revê o recurso.</li>
          <li>Denúncias falsas repetidas perdem prioridade. Usar a denúncia para perseguir alguém também fere os Termos de Uso.</li>
          <li>
            A regra completa está nos{' '}
            <Link href="/termos" className="font-semibold underline">
              Termos de Uso
            </Link>
            .
          </li>
        </ul>
      </section>

      <section aria-labelledby="sem-conta" className="fe-card grid gap-2 p-5">
        <h2 id="sem-conta" className="text-xl font-bold">
          Não tem conta?
        </h2>
        <p>Qualquer pessoa pode denunciar, informando nome e e-mail.</p>
        <p>
          <Link href="/denunciar" className="fe-btn fe-btn-primary">
            <IconReport size={18} />
            Denunciar sem conta
          </Link>
        </p>
      </section>
    </article>
  );
}
