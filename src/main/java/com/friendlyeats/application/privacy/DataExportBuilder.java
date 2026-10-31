package com.friendlyeats.application.privacy;

import com.friendlyeats.application.account.PreferencesService;
import com.friendlyeats.application.account.ProfileService;
import com.friendlyeats.application.age.AgeVerificationService;
import com.friendlyeats.application.common.PublicLinks;
import com.friendlyeats.application.consent.ConsentService;
import com.friendlyeats.application.guardian.GuardianService;
import com.friendlyeats.security.SessionService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Monta o ZIP da exportação (RF18; LGPD, art. 18, V, e art. 19, II e §3º): um JSON por assunto, legível por
 * máquina, e um LEIA-ME. Fotos vão como links, não como arquivos. Ficam de fora os segredos (hash da senha, segredo
 * do 2FA, códigos de recuperação), os HMAC de IP e os registros de acesso do Marco Civil, que só saem por ordem
 * judicial (⚖️ a confirmar se o titular pode pedi-los). Da moderação (RF13) e da segurança (RF19) entram só os atos
 * do titular e as decisões sobre o conteúdo dele: nunca quem denunciou, o moderador, quem bloqueou o titular
 * (bloqueio é sigiloso) nem o caso encaminhado às autoridades por indício de abuso sexual infantil (⚖️ sigilo da
 * investigação; a confirmar com o jurídico).
 */
@Component
public class DataExportBuilder {

    static final String README = """
            Friendly Eats · cópia dos seus dados (LGPD, art. 18, V, e art. 19)

            Cada arquivo .json traz um assunto:
              conta.json             dados da conta e situação
              perfil.json            perfil, gosto e campos do tipo de conta
              preferencias.json      configurações de exibição, privacidade e notificações
              consentimentos.json    estado atual e o histórico de cada consentimento
              termos.json            versões dos Termos de Uso aceitas, com data e canal
              sessoes.json           aparelhos conectados agora
              posts.json             posts publicados, com o link de cada foto
              comentarios.json       comentários que você escreveu
              curtidas.json          posts que você curtiu
              salvos.json            posts que você salvou
              compartilhamentos.json posts que você compartilhou
              avaliacoes.json        avaliações de estabelecimentos e as versões anteriores
              estabelecimentos.json  páginas de estabelecimento da sua conta
              idade.json             verificação de idade (resultado, método e data; nunca o documento)
              responsavel.json       vínculo com o responsável (como menor ou como responsável) e o histórico
              seguranca.json         contas que você bloqueou ou silenciou e as palavras que você filtrou
              moderacao.json         denúncias que você fez, decisões sobre o seu conteúdo e os seus recursos
              exportacoes.json       pedidos de exportação

            Não entram: hash da senha, segredo do app autenticador, códigos de recuperação e os registros de acesso
            (data, hora e IP), guardados em sigilo por 6 meses (Marco Civil, art. 15) e entregues só por ordem judicial.
            Também não entram quem denunciou o seu conteúdo, quem decidiu na moderação e quem bloqueou você.
            Dúvidas: veja o encarregado de dados na página /privacidade.
            """;

    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    private final ProfileService profiles;
    private final PreferencesService preferences;
    private final ConsentService consents;
    private final SessionService sessions;
    private final AgeVerificationService ages;
    private final GuardianService guardians;
    private final PublicLinks links;

    public DataExportBuilder(JdbcTemplate jdbc, JsonMapper json, ProfileService profiles,
                             PreferencesService preferences, ConsentService consents, SessionService sessions,
                             AgeVerificationService ages, GuardianService guardians, PublicLinks links) {
        this.jdbc = jdbc;
        this.json = json;
        this.profiles = profiles;
        this.preferences = preferences;
        this.consents = consents;
        this.sessions = sessions;
        this.ages = ages;
        this.guardians = guardians;
        this.links = links;
    }

