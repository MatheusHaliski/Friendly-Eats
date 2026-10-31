# RF5 · Sequência

## Excluir e reativar

```mermaid
sequenceDiagram
    autonumber
    actor T as Titular
    participant APP as App (modal)
    participant API as MeController
    participant AC as AccountService
    participant VK as Valkey (sessões)
    participant DB as PostgreSQL

    T->>APP: Seus dados → Excluir conta
    APP-->>T: modal: consequências, prazo de 30 dias, baixar dados, senha
    T->>APP: marca "entendo" + senha (+ código 2FA)
    APP->>API: POST /api/v1/me/deletion {password, code, confirm: true}
    API->>API: modo sudo (senha + 2FA)
    API->>AC: requestDeletion
    AC->>DB: PENDING_DELETION · deletion_scheduled_for = agora + 30 dias
    AC->>VK: apaga todas as sessões da conta (inclusive esta)
    API-->>APP: 202 {deletionScheduledFor, graceDays: 30}
    T->>API: (dia 12) POST /api/v1/auth/login
    API-->>APP: {status: REACTIVATION_AVAILABLE, deletionScheduledFor}
    T->>API: POST /api/v1/auth/reactivate
    API->>DB: volta ao status anterior · limpa as datas
    API-->>APP: 200 AUTHENTICATED
```

## Eliminação depois do prazo

```mermaid
sequenceDiagram
    autonumber
    participant JOB as AccountDeletionJob (03:15)
    participant AC as AccountService.eliminate
    participant DB as PostgreSQL
    participant VK as Valkey
    participant S3 as Bucket S3

    JOB->>AC: contas PENDING_DELETION com prazo vencido (uma transação cada)
    AC->>DB: DELETE review WHERE author_id = conta (review_revision por ON DELETE CASCADE)
    AC->>DB: PostContentEraser: curtidas e salvos apagados, contadores corrigidos, comentários sem texto
    AC->>DB: compartilhamentos sem a conta, posts REMOVED sem conteúdo (título Conteúdo removido)
    AC->>DB: DELETE establishment WHERE owner_id = conta (review dos lugares por ON DELETE CASCADE)
    AC->>DB: apaga 2FA, códigos de recuperação e preferências
    AC->>DB: apaga bloqueios e silenciados nos dois sentidos, palavras filtradas e o filtro padrão (RF19)
    AC->>DB: apaga verificação de idade, contestações e vínculos como menor · encerra os que supervisiona (RF17)
    AC->>DB: apaga os pedidos de exportação (RF18) · access_log fica 6 meses (Marco Civil)
    AC->>DB: anonymizeForLgpdDeletion · ANONYMOUS_LGPD_DELETED
    AC->>VK: revoga as sessões
    AC->>DB: COMMIT
    AC->>S3: DELETE avatars/…, posts/… e exports/… (MediaJanitor, depois do commit)
    AC->>VK: ZREM dos posts no ranking em alta (depois do commit)
    Note over AC,S3: ⏳ Cassandra, destruição da DEK
```
