package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.core.service.OrganizationServicePort.OrganizationFields;
import com.shale.core.service.OrganizationServicePort.UpdateOrganizationAggregateCommand;
import com.shale.data.dao.OrganizationDao;
import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.component.OrganizationTypeAssignmentPane;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.util.WindowSizingUtil;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Dedicated, dialog-local editor for the complete Organization aggregate. */
final class EditOrganizationDialog {
	private static final Logger LOG = LoggerFactory.getLogger(EditOrganizationDialog.class);
    record LoadResult(Organization organization, byte[] rowVer, OrganizationTypeAssignmentStage assignments,
            OrganizationServicePort.OrganizationStructuredContactProfile contacts) {
        LoadResult { rowVer = rowVer == null ? null : rowVer.clone(); }
        @Override public byte[] rowVer() { return rowVer == null ? null : rowVer.clone(); }
    }

    private final int organizationId;
    private final OrganizationDao dao;
    private final OrganizationServicePort service;
    private final AppState state;
    private final Executor executor;
    private final Consumer<OrganizationServicePort.OrganizationAggregateResult> saved;
    private final Runnable closed;
    private final Dialog<Void> dialog = new Dialog<>();
    private final Label status = new Label("Loading authoritative Organization details…");
    private final VBox content = new VBox(14);
    private final ScrollPane scroll = new ScrollPane(content);
    private final ButtonType saveType = new ButtonType("Save Changes", ButtonData.OK_DONE);
    private final TextField name = field();
    private final EnhancedTextArea notes = new EnhancedTextArea();
    private final OrganizationTypeAssignmentPane assignments = new OrganizationTypeAssignmentPane();
    private LoadResult baseline;
    private PhoneEditor phones;
    private EmailEditor emails;
    private AddressEditor addresses;
    private WebsiteEditor websites;
    private boolean saving;
    private boolean loading = true;
    private boolean forceClose;
    private long generation;

    EditOrganizationDialog(int organizationId, OrganizationDao dao, OrganizationServicePort service, AppState state,
            Executor executor, Consumer<OrganizationServicePort.OrganizationAggregateResult> saved, Runnable closed) {
        this.organizationId = organizationId;
        this.dao = Objects.requireNonNull(dao);
        this.service = Objects.requireNonNull(service);
        this.state = Objects.requireNonNull(state);
        this.executor = Objects.requireNonNull(executor);
        this.saved = Objects.requireNonNull(saved);
        this.closed = Objects.requireNonNull(closed);
    }

