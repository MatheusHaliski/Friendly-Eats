package com.friendlyeats.domain.model;

import java.time.ZoneId;
import java.util.Map;

/**
 * Fuso do lugar (RF11) pela UF e, nas exceções, pelo município (código IBGE). O "aberto agora" é calculado neste
 * fuso, não no de quem olha. O Brasil não tem horário de verão desde 2019, mas os fusos IANA guardam o histórico.
 *
 * <p>Fernando de Noronha (PE) fica em UTC−2. ⏳ Os municípios do oeste do Amazonas em UTC−5 (Lei 12.876/2013)
 * ainda caem em {@code America/Manaus}: falta a lista de códigos IBGE conferida.
 */
public final class PlaceTimeZone {

    public static final ZoneId DEFAULT = ZoneId.of("America/Sao_Paulo");
    /** Fernando de Noronha (PE). */
    static final String NORONHA_IBGE = "2605459";

    private static final Map<String, String> BY_UF = Map.ofEntries(
            Map.entry("AC", "America/Rio_Branco"),
            Map.entry("AM", "America/Manaus"),
            Map.entry("RR", "America/Boa_Vista"),
            Map.entry("RO", "America/Porto_Velho"),
            Map.entry("MT", "America/Cuiaba"),
            Map.entry("MS", "America/Campo_Grande"),
            Map.entry("PA", "America/Belem"),
            Map.entry("AP", "America/Belem"),
            Map.entry("TO", "America/Araguaina"),
            Map.entry("MA", "America/Fortaleza"),
            Map.entry("PI", "America/Fortaleza"),
            Map.entry("CE", "America/Fortaleza"),
            Map.entry("RN", "America/Fortaleza"),
            Map.entry("PB", "America/Fortaleza"),
            Map.entry("PE", "America/Recife"),
            Map.entry("AL", "America/Maceio"),
            Map.entry("SE", "America/Maceio"),
            Map.entry("BA", "America/Bahia"));

    private PlaceTimeZone() {
    }

    /** UF desconhecida ou ausente cai no horário de Brasília. */
    public static ZoneId of(String uf, String municipalityIbge) {
        if (NORONHA_IBGE.equals(municipalityIbge)) {
            return ZoneId.of("America/Noronha");
        }
        String zone = uf == null ? null : BY_UF.get(uf);
        return zone == null ? DEFAULT : ZoneId.of(zone);
    }
}
