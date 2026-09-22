package com.tuckersoft.branchengine.service;

import com.tuckersoft.branchengine.dto.DecisionRequest;
import com.tuckersoft.branchengine.dto.DecisionResponse;
import com.tuckersoft.branchengine.entity.Decision;
import com.tuckersoft.branchengine.entity.Playthrough;
import com.tuckersoft.branchengine.entity.StoryNode;
import com.tuckersoft.branchengine.entity.User;
import com.tuckersoft.branchengine.event.DecisionCommittedEvent;
import com.tuckersoft.branchengine.repository.DecisionRepository;
import com.tuckersoft.branchengine.repository.PlaythroughRepository;
import com.tuckersoft.branchengine.repository.RealityLogRepository;
import com.tuckersoft.branchengine.repository.StoryNodeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DecisionServiceTest {

    @Mock PlaythroughRepository playthroughRepository;
    @Mock DecisionRepository decisionRepository;
    @Mock StoryNodeRepository nodeRepository;
    @Mock RealityLogRepository realityLogRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock CurrentUserService currentUserService;

    @InjectMocks DecisionService service;

    User owner;
    StoryNode cereal, bus, espejo;
    Playthrough partida;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(1L);
        owner.setEmail("ada@tuckersoft.test");
        owner.setDisplayName("Ada Lovelace");
        owner.setRole("ROLE_USER");

        cereal = node(10L, "NODE-CEREAL", "NODE-BUS", "NODE-ESPEJO");
        bus = node(11L, "NODE-BUS", null, null);
        espejo = node(12L, "NODE-ESPEJO", null, null);

        partida = new Playthrough();
        partida.setId(100L);
        partida.setPlayerTag("STEFAN-01");
        partida.setUser(owner);
        partida.setStartNodeCode("NODE-CEREAL");
        partida.setCurrentNode(cereal);
        partida.setLucidity(100);
        partida.setControlLevel(0);
        partida.setStatus("ACTIVA");
        partida.setCreatedAt(Instant.now());
        partida.setUpdatedAt(Instant.now());

        lenient().when(currentUserService.get()).thenReturn(owner);
        lenient().when(playthroughRepository.findById(100L)).thenReturn(Optional.of(partida));
        lenient().when(playthroughRepository.save(any(Playthrough.class))).thenAnswer(i -> i.getArgument(0));
        lenient().when(decisionRepository.save(any(Decision.class))).thenAnswer(i -> {
            Decision d = i.getArgument(0);
            if (d.getId() == null) d.setId(500L);
            return d;
        });
        lenient().when(nodeRepository.findByNodeCode("NODE-BUS")).thenReturn(Optional.of(bus));
        lenient().when(nodeRepository.findByNodeCode("NODE-ESPEJO")).thenReturn(Optional.of(espejo));
    }

    private static StoryNode node(Long id, String code, String primary, String glitch) {
        StoryNode n = new StoryNode();
        n.setId(id);
        n.setNodeCode(code);
        n.setTitle("Titulo " + code);
        n.setSceneText("Escena de prueba para " + code);
        n.setBranchCapacity(5);
        n.setCurrentBranches(1);
        n.setPrimaryBranchCode(primary);
        n.setGlitchBranchCode(glitch);
        return n;
    }

    private DecisionResponse decide(String text, String impact) {
        return service.create(new DecisionRequest(100L, text, impact), null);
    }

    @Test
    void destruyeLaCamara_esRupturaCuartaPared_porPrecedencia() {
        assertEquals("RUPTURA_CUARTA_PARED", BranchRules.classify("Stefan destruye la camara"));
        assertEquals("RUPTURA_CUARTA_PARED", BranchRules.classify("Stefan destruye la CÁMARA"));

        DecisionResponse r = decide("Stefan destruye la camara", "LEVE");
        assertEquals("RUPTURA_CUARTA_PARED", r.branchType());
        assertEquals("Departamento Netflix", r.handlerUnit());
        assertEquals("BREAK_FOURTH_WALL", r.outcomeCode());
        assertEquals("NODE-ESPEJO", r.resolvedNodeCode());
    }

    @Test
    void textoSinLetras_esEntradaCorrupta_yLaPartidaNoCambia() {
        DecisionResponse r = decide("1234567890 !!! ???", "GRAVE");

        assertEquals("ENTRADA_CORRUPTA", r.branchType());
        assertEquals("ERROR", r.status());
        assertNull(r.resolvedNodeCode());
        assertEquals(100, partida.getLucidity());
        assertEquals(0, partida.getControlLevel());
        assertEquals("ACTIVA", partida.getStatus());
        assertSame(cereal, partida.getCurrentNode());
        verify(playthroughRepository, never()).save(any(Playthrough.class));
    }

    @Test
    void impactoCritico_bajaLucidez40_subeControl45_yRespetaLimites() {
        DecisionResponse r = decide("Stefan acepta la oferta de Mohan", "CRITICO");
        assertEquals(60, r.lucidity());
        assertEquals(45, r.controlLevel());
        assertEquals("ACTIVA", r.playthroughStatus());
        assertEquals("NODE-ESPEJO", r.resolvedNodeCode()); // CRITICO usa glitchBranchCode

        // limites: desde lucidez 20 / control 70 no se sale de [0,100]
        partida.setStatus("ACTIVA");
        partida.setEndingCode(null);
        partida.setCurrentNode(cereal);
        partida.setLucidity(20);
        partida.setControlLevel(70);
        DecisionResponse r2 = decide("Stefan acepta la oferta de Mohan", "CRITICO");
        assertEquals(0, r2.lucidity());
        assertEquals(100, r2.controlLevel());
    }

    @Test
    void control100_terminaConPacSymbol_aunqueLucidezSeaCero() {
        partida.setLucidity(40);
        partida.setControlLevel(55);

        DecisionResponse r = decide("Stefan acepta la oferta de Mohan", "CRITICO");

        assertEquals(0, r.lucidity());
        assertEquals(100, r.controlLevel());
        assertEquals("FINALIZADA", r.playthroughStatus());
        assertEquals("ENDING_PAC_SYMBOL", r.endingCode());
        assertSame(cereal, partida.getCurrentNode()); // al finalizar no se mueve
    }

    @Test
    void publishEvent_unaVezEnNormal_ceroEnCorrupta() {
        decide("Stefan acepta la oferta de Mohan", "LEVE");
        ArgumentCaptor<DecisionCommittedEvent> captor = ArgumentCaptor.forClass(DecisionCommittedEvent.class);
        verify(eventPublisher, times(1)).publishEvent(captor.capture());
        assertEquals(500L, captor.getValue().decisionId());
        assertEquals("ada@tuckersoft.test", captor.getValue().recipientEmail());

        reset(eventPublisher);
        decide("0000000000 ####", "LEVE");
        verifyNoInteractions(eventPublisher);
    }
}