    /** Os assuntos, na ordem do LEIA-ME. */
    @Transactional
    public Map<String, JsonNode> sections(long userId) {
        Map<String, JsonNode> out = new LinkedHashMap<>();
        out.put("conta.json", one("""
                SELECT to_jsonb(u) - ARRAY['password_hash','last_saved_point','dietary_styles','favorite_cuisines',
                                           'type_details','last_saved_location','version']
                FROM user_account u WHERE id = ?
                """, userId));
        out.put("perfil.json", json.valueToTree(profiles.get(userId)));
        out.put("preferencias.json", json.valueToTree(preferences.get(userId)));
        ObjectNode consent = json.createObjectNode();
        consent.set("atual", json.valueToTree(consents.list(userId).items()));
        consent.set("historico", array("""
                SELECT to_jsonb(c) - 'ip_hash' FROM consent_event c WHERE user_id = ? ORDER BY occurred_at, id
                """, userId));
        out.put("consentimentos.json", consent);
        out.put("termos.json", array("""
                SELECT to_jsonb(t) - 'ip_hash' FROM terms_acceptance t WHERE user_id = ? ORDER BY accepted_at, id
                """, userId));
        out.put("sessoes.json", json.valueToTree(sessions.list(userId, null)));
        ArrayNode posts = array("SELECT to_jsonb(p) FROM post p WHERE author_id = ? ORDER BY created_at, id", userId);
        posts.forEach(p -> {
            if (p instanceof ObjectNode o && o.hasNonNull("media_object_key")) {
                o.put("media_url", links.mediaUrl(o.get("media_object_key").asString()));
            }
        });
        out.put("posts.json", posts);
        out.put("comentarios.json", array("""
                SELECT to_jsonb(c) FROM post_comment c WHERE author_id = ? ORDER BY created_at, id
                """, userId));
        out.put("curtidas.json", array("""
                SELECT to_jsonb(r) FROM post_reaction r WHERE user_id = ? AND kind = 'LIKE' ORDER BY created_at
                """, userId));
        out.put("salvos.json", array("""
                SELECT to_jsonb(r) FROM post_reaction r WHERE user_id = ? AND kind = 'SAVE' ORDER BY created_at
                """, userId));
        out.put("compartilhamentos.json", array("""
                SELECT to_jsonb(s) FROM post_share s WHERE user_id = ? ORDER BY created_at, id
                """, userId));
        ObjectNode reviews = json.createObjectNode();
        reviews.set("avaliacoes", array("""
                SELECT to_jsonb(r) - 'origin_hash' FROM review r WHERE author_id = ? ORDER BY created_at, id
                """, userId));
        reviews.set("versoesAnteriores", array("""
                SELECT to_jsonb(v) FROM review_revision v JOIN review r ON r.id = v.review_id
                WHERE r.author_id = ? ORDER BY v.id
                """, userId));
        out.put("avaliacoes.json", reviews);
        out.put("estabelecimentos.json", array("""
                SELECT to_jsonb(e) - ARRAY['point','cuisines_all','formats_all']
                FROM establishment e WHERE owner_id = ? ORDER BY created_at, id
                """, userId));
        ObjectNode age = json.createObjectNode();
        age.set("atual", one("SELECT to_jsonb(a) FROM age_verification a WHERE user_id = ?", userId));
        age.set("historico", json.valueToTree(ages.history(userId)));
        age.set("contestacoes", array("""
                SELECT to_jsonb(a) - 'decided_by' FROM age_appeal a WHERE user_id = ? ORDER BY created_at, id
                """, userId));
        out.put("idade.json", age);
        ObjectNode guardian = json.createObjectNode();
        guardian.set("vinculos", json.valueToTree(guardians.exportFor(userId)));
        guardian.set("historico", array("""
                SELECT to_jsonb(a) FROM guardian_action a JOIN guardian_link l ON l.id = a.link_id
                WHERE l.minor_id = ? OR l.guardian_id = ? ORDER BY a.occurred_at, a.id
                """, userId, userId));
        out.put("responsavel.json", guardian);
        ObjectNode safety = json.createObjectNode();
        safety.set("bloqueados", array("""
                SELECT jsonb_build_object('userId', blocked_id, 'createdAt', created_at)
                FROM user_block WHERE blocker_id = ? ORDER BY created_at
                """, userId));
        safety.set("silenciados", array("""
                SELECT jsonb_build_object('userId', muted_id, 'createdAt', created_at)
                FROM user_mute WHERE muter_id = ? ORDER BY created_at
                """, userId));
        safety.set("palavrasFiltradas", array("""
                SELECT jsonb_build_object('word', word, 'createdAt', created_at)
                FROM user_filtered_word WHERE user_id = ? ORDER BY created_at, word
                """, userId));
        safety.set("ajustes", one("SELECT to_jsonb(s) FROM user_safety_setting s WHERE user_id = ?", userId));
        out.put("seguranca.json", safety);
        ObjectNode moderation = json.createObjectNode();
        moderation.set("denunciasFeitas", array("""
                SELECT to_jsonb(r) - ARRAY['ip_hash','low_trust'] FROM moderation_report r
                WHERE reporter_id = ? ORDER BY created_at, id
                """, userId));
        moderation.set("casosSobreSeuConteudo", array("""
                SELECT to_jsonb(c) - ARRAY['authority_referral','authority_reference','authority_sent_at','priority']
                       || jsonb_build_object('decisoes', COALESCE((
                              SELECT jsonb_agg(to_jsonb(d) - 'moderator_id' ORDER BY d.id)
                              FROM moderation_decision d WHERE d.case_id = c.id), '[]'::jsonb))
                FROM moderation_case c WHERE target_owner_id = ? AND authority_referral = 'NONE'
                ORDER BY created_at, id
                """, userId));
        moderation.set("recursos", array("""
                SELECT to_jsonb(a) FROM moderation_appeal a WHERE appellant_id = ? ORDER BY created_at, id
                """, userId));
        out.put("moderacao.json", moderation);
        out.put("exportacoes.json", array("""
                SELECT to_jsonb(d) - 'object_key' FROM data_export d WHERE user_id = ? ORDER BY requested_at
                """, userId));
        return out;
    }

    /** ZIP com os JSON (indentados) e o LEIA-ME. */
    public byte[] zip(long userId, Instant generatedAt) {
        Map<String, JsonNode> sections = sections(userId);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            put(zip, "LEIA-ME.txt", (README + "\nGerado em " + generatedAt + " (UTC).\n").getBytes(StandardCharsets.UTF_8));
            for (Map.Entry<String, JsonNode> e : sections.entrySet()) {
                put(zip, e.getKey(), json.writerWithDefaultPrettyPrinter().writeValueAsBytes(e.getValue()));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void put(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private ArrayNode array(String sql, Object... args) {
        ArrayNode out = json.createArrayNode();
        jdbc.query(sql, rs -> {
            out.add(json.readTree(rs.getString(1)));
        }, args);
        return out;
    }

    private JsonNode one(String sql, Object... args) {
        ArrayNode rows = array(sql, args);
        return rows.isEmpty() ? json.nullNode() : rows.get(0);
    }
}
