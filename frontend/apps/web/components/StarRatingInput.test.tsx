import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { StarRatingInput } from './StarRatingInput';

function Harness() {
  const [v, setV] = useState<number | null>(null);
  return <StarRatingInput name="nota" legend="Sua nota" value={v} onChange={setV} required />;
}

describe('StarRatingInput', () => {
  it('grupo de rádio com 5 opções rotuladas, inteiras', () => {
    render(<Harness />);
    const group = screen.getByRole('radiogroup', { name: /Sua nota/ });
    expect(group).toHaveAttribute('aria-required', 'true');
    const labels = screen.getAllByRole('radio').map((r) => r.getAttribute('value'));
    expect(labels).toEqual(['1', '2', '3', '4', '5']);
    for (const name of ['1 – Péssimo', '2 – Ruim', '3 – Regular', '4 – Bom', '5 – Excelente']) {
      expect(screen.getByRole('radio', { name })).toBeInTheDocument();
    }
    expect(screen.getByTestId('nota-escolhida')).toHaveTextContent('Sem nota');
  });

  it('clique e setas do teclado; o rótulo escolhido aparece em texto', async () => {
    const user = userEvent.setup();
    render(<Harness />);
    await user.click(screen.getByRole('radio', { name: '2 – Ruim' }));
    expect(screen.getByTestId('nota-escolhida')).toHaveTextContent('2 – Ruim');
    await user.keyboard('{ArrowRight}{ArrowRight}');
    expect(screen.getByRole('radio', { name: '4 – Bom' })).toBeChecked();
    expect(screen.getByTestId('nota-escolhida')).toHaveTextContent('4 – Bom');
  });

  it('as estrelas são decorativas', () => {
    const { container } = render(<Harness />);
    const svgs = container.querySelectorAll('svg');
    expect(svgs).toHaveLength(5);
    svgs.forEach((s) => expect(s).toHaveAttribute('aria-hidden', 'true'));
  });
});
