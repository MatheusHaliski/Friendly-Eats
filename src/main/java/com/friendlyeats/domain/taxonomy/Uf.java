package com.friendlyeats.domain.taxonomy;

import java.util.Arrays;
import java.util.Optional;

/**
 * Unidades da federação com o código IBGE de 2 dígitos, que também abre o código de 7 dígitos de cada município
 * (3550308 = São Paulo/SP). O município é a chave do local; o CEP não, porque muda.
 */
public enum Uf {
    RO(11), AC(12), AM(13), RR(14), PA(15), AP(16), TO(17),
    MA(21), PI(22), CE(23), RN(24), PB(25), PE(26), AL(27), SE(28), BA(29),
    MG(31), ES(32), RJ(33), SP(35),
    PR(41), SC(42), RS(43),
    MS(50), MT(51), GO(52), DF(53);

    private final int ibgeCode;

    Uf(int ibgeCode) {
        this.ibgeCode = ibgeCode;
    }

    public int getIbgeCode() {
        return ibgeCode;
    }

    /** Código de município do IBGE: 7 dígitos começando pelo código desta UF. */
    public boolean ownsMunicipality(String ibge7) {
        return ibge7 != null && ibge7.matches("^\\d{7}$") && ibge7.startsWith(String.valueOf(ibgeCode));
    }

    public static Optional<Uf> fromCode(String sigla) {
        return Arrays.stream(values()).filter(u -> u.name().equals(sigla)).findFirst();
    }
}
