package com.shale.ui.controller;

import com.shale.core.dto.CaseDateDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.CaseServicePort.UpdateCaseDateCommand;
import com.shale.ui.component.dialog.CaseDateOccurrenceDialog;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import javafx.application.Platform;
import javafx.stage.Window;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** UI-level orchestration for opening the authoritative existing-occurrence editor. */
final class CaseDateOccurrenceEditorLauncher {
    record Context(int tenantId, int actorId, long caseId, boolean open) {
        boolean valid() { return tenantId > 0 && actorId > 0 && caseId > 0 && open; }
    }
    record SaveResult(Context context, CaseDateDto date) {}

    private final CaseServicePort caseService;
    private final Executor executor;
    private final Supplier<Context> currentContext;
    private final Supplier<Window> owner;
    private final Consumer<SaveResult> onSaved;
    private final Consumer<String> onLoadFailure;
    private final Consumer<Boolean> onDialogState;
    private final AtomicLong generations = new AtomicLong();
    private final Map<Long, Long> opening = new ConcurrentHashMap<>();

    CaseDateOccurrenceEditorLauncher(CaseServicePort caseService, Executor executor,
            Supplier<Context> currentContext, Supplier<Window> owner,
            Consumer<SaveResult> onSaved, Consumer<String> onLoadFailure, Consumer<Boolean> onDialogState) {
        this.caseService = Objects.requireNonNull(caseService);
        this.executor = Objects.requireNonNull(executor);
        this.currentContext = Objects.requireNonNull(currentContext);
        this.owner = Objects.requireNonNull(owner);
        this.onSaved = onSaved == null ? result -> {} : onSaved;
        this.onLoadFailure = onLoadFailure == null ? message -> {} : onLoadFailure;
        this.onDialogState = onDialogState == null ? open -> {} : onDialogState;
    }

    void open(long expectedCaseId, long caseDateId) {
        Context captured = currentContext.get();
        if (caseDateId <= 0 || captured == null || !captured.valid() || captured.caseId() != expectedCaseId) return;
        long generation = generations.incrementAndGet();
        if (opening.putIfAbsent(caseDateId, generation) != null) return;
        executor.execute(() -> load(captured, caseDateId, generation));
    }

    private void load(Context captured, long caseDateId, long generation) {
        try {
            Optional<CaseDateDto> loaded = caseService.getCaseDate(caseDateId, captured.tenantId(), captured.actorId());
            Optional<CaseOverviewDto> caseOverview = caseService.getCaseOverview(captured.caseId(), captured.tenantId());
            var types = caseService.listEffectiveCaseDateTypes(captured.tenantId(), captured.actorId());
            Platform.runLater(() -> {
                if (!isCurrent(captured, caseDateId, generation)) { opening.remove(caseDateId, generation); return; }
                if (loaded.isEmpty() || !matches(loaded.get(), captured, caseDateId)
                        || caseOverview.isEmpty() || caseOverview.get().getCaseId() != captured.caseId()) {
                    opening.remove(caseDateId, generation);
                    onLoadFailure.accept("This Case Date is no longer available.");
                    return;
                }
                CaseDateDto existing = loaded.get();
                try {
                    onDialogState.accept(true);
                    CaseDateOccurrenceDialog.show(owner.get(), "Edit Date", types, existing, toCaseCardModel(caseOverview.get()),
                            input -> save(captured, existing, input),
                            () -> Platform.runLater(() -> { opening.remove(caseDateId, generation); open(captured.caseId(), caseDateId); }));
                } finally {
                    onDialogState.accept(false);
                    opening.remove(caseDateId, generation);
                }
            });
        } catch (RuntimeException ex) {
            Platform.runLater(() -> {
                if (isCurrent(captured, caseDateId, generation)) onLoadFailure.accept("This Case Date could not be opened.");
                opening.remove(caseDateId, generation);
            });
        }
    }

    static CaseCardModel toCaseCardModel(CaseOverviewDto overview) {
        Objects.requireNonNull(overview, "overview");
        return new CaseCardModel(overview.getCaseId(), overview.getCaseName(), overview.getIntakeDate(),
                overview.getSolDate(), overview.getTortNoticeDeadline(), overview.getResponsibleAttorney(),
                overview.getResponsibleAttorneyColor(), false, overview.getCaseStatus(),
                overview.getPrimaryStatusColor(), overview.getPracticeAreaColor());
    }

    private java.util.concurrent.CompletionStage<String> save(Context captured, CaseDateDto existing,
            CaseDateOccurrenceDialog.Input input) {
        return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
            Context now = currentContext.get();
            if (!sameContext(captured, now) || !matches(existing, captured, existing.id())) return "This Case Date is no longer available.";
            try {
                CaseDateDto saved = caseService.updateCaseDate(new UpdateCaseDateCommand(captured.tenantId(), captured.actorId(),
                        captured.caseId(), existing.id(), input.caseDateTypeId(), input.title(), input.startsAt(),
                        input.endsAt(), input.allDay(), input.notes(), existing.rowVer()));
                Platform.runLater(() -> onSaved.accept(new SaveResult(captured, saved)));
                return null;
            } catch (RuntimeException ex) {
                return rootMessage(ex);
            }
        }, executor);
    }

    private boolean isCurrent(Context captured, long id, long generation) {
        return Objects.equals(opening.get(id), generation) && sameContext(captured, currentContext.get());
    }

    private static boolean sameContext(Context expected, Context actual) {
        return actual != null && actual.valid() && expected.tenantId() == actual.tenantId()
                && expected.actorId() == actual.actorId() && expected.caseId() == actual.caseId();
    }

    private static boolean matches(CaseDateDto date, Context context, long id) {
        return date != null && date.id() == id && date.caseId() == context.caseId()
                && date.shaleClientId() == context.tenantId();
    }

    private static String rootMessage(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getMessage() == null || current.getMessage().isBlank() ? "Unable to save this case date." : current.getMessage();
    }
}
