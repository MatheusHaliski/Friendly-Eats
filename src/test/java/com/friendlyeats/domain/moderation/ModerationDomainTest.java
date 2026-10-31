package com.friendlyeats.domain.moderation;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/** RF13 · motivos, prioridades e ações permitidas por tipo de alvo. */
class ModerationDomainTest {

    @Test
    void childAtRiskIsCriticalAndNeverLowered() {
        assertThat(ReportReason.CRIANCA_EM_RISCO.priority()).isEqualTo(ModerationPriority.CRITICA);
        assertThat(ModerationPriority.CRITICA.lowered()).isEqualTo(ModerationPriority.CRITICA);
        assertThat(ReportReason.DESINFORMACAO_SANITARIA.priority()).isEqualTo(ModerationPriority.URGENTE);
        assertThat(ModerationPriority.ALTA.lowered()).isEqualTo(ModerationPriority.NORMAL);
        assertThat(ModerationPriority.BAIXA.lowered()).isEqualTo(ModerationPriority.BAIXA);
        assertThat(ModerationPriority.CRITICA.sla()).isLessThanOrEqualTo(ModerationPriority.NORMAL.sla());
    }

    @Test
    void ownerContestReasonsExcludeDisagreeingWithTheRating() {
        assertThat(Arrays.stream(ReportReason.values()).filter(ReportReason::ownerContest))
                .containsExactlyInAnyOrder(ReportReason.DADO_PESSOAL, ReportReason.AVALIACAO_FALSA,
                        ReportReason.CONFLITO_INTERESSE, ReportReason.OFENSA_OU_CRIME, ReportReason.FORA_DO_TEMA);
        assertThat(ReportReason.values()).allSatisfy(r -> assertThat(r.targets()).isNotEmpty());
        assertThat(ReportReason.fromCode(" crianca_em_risco ")).contains(ReportReason.CRIANCA_EM_RISCO);
        assertThat(ReportReason.fromCode("DISCORDO")).isEmpty();
    }

    @Test
    void actionsDependOnTheTarget() {
        assertThat(TargetType.POST.actions()).contains(ModerationAction.HIDE);
        assertThat(TargetType.REVIEW.actions()).doesNotContain(ModerationAction.HIDE);
        assertThat(TargetType.PROFILE.preventiveHide()).isFalse();
        assertThat(TargetType.COMMENT.preventiveHide()).isTrue();
        assertThat(ModerationAction.WARN.takesDown()).isFalse();
        assertThat(ModerationAction.REMOVE.takesDown()).isTrue();
    }
}
