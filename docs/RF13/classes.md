# RF13 · Classes

## Domínio

```mermaid
classDiagram
    direction LR
    class ReportReason {
        <<enumeration>>
        CRIANCA_EM_RISCO
        DESINFORMACAO_SANITARIA
        ASSEDIO
        ODIO
        CONTEUDO_SEXUAL
        DADO_PESSOAL
        GOLPE_FRAUDE
        AVALIACAO_FALSA
        CONFLITO_INTERESSE
        OFENSA_OU_CRIME
        DIREITO_AUTORAL
        SPAM
        FORA_DO_TEMA
        OUTRO
        +priority() ModerationPriority
        +appliesTo(TargetType) boolean
        +ownerContest() boolean
    }
    class ModerationPriority {
        <<enumeration>>
        CRITICA 24h
        URGENTE 24h
        ALTA 72h
        NORMAL 7d
        BAIXA 14d
        +sla() Duration
        +lowered() ModerationPriority
    }
    class TargetType {
        <<enumeration>>
        POST
        COMMENT
        REVIEW
        PROFILE
        ESTABLISHMENT
        +actions() Set
        +preventiveHide() boolean
    }
    class ModerationAction {
        <<enumeration>>
        KEEP
        HIDE
        REMOVE
        WARN
        +takesDown() boolean
    }
    class CaseStatus {
        <<enumeration>>
        OPEN
        DECIDED
        IN_APPEAL
        FINAL
    }
    class ReportSource {
        <<enumeration>>
        USER
        PUBLIC_FORM
        OWNER_CONTEST
        SYSTEM
    }
    class ContentModeration {
        <<enumeration>>
        PREVENTIVE
        HIDDEN
        REMOVED
    }
    class ReviewSignal {
        <<enumeration>>
        BURST_NEW_ACCOUNTS
        SAME_ORIGIN
        INCENTIVE_DECLARED
        CONFLICT_DECLARED
        +suspicious() boolean
    }
    class AlertKind {
        <<enumeration>>
        AVALIACAO_PAGA
        ATIVIDADE_SUSPEITA
        ATENCAO_PUBLICA
        +getDefaultMessage() String
    }
    class Review {
        -String originHash
        +declare(Disclosure) void
        +putUnderReview() void
        +removeByModeration() void
        +restoreByModeration() void
    }
    ReportReason --> ModerationPriority
    ReportReason --> TargetType
    TargetType --> ModerationAction
    Review ..> ReviewSignal : sinais
```

## Serviços, persistência e views

```mermaid
classDiagram
    direction LR
    class ReportService {
        +reasons() List~ReasonView~
        +report(viewerId, ReportRequest) ReceiptView
        +publicReport(PublicReportRequest, ipHash) ReceiptView
        +contest(viewerId, placeId, reviewId, ContestRequest) ReceiptView
        +mine(viewerId) List~MyReportView~
    }
    class ModerationService {
        +queue(moderatorId, status, page, size) CasePageView
        +detail(moderatorId, caseId) CaseDetailView
        +decide(moderatorId, caseId, DecisionRequest) CaseDetailView
        +appeal(viewerId, caseId, AppealRequest) AppealView
        +decideAppeal(moderatorId, caseId, AppealDecisionRequest) CaseDetailView
        +recordAuthorityReferral(moderatorId, caseId, ref) CaseDetailView
        +myDecisions(viewerId) List~MyDecisionView~
        +stats(moderatorId, days) StatsView
    }
    class ReviewIntegrityService {
        +requireNotFrozen(placeId) void
        +afterNewReview(Review, Disclosure) void
        +view(viewerId, placeId) IntegrityView
        +createAlert(moderatorId, placeId, AlertRequest) AlertView
        +liftAlert(moderatorId, placeId, alertId) AlertView
    }
    class ModerationTargets {
        +forReport(TargetType, id, viewerId) Target
        +current(TargetType, key) Optional~Target~
        +preventive(TargetType, key) void
        +apply(TargetType, key, ModerationAction) void
    }
    class ModerationRoles {
        +isModerator(userId) boolean
        +requireModerator(userId) void
        +grant(userId, by) boolean
    }
    class ModerationPolicy {
        +reportsPerHour() int
        +appealWindow() Duration
        +lowTrust(dismissed, resolved) boolean
        +signalsEnabled() boolean
    }
    class ModerationNotifier {
        <<interface>>
        +decisionToAuthor(...)
        +outcomeToReporter(...)
        +appealResult(...)
    }
    class ModerationRepository {
        +openCase(...) long
        +addReportToCase(...) void
        +insertReport(...) long
        +insertDecision(...) long
        +insertAppeal(...) long
        +queue(status, offset, limit) List~CaseRow~
    }
    class IntegrityRepository {
        +recentFromNewAccounts(...) List~Long~
        +sameOrigin(...) List~Long~
        +addSignal(...) boolean
        +frozenUntil(placeId, now) Optional~Instant~
    }
    class UserRoleRepository {
        +rolesOf(userId) List~String~
        +grant(userId, role, by) boolean
    }
    ReportService --> ModerationTargets
    ReportService --> ModerationRepository
    ReportService --> ModerationPolicy
    ModerationService --> ModerationTargets
    ModerationService --> ModerationRepository
    ModerationService --> ModerationRoles
    ModerationService --> ModerationNotifier
    ModerationService --> ReviewIntegrityService
    ReviewIntegrityService --> IntegrityRepository
    ReviewIntegrityService --> ModerationRepository
    ReviewIntegrityService --> ModerationTargets
    ModerationRoles --> UserRoleRepository
```

As views (`ModerationViews`) são records: `ReceiptView`, `ReasonView`, `TargetView`, `CaseSummaryView`,
`CaseDetailView`, `ReportView`, `DecisionView`, `AppealView`, `SignalView`, `MyReportView`, `MyDecisionView`,
`AlertView`, `IntegrityView`, `StatsView` e `RolesView`.
