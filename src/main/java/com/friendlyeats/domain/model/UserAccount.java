package com.friendlyeats.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Conta do Friendly Eats (tabela {@code user_account}; DDL em src/main/resources/db/migration/V1__baseline.sql).
 * Guarda só o necessário para uma rede de comida (LGPD Art. 6º, III).
 */
@Entity
@Table(name = "user_account")
public class UserAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // nome de exibição
    @NotBlank
    @Size(max = 120)
    @Column(name = "name", nullable = false, length = 120)
    private String name;

    // senha: só o hash Argon2id
    @NotBlank
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    // e-mail de login (único, minúsculo)
    @NotBlank
    @Email
    @Column(name = "email", nullable = false, unique = true, length = 254)
    private String email;

    // e-mail de recuperação
    @Email
    @Column(name = "recovery_email", length = 254)
    private String recoveryEmail;


    // país de residência (ISO 3166-1 alpha-2)
    @Size(min = 2, max = 2)
    @Column(name = "country_of_residence", length = 2)
    private String countryOfResidence;

    // UF / estado
    @Column(name = "state_of_residence", length = 80)
    private String stateOfResidence;

    // cidade
    @Column(name = "city_of_residence", length = 120)
    private String cityOfResidence;


    // celular (E.164)
    @Pattern(regexp = "^\\+[1-9]\\d{7,14}$")
    @Column(name = "cellphone_number", length = 20)
    private String cellphoneNumber;

    // gosto: códigos do catálogo de culinárias (docs/TAXONOMIA_FRIENDLY_EATS.md)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "favorite_cuisines")
    private List<String> favoriteCuisines = new ArrayList<>();

    // estilos alimentares: dado sensível (saúde ou religião), só com consentimento SENSITIVE_PROFILE_DATA
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "dietary_styles")
    private List<String> dietaryStyles = new ArrayList<>();


    // data de nascimento: idade mínima e proteções do ECA Digital
    @Past
    @Column(name = "user_date_of_birth")
    private LocalDate userDateOfBirth;



    // foto de perfil: chave do objeto no S3 (avatars/…), nunca o binário
    @Column(name = "photo_object_key", length = 512)
    private String photoObjectKey;

    // tipo de conta
    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "user_type", nullable = false)
    private UserCategoryEnum userType = UserCategoryEnum.COMENSAL;


    // última localização (só com consentimento LOCATION_TRACKING)
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "last_saved_location")
    private GeoLocation lastSavedLocation;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false)
    private AccountStatus accountStatus = AccountStatus.PENDING_VERIFICATION;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    // ---------------------------------------------------------------- perfil público

    /** @usuário público: minúsculas, dígitos, ponto e sublinhado; 3 a 30 caracteres. */
    @Pattern(regexp = "^[a-z0-9._]{3,30}$")
    @Column(name = "handle", length = 30)
    private String handle;

    @Size(max = 150)
    @Column(name = "bio", length = 150)
    private String bio;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pronouns")
    private List<String> pronouns = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "links")
    private List<String> links = new ArrayList<>();

    /** Campos específicos da categoria (§3), validados por {@code UserTypeDetailsValidator}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "type_details")
    private java.util.Map<String, Object> typeDetails = new java.util.LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "category_verification", nullable = false)
    private CategoryVerification categoryVerification = CategoryVerification.NOT_REQUIRED;

    /** Exclusão com carência (RF5): pedido, fim do prazo e status para o qual a conta volta se reativada. */
    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "deletion_scheduled_for")
    private Instant deletionScheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_before_deletion")
    private AccountStatus statusBeforeDeletion;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected UserAccount() {
        // JPA
    }

    public UserAccount(String name, String email, String passwordHash, UserCategoryEnum userType) {
        this.name = name;
        this.email = normalizeEmail(email);
        this.passwordHash = passwordHash;
        this.userType = userType;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // ---------------------------------------------------------------- ciclo de vida

    public void activate() {
        transitionTo(AccountStatus.ACTIVE);
    }

    public void suspend() {
        transitionTo(AccountStatus.SUSPENDED);
    }

    /** Código de e-mail confirmado (RF1): conta pendente vira ativa; trocar e-mail depois só registra a data. */
    public void markEmailVerified(Instant when) {
        this.emailVerifiedAt = when;
        if (accountStatus == AccountStatus.PENDING_VERIFICATION) {
            transitionTo(AccountStatus.ACTIVE);
        }
    }

    /**
     * "Excluir conta" confirmado (RF5): a conta some do app na hora e fica {@code grace} esperando.
     * Entrar de novo dentro do prazo permite {@link #reactivate(Instant)}; depois dele, a saga elimina.
     */
    public Instant requestDeletion(Instant now, Duration grace) {
        AccountStatus previous = accountStatus;
        transitionTo(AccountStatus.PENDING_DELETION);
        this.statusBeforeDeletion = previous;
        this.deletionRequestedAt = now;
        this.deletionScheduledFor = now.plus(grace);
        return deletionScheduledFor;
    }

    public boolean canReactivate(Instant now) {
        return accountStatus == AccountStatus.PENDING_DELETION && now.isBefore(deletionScheduledFor);
    }

    /** Desfaz o pedido de exclusão dentro do prazo, devolvendo o status anterior (ativa, pendente ou suspensa). */
    public void reactivate(Instant now) {
        if (!canReactivate(now)) {
            throw new IllegalStateException("O prazo para reativar esta conta terminou.");
        }
        transitionTo(statusBeforeDeletion == null ? AccountStatus.ACTIVE : statusBeforeDeletion);
        this.statusBeforeDeletion = null;
        this.deletionRequestedAt = null;
        this.deletionScheduledFor = null;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Eliminação a pedido do titular (LGPD Art. 18, VI). Mantém apenas o id para
     * integridade referencial; todos os dados pessoais são removidos ou substituídos.
     * A remoção da foto no S3 e da timeline no Cassandra é feita pelo serviço de aplicação.
     */
    public void anonymizeForLgpdDeletion(String anonymizedPasswordHash) {
        transitionTo(AccountStatus.ANONYMOUS_LGPD_DELETED);
        this.name = "Usuário removido";
        this.email = "deleted+" + id + "@anon.friendlyeats.invalid";
        this.passwordHash = anonymizedPasswordHash;
        this.recoveryEmail = null;
        this.countryOfResidence = null;
        this.stateOfResidence = null;
        this.cityOfResidence = null;
        this.cellphoneNumber = null;
        this.favoriteCuisines = new ArrayList<>();
        this.dietaryStyles = new ArrayList<>();
        this.userDateOfBirth = null;
        this.photoObjectKey = null;
        this.handle = null;
        this.bio = null;
        this.pronouns = new ArrayList<>();
        this.links = new ArrayList<>();
        this.typeDetails = new java.util.LinkedHashMap<>();
        this.lastSavedLocation = null;
        this.deletedAt = Instant.now();
    }

    private void transitionTo(AccountStatus target) {
        if (!accountStatus.canTransitionTo(target)) {
            throw new IllegalStateException("Transição inválida: " + accountStatus + " -> " + target);
        }
        this.accountStatus = target;
    }

    // ---------------------------------------------------------------- getters / setters

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    public String getRecoveryEmail() {
        return recoveryEmail;
    }

    public void setRecoveryEmail(String recoveryEmail) {
        this.recoveryEmail = recoveryEmail;
    }

    public String getCountryOfResidence() {
        return countryOfResidence;
    }

    public void setCountryOfResidence(String countryOfResidence) {
        this.countryOfResidence = countryOfResidence;
    }

    public String getStateOfResidence() {
        return stateOfResidence;
    }

    public void setStateOfResidence(String stateOfResidence) {
        this.stateOfResidence = stateOfResidence;
    }

    public String getCityOfResidence() {
        return cityOfResidence;
    }

    public void setCityOfResidence(String cityOfResidence) {
        this.cityOfResidence = cityOfResidence;
    }

    public String getCellphoneNumber() {
        return cellphoneNumber;
    }

    public void setCellphoneNumber(String cellphoneNumber) {
        this.cellphoneNumber = cellphoneNumber;
    }

    public List<String> getFavoriteCuisines() {
        return favoriteCuisines;
    }

    public void setFavoriteCuisines(List<String> favoriteCuisines) {
        this.favoriteCuisines = favoriteCuisines;
    }

    public List<String> getDietaryStyles() {
        return dietaryStyles;
    }

    public void setDietaryStyles(List<String> dietaryStyles) {
        this.dietaryStyles = dietaryStyles;
    }

    public LocalDate getUserDateOfBirth() {
        return userDateOfBirth;
    }

    public void setUserDateOfBirth(LocalDate userDateOfBirth) {
        this.userDateOfBirth = userDateOfBirth;
    }

    public String getPhotoObjectKey() {
        return photoObjectKey;
    }

    public void setPhotoObjectKey(String photoObjectKey) {
        this.photoObjectKey = photoObjectKey;
    }

    public UserCategoryEnum getUserType() {
        return userType;
    }

    public void setUserType(UserCategoryEnum userType) {
        this.userType = userType;
    }

    public GeoLocation getLastSavedLocation() {
        return lastSavedLocation;
    }

    public void setLastSavedLocation(GeoLocation lastSavedLocation) {
        this.lastSavedLocation = lastSavedLocation;
    }

    public AccountStatus getAccountStatus() {
        return accountStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public String getHandle() {
        return handle;
    }

    public void setHandle(String handle) {
        this.handle = handle == null ? null : handle.strip().toLowerCase(java.util.Locale.ROOT);
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    public List<String> getPronouns() {
        return pronouns;
    }

    public void setPronouns(List<String> pronouns) {
        this.pronouns = pronouns == null ? new ArrayList<>() : new ArrayList<>(pronouns);
    }

    public List<String> getLinks() {
        return links;
    }

    public void setLinks(List<String> links) {
        this.links = links == null ? new ArrayList<>() : new ArrayList<>(links);
    }

    public java.util.Map<String, Object> getTypeDetails() {
        return typeDetails;
    }

    public void setTypeDetails(java.util.Map<String, Object> typeDetails) {
        this.typeDetails = typeDetails == null ? new java.util.LinkedHashMap<>() : new java.util.LinkedHashMap<>(typeDetails);
    }

    public CategoryVerification getCategoryVerification() {
        return categoryVerification;
    }

    public void setCategoryVerification(CategoryVerification categoryVerification) {
        this.categoryVerification = categoryVerification;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public Instant getDeletionRequestedAt() {
        return deletionRequestedAt;
    }

    public Instant getDeletionScheduledFor() {
        return deletionScheduledFor;
    }

    public AccountStatus getStatusBeforeDeletion() {
        return statusBeforeDeletion;
    }

    public Long getVersion() {
        return version;
    }
}
