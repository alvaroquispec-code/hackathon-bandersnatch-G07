# ENTREGA — Tuckersoft Branch Engine

## Resumen de estrellas (salida final de `cd autotests && ./mvnw test`)

```
  ──────────────────────────────────────────────────────────────
   TUCKERSOFT · CONTROL DE CALIDAD
   motor: http://localhost:8080        corrida: MUBX5S22
  ──────────────────────────────────────────────────────────────

   ★★★★★   5 / 5   Cinco estrellas.

   ✔  ★1  SEGURIDAD    65 comprobaciones
   ✔  ★2  NODOS        37 comprobaciones
   ✔  ★3  PARTIDAS     40 comprobaciones
   ✔  ★4  DECISIONES   101 comprobaciones
   ✔  ★5  ASINCRONIA   41 comprobaciones

   tablero: publicado como "G09"

   Las cinco estrellas. Bandersnatch sale para Navidad.
  ──────────────────────────────────────────────────────────────

Tests run: 52, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Tests unitarios propios (`./mvnw test` en la raíz): 5 tests de `DecisionService` con Mockito,
sin PostgreSQL ni red. Resultado: Tests run: 5, Failures: 0, Errors: 0.

## Flujo asíncrono implementado

1. `DecisionService.create` (`@Transactional`) valida propiedad y estado, clasifica el `rawInput`
   con reglas deterministas (`BranchRules`), aplica stats, resuelve el nodo destino y el final,
   guarda `Playthrough` y `Decision` (status `REGISTRADA`) y publica un `DecisionCommittedEvent`
   con todos los datos del correo (destinatario, displayName, jugador, stats...), porque en el
   hilo del listener ya no hay usuario autenticado.
2. El Controller responde 201 de inmediato con status `REGISTRADA`.
3. `BranchNotificationListener` es un `@Component` separado (el service no inyecta
   `JavaMailSender`) con `@Async("branchExecutor")` + `@Transactional(REQUIRES_NEW)` +
   `@TransactionalEventListener(phase = AFTER_COMMIT)`: solo corre cuando PostgreSQL confirmó,
   en un hilo `branch-worker-N` del `ThreadPoolTaskExecutor` (core 2, max 4, cola 50).
4. El listener pone la decisión en `PROCESANDO`, envía el Informe de Realidad con
   `JavaMailSender` y registra un `RealityLog`: `SENT` + `ESTABILIZADA` si sale bien;
   `FAILED` + `ERROR` + `log.error` si falla. La cabecera `X-Bandersnatch-Simulate: MAIL_FAILURE`
   viaja en el evento y hace que el envío lance una `MailSendException` real, atrapada por el
   mismo `catch` que un fallo SMTP de verdad.
5. Imprime `[BRANCH-LOG] ... | Thread: branch-worker-N | Status: ...`.

Las entradas `ENTRADA_CORRUPTA` se guardan con `ERROR` y no publican evento.

## Pendiente

Nada: las 5 estrellas pasan, el tablero está publicado como G09 y los 5 tests unitarios
están implementados.