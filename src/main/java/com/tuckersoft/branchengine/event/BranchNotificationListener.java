package com.tuckersoft.branchengine.event;

import com.tuckersoft.branchengine.entity.Decision;
import com.tuckersoft.branchengine.entity.RealityLog;
import com.tuckersoft.branchengine.repository.DecisionRepository;
import com.tuckersoft.branchengine.repository.RealityLogRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class BranchNotificationListener {
    private final DecisionRepository decisionRepository;
    private final RealityLogRepository realityLogRepository;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String mailFrom;

    @Async("branchExecutor")
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void alCommit(DecisionCommittedEvent e) {
        Decision d = decisionRepository.findById(e.decisionId()).orElse(null);
        if (d == null) {
            log.error("Decision {} no encontrada tras el commit", e.decisionId());
            return;
        }

        // 7. PROCESANDO
        d.setStatus("PROCESANDO");
        d.setUpdatedAt(Instant.now());
        decisionRepository.saveAndFlush(d);

        String subject = "[TUCKERSOFT] " + e.branchType() + " en " + e.playerTag() + " | Impacto " + e.impactLevel();
        RealityLog rl = new RealityLog();
        rl.setDecision(d);
        rl.setRecipientEmail(e.recipientEmail());
        rl.setSubject(subject);
        rl.setCreatedAt(Instant.now());

        // 8. Enviar el Informe de Realidad
        try {
            sendReport(e, subject);
            d.setStatus("ESTABILIZADA");
            rl.setLogStatus("SENT");
            rl.setSentAt(Instant.now());
        } catch (Exception ex) {
            d.setStatus("ERROR");
            rl.setLogStatus("FAILED");
            rl.setErrorMessage(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getName());
            log.error("Fallo el envio del Informe de Realidad para la decision {}: {}", e.decisionId(), ex.getMessage());
        }
        d.setUpdatedAt(Instant.now());
        decisionRepository.save(d);
        realityLogRepository.save(rl);

        // 9. Log obligatorio
        log.info("[BRANCH-LOG] Decision ID: {} | Player: {} | Branch: {} | Impact: {} | Unit: {} | Node: {} -> {} | Thread: {} | Status: {}",
                e.decisionId(), e.playerTag(), e.branchType(), e.impactLevel(), e.handlerUnit(),
                e.sourceNodeCode(), e.resolvedNodeCode(), Thread.currentThread().getName(), d.getStatus());
    }

    private void sendReport(DecisionCommittedEvent e, String subject) throws Exception {
        if (e.simulateMailFailure()) {
            // Fallo real lanzado en el punto de envio: lo atrapa el mismo catch que un fallo SMTP de verdad
            throw new MailSendException("Fallo SMTP simulado (X-Bandersnatch-Simulate: MAIL_FAILURE)");
        }
        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
        helper.setFrom(mailFrom == null || mailFrom.isBlank() ? "qa@tuckersoft.test" : mailFrom);
        helper.setTo(e.recipientEmail());
        helper.setSubject(subject);
        helper.setText(buildBody(e), false);
        mailSender.send(msg);
    }

    private String buildBody(DecisionCommittedEvent e) {
        String line = "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━";
        return "Hola " + e.displayName() + ",\n\n"
                + "Una partida de prueba acaba de ramificarse.\n\n"
                + line + "\n"
                + "Decision ID      : #" + e.decisionId() + "\n"
                + "Jugador          : " + e.playerTag() + "\n"
                + "Rama             : " + e.branchType() + "\n"
                + "Impacto          : " + e.impactLevel() + "\n"
                + "Departamento     : " + e.handlerUnit() + "\n"
                + "Consecuencia     : " + e.outcomeCode() + "\n"
                + "Nodo origen      : " + e.sourceNodeCode() + "\n"
                + "Nodo destino     : " + (e.resolvedNodeCode() == null ? "-" : e.resolvedNodeCode()) + "\n"
                + "Estado partida   : " + e.playthroughStatus() + "\n"
                + "Lucidez          : " + e.lucidity() + "/100\n"
                + "Nivel de control : " + e.controlLevel() + "/100\n"
                + "Final            : " + (e.endingCode() == null ? "-" : e.endingCode()) + "\n"
                + "Registrada       : " + e.createdAt() + "\n"
                + line + "\n\n"
                + "Decisión original del jugador:\n"
                + "\"" + e.rawInput() + "\"\n\n"
                + "— Tuckersoft Branch Engine, 1984\n";
    }
}
