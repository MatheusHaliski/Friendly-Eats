# RF11 · Classes

```mermaid
classDiagram
    direction LR
    class Establishment {
        -HoursData openingHours
        -boolean temporarilyClosed
        -LocalDate reopensOn
        -ChargesData charges
        -Instant chargesDeclaredAt
        -Map facets
        -ActionsData actionLinks
        +setOpeningHours(HoursData) void
        +setTemporaryClosure(boolean, LocalDate) void
        +declareCharges(ChargesData, Instant) void
        +setFacets(Map) void
        +setActionLinks(ActionsData) void
    }
    class PlaceDetails {
        <<records gravados em JSON>>
        HoursData
        SpecialDayData
        ChargesData
        CouvertData
        PaymentAdjustmentData
        ActionsData
        ActionLinkData
    }
    class OpeningHours {
        +from(HoursData)$ OpeningHours
        +status(Instant, ZoneId, permanentlyClosed, temporarilyClosed, reopensOn) Status
        +upcoming(LocalDate) List
        +meals() Map
        +dayName(DayOfWeek)$ String
    }
    class Range {
        <<record>>
        +int opens
        +int closes
        +overnight() boolean
        +end() int
        +of(String, String)$ Optional
    }
    class State {
        <<enumeration>>
        OPEN
        CLOSED
        UNKNOWN
        TEMPORARILY_CLOSED
        MAYBE_CLOSED
        PERMANENTLY_CLOSED
    }
    class PlaceTimeZone {
        <<utility>>
        +of(uf, municipalityIbge)$ ZoneId
    }
    class Facet {
        <<enumeration>>
        39 facetas
        +getSection() Section
        +getValues() Values
        +declarable() boolean
        +fixed() boolean
        +appliesTo(EstablishmentGroup) boolean
        +valueLabel(String) String
    }
    class ActionProvider {
        <<enumeration>>
        IFOOD · RAPPI · AIQFOME · NOVENTA_E_NOVE_FOOD
        THEFORK · TAGME · GET_IN · OPENTABLE · WHATSAPP
        +getKind() Kind
        +acceptsHost(String) boolean
    }
    class PlaceDetailsRules {
        <<utility>>
        +hours(HoursDraft, today, errors)$ HoursData
        +temporaryClosure(draft, today, errors)$ Closure
        +charges(ChargesDraft, facets, errors)$ ChargesData
        +facets(Map, EstablishmentGroup, errors)$ Map
        +actions(ActionsDraft, errors)$ ActionsData
    }
    class PlaceDetailsViews {
        <<utility>>
        +openStatus(Establishment, Instant)$ OpenStatusView
        +hours(Establishment, Instant)$ HoursView
        +charges(Establishment)$ ChargesView
        +facets(Establishment)$ List
        +actions(Establishment, Instant)$ List
    }
    class PlaceEditors {
        +canEdit(Establishment, viewerId) boolean
    }
    class EstablishmentService {
        +create(viewerId, EstablishmentDraft) EstablishmentView
        +update(viewerId, id, EstablishmentDraft) EstablishmentView
        -details(draft, group, zone, charges, facets, revalidate, errors) Consumer
    }
    class EstablishmentViews {
        <<records>>
        EstablishmentView
        OpenStatusView
        HoursView
        ChargesView
        FacetSectionView
        ActionView
    }
    EstablishmentService --> PlaceDetailsRules
    EstablishmentService --> PlaceDetailsViews
    EstablishmentService --> PlaceEditors
    EstablishmentService ..> EstablishmentViews : devolve
    PlaceDetailsRules ..> PlaceDetails : produz
    PlaceDetailsRules --> Facet
    PlaceDetailsRules --> ActionProvider
    PlaceDetailsRules --> OpeningHours
    PlaceDetailsViews --> OpeningHours
    PlaceDetailsViews --> PlaceTimeZone
    PlaceDetailsViews --> Facet
    Establishment --> PlaceDetails
    OpeningHours --> Range
    OpeningHours --> State
```

No app web: `PlaceView` usa `OpenStatusBadge`, `ActionButtons`, `HoursSection`, `ChargesSection`, `FacetsSection` e
`PlaceLocation` (com `PlaceMap`); o `PlaceForm` monta o `EstablishmentDraft` com `lib/place-form`
(`buildPlaceDraft`, `valuesFromPlace`, `mapPlaceErrors`).