    void show(Window owner) {
        AppDialogs.applySecondaryDialogShell(dialog, "Edit Organization");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.setResizable(true);
        dialog.getDialogPane().getButtonTypes().setAll(saveType, ButtonType.CANCEL);
        status.setWrapText(true);
        status.getStyleClass().add("dialog-error-text");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("contact-editor-section-scroll");
        content.setPadding(new Insets(4));
        VBox shell = new VBox(10, status, scroll);
        shell.getStyleClass().add("contact-editor-surface");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        dialog.getDialogPane().setContent(shell);
        dialog.getDialogPane().getStyleClass().add("contact-editor-dialog");
        dialog.getDialogPane().setPrefSize(900, 680);
        Button saveButton = button(saveType);
        Button cancelButton = button(ButtonType.CANCEL);
        ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
        saveButton.setDisable(true);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> { event.consume(); save(); });
        cancelButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            requestClose();
        });
        dialog.setOnCloseRequest(event -> {
            if (!forceClose && !confirmDiscard()) event.consume();
        });
        dialog.setOnHidden(event -> { generation++; closed.run(); });
        dialog.show();
        if (dialog.getDialogPane().getScene().getWindow() instanceof Stage stage) {
            WindowSizingUtil.sizeModalStage(stage, owner, 900, 680, 680, 480);
        }
        reload();
    }

    private void reload() {
        loading = true;
        baseline = null;
        content.getChildren().clear();
        showStatus("Loading authoritative Organization details…");
        button(saveType).setDisable(true);
        long request = ++generation;
        int tenant = requirePositive(state.getShaleClientId(), "No tenant is selected.");
        executor.execute(() -> {
            try {
                Organization organization = dao.findById(organizationId);
                if (organization == null || !Objects.equals(organization.getShaleClientId(), tenant))
                    throw new IllegalStateException("Organization was not found.");
                var definitions = service.listEffectiveOrganizationTypes(tenant);
                var profile = service.getOrganizationTypeProfile(organizationId, tenant)
                        .orElseThrow(() -> new IllegalStateException("Organization Type profile was not found."));
                var stage = OrganizationTypeAssignmentStage.forEdit(definitions, profile);
                var contacts = service.findStructuredContactProfile(tenant, organizationId)
                        .orElseThrow(() -> new IllegalStateException("Structured Organization contact profile was not found."));
                byte[] rowVer = dao.findOrganizationRowVer(organizationId, tenant);
                if (rowVer == null) throw new IllegalStateException("Organization concurrency data was not found.");
                LoadResult result = new LoadResult(organization, rowVer, stage, contacts);
                Platform.runLater(() -> applyLoad(request, result));
            } catch (RuntimeException failure) {
                Platform.runLater(() -> applyLoadFailure(request));
            }
        });
    }

    private void applyLoad(long request, LoadResult loaded) {
        if (request != generation || !dialog.isShowing()) return;
        baseline = loaded;
        loading = false;
        populate(loaded.organization());
        phones = new PhoneEditor(loaded.contacts().phones());
        emails = new EmailEditor(loaded.contacts().emails());
        addresses = new AddressEditor(loaded.contacts().addresses());
        websites = new WebsiteEditor(loaded.contacts().websites());
        assignments.setStage(loaded.assignments());
        assignments.setMessageHandler(this::showStatus);
        content.getChildren().setAll(section("Organization Details", detailsGrid()),
                section("Contact Information", new VBox(14,
                        subsection("Phone Numbers", phones.box), subsection("Email Addresses", emails.box),
                        subsection("Addresses", addresses.box), subsection("Websites", websites.box))),
                section("Organization Types", assignments), section("Notes", notes));
        if (!loaded.contacts().compatibilityConsistent()) {
            showStatus("Organization contact data is inconsistent with its compatibility values. Reload after resolving the conflict; saving is disabled.");
            button(saveType).setDisable(true);
        } else { hideStatus(); button(saveType).setDisable(false); }
    }

    private void applyLoadFailure(long request) {
        if (request != generation || !dialog.isShowing()) return;
        loading = false;
        showStatus("Unable to load authoritative Organization details. Close this window and try again.");
    }

    private void save() {
        if (saving || loading || baseline == null) return;
        if (safe(name.getText()).isBlank()) { showStatus("Name is required."); return; }
        if (!baseline.assignments().isValid()) { showStatus("Exactly one eligible primary Organization Type is required."); return; }
        try { phones.validate(); emails.validate(); addresses.validate(); websites.validate(); }
        catch (IllegalArgumentException failure) { showStatus(failure.getMessage()); return; }
        int tenant;
        int actor;
        try {
            tenant = requirePositive(state.getShaleClientId(), "No tenant is selected.");
            actor = requirePositive(state.getUserId(), "You are not authorized to edit Organizations.");
        } catch (IllegalStateException failure) { showStatus(failure.getMessage()); return; }
        Organization o = baseline.organization();
        var fields = new OrganizationFields(value(name), safe(o.getPhone()), safe(o.getFax()), safe(o.getEmail()), safe(o.getWebsite()),
                safe(o.getAddress1()), safe(o.getAddress2()), safe(o.getCity()), safe(o.getState()), safe(o.getPostalCode()), safe(o.getCountry()), safe(notes.getText()));
        var contacts = new OrganizationServicePort.StructuredContactMutation(
                OrganizationServicePort.OwnedContactCollection.exact(phones.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(emails.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(addresses.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(websites.intent()));
        var command = new UpdateOrganizationAggregateCommand(organizationId, tenant, actor, baseline.rowVer(), fields,
                baseline.assignments().commandAssignments(), contacts);
        saving = true;
        setControlsDisabled(true);
        showStatus("Saving Organization…");
        executor.execute(() -> {
            try {
                var result = service.updateOrganizationAggregate(command);
                Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    saving = false;
                    forceClose = true;
                    dialog.close();
                    saved.accept(result);
                });
            } catch (RuntimeException failure) {
				LOG.warn("Organization aggregate save failed operation=updateOrganizationAggregate tenantId={} actorId={} organizationId={} exceptionClass={}",
						tenant, actor, organizationId, failure.getClass().getName(), failure);
                Platform.runLater(() -> {
                    if (!dialog.isShowing()) return;
                    saving = false;
					String safeFailure = safeFailureMessage(failure);
					boolean conflict = safeFailure.contains("changed by another user");
                    if (conflict) {
                        showStatus("Organization changed elsewhere. Authoritative values are being reloaded.");
                        reload();
					} else if (safeFailure.contains("compatibility ownership")) {
						setControlsDisabled(false);
						showStatus("Organization contact data changed. Reload the Organization and try again.");
					} else if (failure instanceof IllegalArgumentException && !safeFailure.isBlank()) {
						setControlsDisabled(false);
						showStatus(failure.getMessage());
                    } else {
                        setControlsDisabled(false);
                        showStatus("Unable to save Organization. No changes were applied.");
                    }
                });
            }
        });
    }

	private static String safeFailureMessage(Throwable failure) {
		return failure.getMessage() == null ? "" : failure.getMessage().toLowerCase(java.util.Locale.ROOT);
	}

    private GridPane detailsGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(14); grid.setVgap(9);
        ColumnConstraints labels = new ColumnConstraints(130, 150, 180);
        ColumnConstraints controls = new ColumnConstraints(); controls.setHgrow(Priority.ALWAYS); controls.setFillWidth(true);
        grid.getColumnConstraints().addAll(labels, controls);
        ControlStyles.formControl(name);
        notes.setEditorTitle("Organization Notes"); notes.setPrefRowCount(5); notes.setMaxWidth(Double.MAX_VALUE);
        add(grid, 0, "Name", name);
        return grid;
    }

    private boolean isDirty() {
        if (baseline == null) return false;
        Organization o = baseline.organization();
        return !Objects.equals(value(name), safe(o.getName())) || !Objects.equals(safe(notes.getText()), safe(o.getNotes()))
                || baseline.assignments().isDirty() || phones.isDirty() || emails.isDirty()
                || addresses.isDirty() || websites.isDirty();
    }

    private void requestClose() { if (confirmDiscard()) { forceClose = true; dialog.close(); } }
    private boolean confirmDiscard() {
        if (saving) return false;
        return !isDirty() || AppDialogs.showConfirmation(dialog.getOwner(), "Discard Changes?", "Discard unsaved changes?",
                "Closing will discard all Organization and Organization Type changes.", "Discard Changes", AppDialogs.DialogActionKind.DANGER);
    }
    private void populate(Organization o) {
        name.setText(safe(o.getName())); notes.setText(safe(o.getNotes()));
    }
    private void setControlsDisabled(boolean disabled) { content.setDisable(disabled); button(saveType).setDisable(disabled); button(ButtonType.CANCEL).setDisable(disabled); }
    private void showStatus(String message) { status.setText(message == null ? "" : message); status.setVisible(true); status.setManaged(true); }
    private void hideStatus() { status.setText(""); status.setVisible(false); status.setManaged(false); }
    private Button button(ButtonType type) { return (Button) dialog.getDialogPane().lookupButton(type); }
    private static TextField field() { TextField f = new TextField(); f.setMaxWidth(Double.MAX_VALUE); return f; }
    private static VBox section(String title, Node body) { Label heading = new Label(title); heading.getStyleClass().add("contact-editor-section-heading"); return new VBox(9, heading, body); }
    private static VBox subsection(String title, Node body) { return section(title, body); }
    private static void add(GridPane grid, int row, String text, Node field) { Label label = new Label(text); label.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE); label.setLabelFor(field); label.getStyleClass().add("contact-editor-field-label"); grid.add(label, 0, row); grid.add(field, 1, row); GridPane.setHgrow(field, Priority.ALWAYS); }
    private static int requirePositive(Integer value, String message) { if (value == null || value <= 0) throw new IllegalStateException(message); return value; }
    private static String safe(String value) { return value == null ? "" : value.trim(); }
    private static String value(TextField field) { return safe(field.getText()); }

    private abstract static class PointEditor<T> {
        final VBox box = new VBox(8); final List<T> items = new ArrayList<>();
        final CheckBox showRemoved = new CheckBox("Show Removed");
        PointEditor(String add) { Button button = small(add, this::add, ControlStyles.Purpose.PRIMARY); showRemoved.selectedProperty().addListener((o,a,v)->render()); HBox toolbar=new HBox(10, button, showRemoved);toolbar.getStyleClass().add("contact-editor-toolbar");box.getChildren().add(toolbar); }
        abstract void add(); abstract boolean deleted(T x); abstract void deleted(T x, boolean value); abstract boolean primary(T x); abstract void primary(T x, boolean value); abstract int order(T x); abstract void order(T x,int value); abstract Node card(T x); abstract void validate(); abstract String signature();
        String baselineSignature;
        void capture() { normalize(); baselineSignature=signature(); render(); }
        boolean isDirty(){ normalize(); return !Objects.equals(baselineSignature, signature()); }
        void render(){ while(box.getChildren().size()>1)box.getChildren().remove(1); normalize(); for(T x:items)if(!deleted(x)||showRemoved.isSelected())box.getChildren().add(card(x)); if(box.getChildren().size()==1)box.getChildren().add(empty("No entries")); }
        void toggle(T x){deleted(x,!deleted(x));if(deleted(x))primary(x,false);ensurePrimary();render();}
        void makePrimary(T x){for(T y:items)if(!deleted(y))primary(y,y==x);render();}
        void ensurePrimary(){List<T>a=active();if(!a.isEmpty()&&a.stream().noneMatch(this::primary))primary(a.get(0),true);}
        List<T> active(){return items.stream().filter(x->!deleted(x)).sorted(Comparator.comparingInt(this::order)).toList();}
        void move(T x,int delta){List<T>a=active();int i=a.indexOf(x),j=i+delta;if(i<0||j<0||j>=a.size())return;T y=a.get(j);int old=order(x);order(x,order(y));order(y,old);render();}
        void normalize(){int i=0;for(T x:active())order(x,i++);}
        VBox shell(T x,String display,String kind,Runnable edit){HBox badges=new HBox(6,badge(kind));if(primary(x))badges.getChildren().add(badge("Primary"));if(deleted(x))badges.getChildren().add(badge("Removed · Historical"));Label value=new Label(safe(display));value.setWrapText(true);value.getStyleClass().add("contact-point-value");Button editButton=small("Edit",edit,ControlStyles.Purpose.SECONDARY), primaryButton=small("Make Primary",()->makePrimary(x),ControlStyles.Purpose.SECONDARY), up=small("Move Up",()->move(x,-1),ControlStyles.Purpose.SECONDARY), down=small("Move Down",()->move(x,1),ControlStyles.Purpose.SECONDARY), remove=small(deleted(x)?"Restore":"Remove",()->toggle(x),deleted(x)?ControlStyles.Purpose.SECONDARY:ControlStyles.Purpose.DANGER);primaryButton.setVisible(!deleted(x)&&!primary(x));primaryButton.setManaged(primaryButton.isVisible());up.setDisable(deleted(x)||order(x)==0);down.setDisable(deleted(x)||order(x)>=active().size()-1);VBox card=new VBox(7,badges,value,new HBox(6,editButton,primaryButton,up,down,remove));card.getStyleClass().add("contact-point-card");if(deleted(x))card.getStyleClass().add("removed");return card;}
    }
    private static Label badge(String text){Label label=new Label(text);label.getStyleClass().add("contact-point-badge");return label;}
    private static Label empty(String text){Label label=new Label(text);label.getStyleClass().add("shale-empty-state");return label;}
    private static Button small(String text,Runnable action,ControlStyles.Purpose purpose){Button b=new Button(text);ControlStyles.apply(b,purpose,ControlStyles.Size.SMALL);b.setOnAction(e->action.run());return b;}
    private static VBox formField(String label,Node field){ControlStyles.formControl((javafx.scene.control.Control)field);Label l=new Label(label);l.setLabelFor(field);l.getStyleClass().add("contact-editor-field-label");return new VBox(4,l,field);}
    private static boolean editDialog(String title,List<Node> fields,Runnable apply){Dialog<ButtonType>d=new Dialog<>();AppDialogs.applySecondaryDialogShell(d,"Edit "+title);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);d.getDialogPane().setContent(new VBox(8,fields.toArray(Node[]::new)));return d.showAndWait().filter(ButtonType.OK::equals).map(x->{apply.run();return true;}).orElse(false);}
    private static String rv(byte[] value){return value==null?"new":java.util.Base64.getEncoder().encodeToString(value);}

    private static final class P {Long id;byte[]rv;OrganizationServicePort.OrganizationPhoneKind kind;String number,extension;boolean primary,deleted;int order;P(OrganizationServicePort.OrganizationPhoneNumber x){id=x.id();rv=x.rowVer();kind=x.kind();number=x.displayNumber();extension=x.extension();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class PhoneEditor extends PointEditor<P>{PhoneEditor(List<OrganizationServicePort.OrganizationPhoneNumber> xs){super("Add Phone");xs.forEach(x->items.add(new P(x)));capture();}void add(){P x=new P(new OrganizationServicePort.OrganizationPhoneNumber(0,0,0,OrganizationServicePort.OrganizationPhoneKind.MOBILE,"MOBILE","",null,null,false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(P x){TextField n=new TextField(safe(x.number)),e=new TextField(safe(x.extension));ComboBox<OrganizationServicePort.OrganizationPhoneKind> k=combo(OrganizationServicePort.OrganizationPhoneKind.values(),x.kind);return editDialog("Phone Number",List.of(formField("Display Number",n),formField("Extension (optional)",e),formField("Kind",k)),()->{x.number=n.getText();x.extension=value(e).isBlank()?null:value(e);x.kind=k.getValue();if(x.kind==OrganizationServicePort.OrganizationPhoneKind.FAX&&active().stream().anyMatch(y->y!=x&&y.kind!=OrganizationServicePort.OrganizationPhoneKind.FAX))x.primary=false;ensureVoicePrimary();});}void ensureVoicePrimary(){List<P>voice=active().stream().filter(x->x.kind!=OrganizationServicePort.OrganizationPhoneKind.FAX).toList();if(!voice.isEmpty()&&voice.stream().noneMatch(x->x.primary)){active().forEach(x->x.primary=false);voice.get(0).primary=true;}else ensurePrimary();}boolean deleted(P x){return x.deleted;}void deleted(P x,boolean v){x.deleted=v;}boolean primary(P x){return x.primary;}void primary(P x,boolean v){x.primary=v;}int order(P x){return x.order;}void order(P x,int v){x.order=v;}Node card(P x){return shell(x,x.number+(safe(x.extension).isBlank()?"":" ext. "+x.extension),x.kind.name(),()->{if(edit(x))render();});}void validate(){ensureVoicePrimary();Set<String>d=new HashSet<>();for(P x:active()){if(safe(x.number).isBlank()||safe(x.number).length()>255)throw new IllegalArgumentException("Phone Numbers: every active entry requires a number of at most 255 characters.");String key=safe(x.number).toLowerCase(Locale.ROOT)+"|"+safe(x.extension).toLowerCase(Locale.ROOT);if(!d.add(key))throw new IllegalArgumentException("Phone Numbers: duplicate active entries are not allowed.");if(x.kind==OrganizationServicePort.OrganizationPhoneKind.UNKNOWN)throw new IllegalArgumentException("Phone Numbers: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationPhone>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationPhone(x.id,x.rv,x.kind,x.number,x.extension,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.displayNumber()+"|"+x.extension()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class E {Long id;byte[]rv;OrganizationServicePort.OrganizationEmailKind kind;String value;boolean primary,deleted;int order;E(OrganizationServicePort.OrganizationEmailAddress x){id=x.id();rv=x.rowVer();kind=x.kind();value=x.emailAddress();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class EmailEditor extends PointEditor<E>{EmailEditor(List<OrganizationServicePort.OrganizationEmailAddress>xs){super("Add Email");xs.forEach(x->items.add(new E(x)));capture();}void add(){E x=new E(new OrganizationServicePort.OrganizationEmailAddress(0,0,0,OrganizationServicePort.OrganizationEmailKind.PERSONAL,"PERSONAL","","",false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(E x){TextField v=new TextField(safe(x.value));ComboBox<OrganizationServicePort.OrganizationEmailKind>k=combo(OrganizationServicePort.OrganizationEmailKind.values(),x.kind);return editDialog("Email Address",List.of(formField("Email Address",v),formField("Kind",k)),()->{x.value=v.getText();x.kind=k.getValue();});}boolean deleted(E x){return x.deleted;}void deleted(E x,boolean v){x.deleted=v;}boolean primary(E x){return x.primary;}void primary(E x,boolean v){x.primary=v;}int order(E x){return x.order;}void order(E x,int v){x.order=v;}Node card(E x){return shell(x,x.value,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();Set<String>d=new HashSet<>();for(E x:active()){String v=safe(x.value);if(v.length()>320||!v.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Email Addresses: enter a valid address for every active entry.");if(!d.add(v.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Email Addresses: duplicate active addresses are not allowed.");if(x.kind==OrganizationServicePort.OrganizationEmailKind.UNKNOWN)throw new IllegalArgumentException("Email Addresses: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationEmail>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationEmail(x.id,x.rv,x.kind,x.value,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.emailAddress()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class A {Long id;byte[]rv;OrganizationServicePort.OrganizationAddressKind kind;String l1,l2,city,state,postal,country,legacy;boolean primary,deleted;int order;A(OrganizationServicePort.OrganizationAddress x){id=x.id();rv=x.rowVer();kind=x.kind();l1=x.addressLine1();l2=x.addressLine2();city=x.city();state=x.stateOrProvince();postal=x.postalCode();country=x.country();legacy=x.legacyAddressText();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class AddressEditor extends PointEditor<A>{AddressEditor(List<OrganizationServicePort.OrganizationAddress>xs){super("Add Address");xs.forEach(x->items.add(new A(x)));capture();}void add(){A x=new A(new OrganizationServicePort.OrganizationAddress(0,0,0,OrganizationServicePort.OrganizationAddressKind.WORK,"WORK",null,null,null,null,null,null,null,false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(A x){TextField l1=new TextField(safe(x.l1)),l2=new TextField(safe(x.l2)),city=new TextField(safe(x.city)),state=new TextField(safe(x.state)),postal=new TextField(safe(x.postal)),country=new TextField(safe(x.country));ComboBox<OrganizationServicePort.OrganizationAddressKind>k=combo(OrganizationServicePort.OrganizationAddressKind.values(),x.kind);GridPane compact=new GridPane();compact.setHgap(8);compact.add(formField("State / Province",state),0,0);compact.add(formField("Postal Code",postal),1,0);return editDialog("Address",List.of(formField("Address Line 1",l1),formField("Address Line 2",l2),formField("City",city),compact,formField("Country",country),formField("Kind",k)),()->{x.l1=l1.getText();x.l2=l2.getText();x.city=city.getText();x.state=state.getText();x.postal=postal.getText();x.country=country.getText();x.kind=k.getValue();});}boolean deleted(A x){return x.deleted;}void deleted(A x,boolean v){x.deleted=v;}boolean primary(A x){return x.primary;}void primary(A x,boolean v){x.primary=v;}int order(A x){return x.order;}void order(A x,int v){x.order=v;}Node card(A x){String shown=java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country).map(EditOrganizationDialog::safe).filter(v->!v.isBlank()).collect(java.util.stream.Collectors.joining(", "));if(shown.isBlank())shown=x.legacy;return shell(x,shown,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();for(A x:active()){if(java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy).allMatch(v->safe(v).isBlank()))throw new IllegalArgumentException("Addresses: every active entry requires at least one address value.");if(x.kind==OrganizationServicePort.OrganizationAddressKind.UNKNOWN)throw new IllegalArgumentException("Addresses: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationAddress>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationAddress(x.id,x.rv,x.kind,x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+Arrays.asList(x.addressLine1(),x.addressLine2(),x.city(),x.stateOrProvince(),x.postalCode(),x.country(),x.legacyAddressText())+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class W {Long id;byte[]rv;OrganizationServicePort.OrganizationWebsiteKind kind;String value;boolean primary,deleted;int order;W(OrganizationServicePort.OrganizationWebsite x){id=x.id();rv=x.rowVer();kind=x.kind();value=x.website();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class WebsiteEditor extends PointEditor<W>{WebsiteEditor(List<OrganizationServicePort.OrganizationWebsite>xs){super("Add Website");xs.forEach(x->items.add(new W(x)));capture();}void add(){W x=new W(new OrganizationServicePort.OrganizationWebsite(0,0,0,OrganizationServicePort.OrganizationWebsiteKind.MAIN,"MAIN","",false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(W x){TextField v=new TextField(safe(x.value));ComboBox<OrganizationServicePort.OrganizationWebsiteKind>k=combo(OrganizationServicePort.OrganizationWebsiteKind.values(),x.kind);return editDialog("Website",List.of(formField("Website",v),formField("Kind",k)),()->{x.value=v.getText();x.kind=k.getValue();});}boolean deleted(W x){return x.deleted;}void deleted(W x,boolean v){x.deleted=v;}boolean primary(W x){return x.primary;}void primary(W x,boolean v){x.primary=v;}int order(W x){return x.order;}void order(W x,int v){x.order=v;}Node card(W x){return shell(x,x.value,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();Set<String>d=new HashSet<>();for(W x:active()){String v=safe(x.value);if(v.isBlank()||v.length()>2048)throw new IllegalArgumentException("Websites: every active entry requires a value.");if(!d.add(v.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Websites: duplicate active values are not allowed.");if(x.kind==OrganizationServicePort.OrganizationWebsiteKind.UNKNOWN)throw new IllegalArgumentException("Websites: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationWebsite>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationWebsite(x.id,x.rv,x.kind,x.value,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.website()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static <T> ComboBox<T> combo(T[] values,T selected){ComboBox<T> box=new ComboBox<>();box.getItems().addAll(values);box.setValue(selected);ControlStyles.formControl(box);return box;}
}
