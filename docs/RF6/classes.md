# RF6 · Classes

```mermaid
classDiagram
    direction LR
    class Establishment {
        -Long id
        -Long ownerId
        -String name
        -EstablishmentType primaryType
        -List secondaryTypes
        -EstablishmentGroup typeGroup
        -Integer priceMinCents
        -Integer priceMaxCents
        -boolean priceNotApplicable
        -String uf
        -String municipalityIbge
        -String city
        -String neighborhood
        -String street
        -String cep
        -Double latitude
        -Double longitude
        -GeoPrecision geoPrecision
        -AddressVisibility addressVisibility
        -Status status
        +setTypes(EstablishmentType, List) void
        +setLocation(...) void
        +setDeclaredPrice(Integer, Integer, boolean) void
        +isAlcoholFocused() boolean
    }
    class Status {
        <<enumeration>>
        ACTIVE
        CLOSED
        REMOVED
    }
    class EstablishmentType {
        <<enumeration>>
        48 tipos
        +getGroup() EstablishmentGroup
        +isAlcoholFocused() boolean
        +getTypicalCnae() List
    }
    class EstablishmentGroup {
        <<enumeration>>
        RESTAURANTE
        RAPIDO
        BAR
        CAFE_DOCE
        PRODUCAO
        VAREJO
        POLO
    }
    class Uf {
        <<enumeration>>
        27 UFs com código IBGE
        +ownsMunicipality(String) boolean
    }
    class GeoPrecision {
        <<enumeration>>
        GEO_PIN_CONFIRMED
        GEO_ROOFTOP
        GEO_STREET
        GEO_CEP_CENTROID
        GEO_BAIRRO_CENTROID
        GEO_MUNICIPIO_CENTROID
    }
    class AddressVisibility {
        <<enumeration>>
        ADDR_PUBLIC_FULL
        ADDR_PUBLIC_BAIRRO
        ADDR_HIDDEN
    }
    class PriceLevel {
        <<enumeration>>
        PRICE_UNKNOWN
        PRICE_1
        PRICE_2
        PRICE_3
        PRICE_4
        PRICE_NA
        +getGlyph() String
    }
    class EstablishmentRules {
        <<utility>>
        +types(UserCategoryEnum, informalFormat, primary, secondary, errors)$ Types
        +location(LocationDraft, homeKitchen, errors)$ Location
        +price(EstablishmentGroup, PriceDraft, errors)$ Price
        +publicCoordinate(double)$ double
    }
    class PriceScale {
        +levelOf(int cents) PriceLevel
        +rangeLabel(PriceLevel) String
        +thresholdsCents() int[]
    }
    class EstablishmentService {
        +create(viewerId, EstablishmentDraft) EstablishmentView
        +update(viewerId, id, EstablishmentDraft) EstablishmentView
        +mine(viewerId) List
        +get(Optional viewerId, id) EstablishmentView
        +requireVisible(id, Optional viewer) Establishment
    }
    class EstablishmentDrafts {
        <<records>>
        EstablishmentDraft
        LocationDraft
        PriceDraft
    }
    class EstablishmentViews {
        <<records>>
        EstablishmentView
        LocationView
        PriceView
        OwnerView
    }
    class EstablishmentRepository {
        <<repository>>
        +findByOwnerIdOrderByCreatedAtAsc(Long) List
        +countByOwnerId(Long) long
        +deleteAllOwnedBy(Long) int
    }
    EstablishmentService --> EstablishmentRules
    EstablishmentService --> PriceScale
    EstablishmentService --> EstablishmentRepository
    EstablishmentService ..> EstablishmentDrafts : recebe
    EstablishmentService ..> EstablishmentViews : devolve
    EstablishmentRules --> Uf
    EstablishmentRepository ..> Establishment
    Establishment --> Status
    Establishment --> EstablishmentType
    Establishment --> EstablishmentGroup
    Establishment --> GeoPrecision
    Establishment --> AddressVisibility
    EstablishmentType --> EstablishmentGroup
    PriceScale ..> PriceLevel
```

A classificação do mesmo `Establishment` (culinária, formato, abordagem e estilos) está no [RF7](../RF7/classes.md),
e a nota agregada que a página mostra, no [RF8](../RF8/classes.md).
