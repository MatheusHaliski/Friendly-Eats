'use client';

import type { ReportTargetType } from '@friendly-eats/api';
import { useState } from 'react';
import { IconReport } from '@/components/icons';
import { ActionsMenu, type MenuAction } from './ActionsMenu';
import { ReportDialog } from './ReportDialog';

/**
 * Menu "…" de um conteúdo com "Denunciar" (RF13) e ações extras (bloquear e silenciar no perfil, contestar na
 * avaliação). {@code context}: "o post", "o comentário de Ana"…
 */
export function ContentActions({
  targetType,
  targetId,
  context,
  extra = [],
  canReport = true,
}: {
  targetType: ReportTargetType;
  targetId: string | number;
  context: string;
  extra?: MenuAction[];
  canReport?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const actions: MenuAction[] = [
    ...extra,
    ...(canReport ? [{ key: 'denunciar', label: 'Denunciar', icon: <IconReport size={16} />, onSelect: () => setOpen(true) }] : []),
  ];
  return (
    <>
      <ActionsMenu label={`Mais ações: ${context}`} actions={actions} />
      {canReport && <ReportDialog open={open} onClose={() => setOpen(false)} targetType={targetType} targetId={targetId} context={context} />}
    </>
  );
}
