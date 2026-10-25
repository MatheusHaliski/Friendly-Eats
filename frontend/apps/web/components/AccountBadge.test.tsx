import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ACCOUNT_TYPES } from '@/lib/account-types';
import { AccountBadge, VerifiedSeal } from './AccountBadge';
import { CuisineChip } from './CuisineChip';

describe('sinais que não dependem só da cor', () => {
  it.each(ACCOUNT_TYPES.map((t) => [t.code, t.glyph, t.label] as const))('%s: bolinha + glifo %s + rótulo', (code, glyph, label) => {
    const { container } = render(<AccountBadge code={code} />);
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(container.querySelector('.fe-glyph')).toHaveTextContent(glyph);
    expect(container.querySelector('.fe-dot')).toHaveAttribute('data-type', code);
  });

  it('selo com rótulo acessível', () => {
    render(<VerifiedSeal />);
    expect(screen.getByText('Conta verificada')).toHaveClass('sr-only');
  });

  it('chip com pictograma e rótulo, cores dos dois temas', () => {
    const { container } = render(
      <CuisineChip chip={{ code: 'BRASILEIRA', label: 'Brasileira', lucideIcon: 'cooking-pot', darkHex: '#DDAE14', lightHex: '#6B5300' }} />,
    );
    const chip = container.querySelector('.fe-chip') as HTMLElement;
    expect(chip).toHaveTextContent('Culinária: Brasileira');
    expect(chip.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    expect(chip.style.getPropertyValue('--chip-dark')).toBe('#DDAE14');
    expect(chip.style.getPropertyValue('--chip-light')).toBe('#6B5300');
  });
});
