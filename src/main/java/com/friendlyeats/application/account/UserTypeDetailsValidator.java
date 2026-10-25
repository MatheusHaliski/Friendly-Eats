package com.friendlyeats.application.account;

import com.friendlyeats.domain.model.CategoryVerification;
import com.friendlyeats.domain.model.Cnpj;
import com.friendlyeats.domain.model.UserCategoryEnum;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Campos específicos de cada um dos 7 tipos de conta, guardados em {@code type_details}. Só as chaves listadas são
 * aceitas (minimização: nada de campo livre que vire depósito de dado pessoal). Chaves "privadas" nunca aparecem no
 * perfil público.
 */
public final class UserTypeDetailsValidator {

    private static final Pattern URL = Pattern.compile("^https?://[^\\s/$.?#].[^\\s]{1,200}$");
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    /** Subclasse CNAE no formato da Receita, ex.: 5611-2/01 (restaurantes e similares). */
    private static final Pattern CNAE = Pattern.compile("^\\d{4}-\\d/\\d{2}$");
    private static final int MAX_TEXT = 200;

    /** Campos que só o titular e a moderação veem. */
    public static final Set<String> PRIVATE_KEYS = Set.of("contactName", "contactEmail",
            "representativeName", "representativeRole", "representativeEmail", "professionalRegistration",
            "guardianEmail");

    record Rule(Set<String> required, Set<String> optional, Map<String, Set<String>> choices) {
    }

    private static final Map<UserCategoryEnum, Rule> RULES = Map.of(
            UserCategoryEnum.COMENSAL, new Rule(Set.of(),
                    Set.of("city", "state", "website", "guardianEmail"), Map.of()),
            UserCategoryEnum.CRIADOR, new Rule(Set.of("format", "channelName", "website"),
                    Set.of("cnpj", "city", "state"),
                    Map.of("format", Set.of("CRITICO", "BLOG", "VIDEO", "PODCAST", "GUIA"))),
            UserCategoryEnum.CHEF, new Rule(Set.of("role"),
                    Set.of("workplace", "professionalRegistration", "website", "city", "state"),
                    Map.of("role", Set.of("CHEF", "COZINHEIRO", "CONFEITEIRO", "PADEIRO", "BARISTA", "SOMMELIER",
                            "BARTENDER", "NUTRICIONISTA"))),
            UserCategoryEnum.ESTABELECIMENTO, new Rule(Set.of("cnpj", "legalName", "tradeName", "cnae", "city", "state",
                    "representativeName", "representativeRole", "representativeEmail"),
                    Set.of("website"), Map.of()),
            UserCategoryEnum.VAREJO, new Rule(Set.of("segment", "cnpj", "legalName", "cnae",
                    "representativeName", "representativeRole", "representativeEmail"),
                    Set.of("tradeName", "website", "city", "state"),
                    Map.of("segment", Set.of("MERCADO", "EMPORIO", "INDUSTRIA", "DISTRIBUIDOR", "PRODUTOR"))),
            UserCategoryEnum.COMUNIDADE, new Rule(Set.of("communityType", "city", "state", "contactName", "contactEmail"),
                    Set.of("cnpj", "website"),
                    Map.of("communityType", Set.of("CONFRARIA", "CLUBE", "COLETIVO", "FEIRA", "GRUPO_INFORMAL"))),
            UserCategoryEnum.OFICIAL, new Rule(Set.of("agency", "cnpj", "legalName", "website",
                    "representativeName", "representativeRole", "representativeEmail"),
                    Set.of("city", "state"),
                    Map.of("agency", Set.of("ANVISA", "VIGILANCIA_ESTADUAL", "VIGILANCIA_MUNICIPAL", "PROCON",
                            "MINISTERIO_AGRICULTURA", "OUTRO_ORGAO"))));

    private UserTypeDetailsValidator() {
    }

    /** Valida e normaliza; lança {@link AccountExceptions.Invalid} com todos os problemas de uma vez. */
    public static Map<String, Object> validate(UserCategoryEnum type, Map<String, Object> raw) {
        Rule rule = RULES.get(type);
        Map<String, Object> in = raw == null ? Map.of() : raw;
        List<String> errors = new ArrayList<>();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : in.entrySet()) {
            if (!rule.required().contains(e.getKey()) && !rule.optional().contains(e.getKey())) {
                errors.add("Campo \"" + e.getKey() + "\" não se aplica à categoria " + type.getLabel() + ".");
                continue;
            }
            if (e.getValue() == null || e.getValue().toString().isBlank()) {
                continue;
            }
            String v = e.getValue().toString().strip();
            if (v.length() > MAX_TEXT) {
                errors.add("Campo \"" + e.getKey() + "\" passa de " + MAX_TEXT + " caracteres.");
                continue;
            }
            out.put(e.getKey(), v);
        }
        for (String req : rule.required()) {
            if (!out.containsKey(req)) {
                errors.add("Preencha o campo \"" + req + "\" da categoria " + type.getLabel() + ".");
            }
        }
        rule.choices().forEach((key, allowed) -> {
            if (out.containsKey(key) && !allowed.contains(out.get(key).toString())) {
                errors.add("Valor inválido para \"" + key + "\": use " + String.join(", ", allowed) + ".");
            }
        });
        out.computeIfPresent("cnpj", (k, v) -> Cnpj.normalize(v.toString()));
        if (out.containsKey("cnpj") && !Cnpj.isValid(out.get("cnpj").toString())) {
            errors.add("CNPJ inválido. Confira os 14 caracteres (o CNPJ pode ter letras desde 2026).");
        }
        for (String key : List.of("website")) {
            if (out.containsKey(key) && !URL.matcher(out.get(key).toString()).matches()) {
                errors.add("Informe o site completo, começando com https://");
            }
        }
        for (String key : List.of("contactEmail", "representativeEmail", "guardianEmail")) {
            if (out.containsKey(key) && !EMAIL.matcher(out.get(key).toString()).matches()) {
                errors.add("E-mail inválido em \"" + key + "\".");
            }
        }
        if (out.containsKey("cnae") && !CNAE.matcher(out.get("cnae").toString()).matches()) {
            errors.add("CNAE no formato da Receita, ex.: 5611-2/01.");
        }
        if (!errors.isEmpty()) {
            throw new AccountExceptions.Invalid(errors);
        }
        return out;
    }

    /** Estabelecimento, varejo e oficial publicam como organização só depois de a moderação conferir o selo. */
    public static CategoryVerification initialVerification(UserCategoryEnum type, Map<String, Object> details) {
        return type.needsSeal() ? CategoryVerification.PENDING : CategoryVerification.NOT_REQUIRED;
    }

    /** Versão pública: sem as chaves privadas. */
    public static Map<String, Object> publicView(Map<String, Object> details) {
        Map<String, Object> out = new LinkedHashMap<>(details == null ? Map.of() : details);
        out.keySet().removeAll(PRIVATE_KEYS);
        return out;
    }
}
