'use client';

import { isApiError, type PublicProfileView, type RelationView } from '@friendly-eats/api';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useId, useRef, useState } from 'react';
import { Dialog } from '@/components/Dialog';
import { IconBlock, IconMute } from '@/components/icons';
import { ContentActions } from '@/components/moderation/ContentActions';
import { api } from '@/lib/api';
import { relationKey, SAFETY_KEY } from '@/lib/moderation';
import { useSession } from '@/lib/queries';

/**
 * Menu "…" do perfil público: Denunciar (RF13), Bloquear e Silenciar (RF19). Bloquear pede confirmação; desfazer fica
 * no mesmo menu e em Configurações → Privacidade. A outra pessoa não é avisada.
 */
export function ProfileSafetyActions({ profile }: { profile: PublicProfileView }) {
  const uid = useId();
  const session = useSession();
  const qc = useQueryClient();
  const logged = !!session.data && session.data.id !== profile.id;
  const relation = useQuery({ queryKey: relationKey(profile.id), queryFn: () => api.safety.relation(profile.id), enabled: logged });
  const [confirm, setConfirm] = useState(false);
  const [status, setStatus] = useState('');
  const cancelRef = useRef<HTMLButtonElement>(null);

  const change = useMutation({
    mutationFn: (op: 'block' | 'unblock' | 'mute' | 'unmute') =>
      op === 'block' ? api.safety.block(profile.id) : op === 'unblock' ? api.safety.unblock(profile.id) : op === 'mute' ? api.safety.mute(profile.id) : api.safety.unmute(profile.id),
    onSuccess: (r: RelationView, op) => {
      qc.setQueryData(relationKey(profile.id), r);
      void qc.invalidateQueries({ queryKey: SAFETY_KEY });
      void qc.invalidateQueries({ queryKey: ['posts'] });
      setConfirm(false);
      setStatus(
        op === 'block'
          ? `${profile.name} foi bloqueado. Vocês deixam de ver um ao outro.`
          : op === 'unblock'
            ? `${profile.name} foi desbloqueado.`
            : op === 'mute'
              ? `${profile.name} foi silenciado: os posts e comentários somem para você.`
              : `${profile.name} voltou a aparecer para você.`,
      );
    },
    onError: (e) => {
      setConfirm(false);
      setStatus(isApiError(e) ? e.detail : 'Não foi possível concluir agora.');
    },
  });

  if (session.data?.id === profile.id) return null;
  const r = relation.data;
  const extra = !logged || !r
    ? []
    : [
        r.muted
          ? { key: 'unmute', label: 'Deixar de silenciar', icon: <IconMute size={16} />, onSelect: () => change.mutate('unmute') }
          : { key: 'mute', label: 'Silenciar', icon: <IconMute size={16} />, onSelect: () => change.mutate('mute') },
        r.blocked
          ? { key: 'unblock', label: 'Desbloquear', icon: <IconBlock size={16} />, onSelect: () => change.mutate('unblock') }
          : { key: 'block', label: 'Bloquear', icon: <IconBlock size={16} />, onSelect: () => setConfirm(true) },
      ];
  return (
    <>
      <ContentActions targetType="PROFILE" targetId={profile.id} context={`o perfil de ${profile.name}`} extra={extra} />
      <p role="status" className="w-full text-sm font-semibold" data-testid="status-relacao">
        {status}
      </p>
      <Dialog open={confirm} onClose={() => setConfirm(false)} role="alertdialog" labelledBy={`${uid}-titulo`} describedBy={`${uid}-desc`} initialFocus={cancelRef}>
        <div className="grid gap-4">
          <h2 id={`${uid}-titulo`} className="text-xl font-bold">
            Bloquear {profile.name}?
          </h2>
          <p id={`${uid}-desc`}>
            Vocês deixam de ver o perfil, os posts e os comentários um do outro, e a pessoa não comenta nos seus posts. Ela não é avisada. As avaliações de
            lugares continuam públicas. Dá para desbloquear quando quiser.
          </p>
          <div className="flex flex-wrap gap-2">
            <button type="button" className="fe-btn fe-btn-primary" onClick={() => change.mutate('block')} disabled={change.isPending}>
              <IconBlock size={18} />
              Bloquear
            </button>
            <button ref={cancelRef} type="button" className="fe-btn" onClick={() => setConfirm(false)}>
              Cancelar
            </button>
          </div>
        </div>
      </Dialog>
    </>
  );
}
