package com.shale.ui.controller;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.shale.core.dto.ContactSharedCaseLinkDto;
import com.shale.core.dto.CaseLinkDto;
import com.shale.core.service.CaseServicePort;
import com.shale.core.service.ContactServicePort;
import com.shale.data.dao.ContactDao;
import com.shale.data.dao.ContactDao.ContactDetailRow;
import com.shale.data.dao.ContactDao.ContactProfileUpdateRequest;
import com.shale.data.dao.CaseSummaryDao.RelatedCaseRow;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.util.ControlStyles;
import com.shale.ui.component.factory.CaseCardFactory;
import com.shale.ui.component.factory.CaseLinkCardFactory;
import com.shale.ui.component.factory.CaseCardFactory.CaseCardModel;
import com.shale.ui.services.ContactDetailService;
import com.shale.ui.services.PhiReadAuditService;
import com.shale.ui.services.LiveUpdateEvents;
import com.shale.ui.services.UiRuntimeBridge;
import com.shale.ui.util.ExternalBrowserHelper;
import com.shale.ui.util.PerfLog;
import com.shale.ui.state.AppState;
import com.shale.ui.util.ReadOnlyTextDisplaySupport;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar.ButtonData;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Region;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.stage.Window;

public final class ContactViewController {

    private static final Logger LOG = Logger.getLogger(ContactViewController.class.getName());

    @FXML private Label contactTitleLabel;
    @FXML private Label contactSubtitleLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label errorLabel;
    @FXML private Button editButton;
    @FXML private Button saveButton;
    @FXML private Button cancelButton;
    @FXML private Button deleteContactButton;
    @FXML private VBox relatedCasesContainer;
    @FXML private Label relatedCasesEmptyLabel;
    @FXML private VBox sharedLinksContainer;
    @FXML private Label sharedLinksStatusLabel;
    @FXML private FlowPane contactTypeChips;
    @FXML private FlowPane specialtyChips;
    @FXML private FlowPane credentialChips;
    @FXML private BorderPane rootPane;
    @FXML private javafx.scene.layout.GridPane profileGrid;
    @FXML private VBox relatedSidebar;

    @FXML private Label displayNameValue;
    @FXML private Label nameValue;
    @FXML private TextField nameEditor;
    @FXML private Label firstNameValue;
    @FXML private TextField firstNameEditor;
    @FXML private Label lastNameValue;
    @FXML private TextField lastNameEditor;
    @FXML private Label emailValue;
    @FXML private TextField emailEditor;
    @FXML private Label phoneValue;
    @FXML private TextField phoneEditor;
    @FXML private Label addressHomeValue;
    @FXML private TextArea addressHomeEditor;
    @FXML private Label dateOfBirthValue;
    @FXML private DatePicker dateOfBirthEditor;
    @FXML private Label conditionValue;
    @FXML private TextArea conditionEditor;
    @FXML private Label deceasedValue;
    @FXML private CheckBox deceasedEditor;
    @FXML private Button editDisplayNameButton;
    @FXML private Button editNameButton;
    @FXML private Button editFirstNameButton;
    @FXML private Button editLastNameButton;
    @FXML private Button editEmailButton;
    @FXML private Button editPhoneButton;
    @FXML private Button editAddressHomeButton;
    @FXML private Button editDateOfBirthButton;
    @FXML private Button editConditionButton;
    @FXML private Button editDeceasedButton;

    private int contactId;
    private ContactDetailService contactDetailService;
    private AppState appState;
    private ContactDetailRow currentContact;
    private boolean editMode;
    private Consumer<Integer> onOpenCase;
    private Consumer<Integer> onOpenContact;
    private Runnable onContactDeleted;
    private CaseCardFactory caseCardFactory;
    private List<RelatedCaseRow> relatedCases = List.of();
    private PhiReadAuditService phiReadAuditService;
    private int detailLoadGeneration = 0;
    private int sharedLinksLoadGeneration = 0;
    private List<ContactSharedCaseLinkDto> sharedLinks = List.of();
    private boolean sharedLinksLoaded;
    private UiRuntimeBridge runtimeBridge;
    private final Consumer<UiRuntimeBridge.EntityUpdatedEvent> sharedLinksLiveHandler = this::handleSharedLinksLiveEvent;
    private CaseServicePort caseService;
    private final CaseLinkCardFactory caseLinkCardFactory = new CaseLinkCardFactory();
    private ExternalBrowserHelper externalBrowserHelper = new ExternalBrowserHelper();
    private boolean initialized;
    private ContactServicePort contactService;
    private ContactServicePort.ClassificationProfile classificationProfile;
    private List<ContactServicePort.Definition> effectiveTypes=List.of(),effectiveSpecialties=List.of();
    private List<ContactServicePort.CredentialDefinition> effectiveCredentials=List.of();
    private boolean saveInFlight,disposed;

    private final ExecutorService dbExec = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "contact-view-loader");
        t.setDaemon(true);
        return t;
    });

    public void init(
            int contactId,
            ContactDetailService contactDetailService,
            AppState appState,
            Consumer<Integer> onOpenCase,
            Runnable onContactDeleted,
            PhiReadAuditService phiReadAuditService) {
        init(contactId, contactDetailService, appState, onOpenCase, null, onContactDeleted, phiReadAuditService, null, null);
    }

    public void init(
            int contactId,
            ContactDetailService contactDetailService,
            AppState appState,
            Consumer<Integer> onOpenCase,
            CaseServicePort caseService,
            Runnable onContactDeleted,
            PhiReadAuditService phiReadAuditService,
            Consumer<Integer> onOpenContact, UiRuntimeBridge runtimeBridge) {
        this.contactId = contactId;
        this.contactDetailService = contactDetailService;
        this.appState = appState;
        this.onOpenCase = onOpenCase;
        this.onContactDeleted = onContactDeleted;
        this.caseService = caseService;
        this.onOpenContact = onOpenContact;
        this.phiReadAuditService = phiReadAuditService;
        this.runtimeBridge = runtimeBridge;
        if (this.runtimeBridge != null) this.runtimeBridge.subscribeEntityUpdated(sharedLinksLiveHandler);
        this.caseCardFactory = new CaseCardFactory(onOpenCase);
        auditContactRead();
        if (initialized) {
            resetSharedLinksState();
            loadContact();
            loadSharedLinks();
        }
    }

    public void setContactService(ContactServicePort service){this.contactService=Objects.requireNonNull(service);}

    private void handleSharedLinksLiveEvent(UiRuntimeBridge.EntityUpdatedEvent event) {
        if (event == null || appState == null || !LiveUpdateEvents.ENTITY_CASE_LINK_SHARE.equals(event.entityType())) return;
        Integer tenantId = appState.getShaleClientId();
        if (tenantId == null || event.shaleClientId() != tenantId) return;
        Object rawContactId = event.patch() == null ? null : event.patch().get("contactId");
        long eventContactId;
        try { eventContactId = rawContactId instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(rawContactId)); }
        catch (RuntimeException ex) { return; }
        if (eventContactId != contactId) return;
        Platform.runLater(this::loadSharedLinks);
    }

    private void auditContactRead() {
        if (phiReadAuditService == null || contactId <= 0) {
            return;
        }
        phiReadAuditService.auditRead("Contact.View.Read", "Contact.View", "Contact", (long) contactId);
    }

    @FXML
    private void initialize() {
        if (editButton != null) {
            ControlStyles.apply(editButton, ControlStyles.Purpose.SECONDARY);
            editButton.setOnAction(e -> onEdit());
            setVisibleManaged(editButton, false);
        }
        if (saveButton != null) {
            ControlStyles.apply(saveButton, ControlStyles.Purpose.PRIMARY);
            saveButton.setOnAction(e -> onSave());
        }
        if (cancelButton != null) {
            ControlStyles.apply(cancelButton, ControlStyles.Purpose.SECONDARY);
            cancelButton.setOnAction(e -> onCancel());
        }
        if (deleteContactButton != null) {
            ControlStyles.apply(deleteContactButton, ControlStyles.Purpose.DANGER);
            deleteContactButton.setOnAction(e -> onDeleteContact());
            setVisibleManaged(deleteContactButton, false);
        }

        initialized = true;
        if(rootPane!=null)rootPane.sceneProperty().addListener((o,oldScene,newScene)->{if(oldScene!=null&&newScene==null)dispose();});
        if(profileGrid!=null)profileGrid.widthProperty().addListener((o,a,width)->applyResponsiveLayout(width.doubleValue()));
        for (javafx.scene.control.Control editor : java.util.stream.Stream.of(nameEditor, firstNameEditor, lastNameEditor,
                emailEditor, phoneEditor, addressHomeEditor, dateOfBirthEditor, conditionEditor, deceasedEditor)
                .filter(Objects::nonNull).toList()) {
            ControlStyles.formControl(editor);
        }
        setEditMode(false);
        renderRelatedCases();
        resetSharedLinksState();
        Platform.runLater(() -> { loadContact(); loadSharedLinks(); });
    }

    public void dispose(){if(disposed)return;disposed=true;detailLoadGeneration++;sharedLinksLoadGeneration++;if(runtimeBridge!=null)runtimeBridge.unsubscribeEntityUpdated(sharedLinksLiveHandler);dbExec.shutdownNow();}
    private void applyResponsiveLayout(double width){if(relatedSidebar==null||profileGrid.getColumnConstraints().size()<2)return;boolean narrow=width<860;profileGrid.getColumnConstraints().get(0).setPercentWidth(narrow?100:63);profileGrid.getColumnConstraints().get(1).setPercentWidth(narrow?0:37);javafx.scene.layout.GridPane.setColumnIndex(relatedSidebar,narrow?0:1);javafx.scene.layout.GridPane.setRowIndex(relatedSidebar,narrow?2:0);javafx.scene.layout.GridPane.setRowSpan(relatedSidebar,narrow?1:2);}

    private void initializeInlineEditButtons() {
        configureInlineEditButton(editDisplayNameButton, "Display Name",
                () -> showContactTextFieldDialog("Edit Display Name", "Display Name", currentContact.displayName(), false, editDisplayNameButton,
                        value -> saveSingleContactField(ContactField.NAME, value)));
        configureInlineEditButton(editNameButton, "Name",
                () -> showContactTextFieldDialog("Edit Name", "Name", currentContact.name(), false, editNameButton,
                        value -> saveSingleContactField(ContactField.NAME, value)));
        configureInlineEditButton(editFirstNameButton, "First Name",
                () -> showContactTextFieldDialog("Edit First Name", "First Name", currentContact.firstName(), false, editFirstNameButton,
                        value -> saveSingleContactField(ContactField.FIRST_NAME, value)));
        configureInlineEditButton(editLastNameButton, "Last Name",
                () -> showContactTextFieldDialog("Edit Last Name", "Last Name", currentContact.lastName(), false, editLastNameButton,
                        value -> saveSingleContactField(ContactField.LAST_NAME, value)));
        configureInlineEditButton(editEmailButton, "Email",
                () -> showContactTextFieldDialog("Edit Email", "Email", currentContact.email(), false, editEmailButton,
                        value -> saveSingleContactField(ContactField.EMAIL, value)));
        configureInlineEditButton(editPhoneButton, "Phone",
                () -> showContactTextFieldDialog("Edit Phone", "Phone", currentContact.phone(), false, editPhoneButton,
                        value -> saveSingleContactField(ContactField.PHONE, value)));
        configureInlineEditButton(editAddressHomeButton, "Home Address",
                () -> showContactTextAreaDialog("Edit Home Address", "Home Address", currentContact.addressHome(), editAddressHomeButton,
                        value -> saveSingleContactField(ContactField.ADDRESS_HOME, value)));
        configureInlineEditButton(editDateOfBirthButton, "Date of Birth",
                () -> showContactDateDialog("Edit Date of Birth", "Date of Birth", currentContact.dateOfBirth(), editDateOfBirthButton,
                        value -> saveSingleContactField(ContactField.DATE_OF_BIRTH, value)));
        configureInlineEditButton(editConditionButton, "Condition",
                () -> showContactTextAreaDialog("Edit Condition", "Condition", currentContact.condition(), editConditionButton,
                        value -> saveSingleContactField(ContactField.CONDITION, value)));
        configureInlineEditButton(editDeceasedButton, "Deceased",
                () -> showContactBooleanDialog("Edit Deceased", "Deceased", currentContact.deceased(), editDeceasedButton,
                        value -> saveSingleContactField(ContactField.DECEASED, value)));
    }

    private void configureInlineEditButton(Button button, String fieldLabel, Runnable editAction) {
        if (button == null) {
            return;
        }
        ControlStyles.apply(button, ControlStyles.Purpose.GHOST, ControlStyles.Size.SMALL);
        ControlStyles.iconOnly(button);
        button.setAccessibleText("Edit " + fieldLabel);
        button.setTooltip(new Tooltip("Edit " + fieldLabel));
        button.setOnAction(e -> {
            if (!canEditContact()) {
                setError("You do not have permission to edit this contact.");
                return;
            }
            if (currentContact == null) {
                setError("Contact details are unavailable.");
                return;
            }
            clearError();
            if (editAction != null) {
                editAction.run();
            }
        });
    }

    private void loadContact() {
        if (disposed) {
            return;
        }
        final int generation = ++detailLoadGeneration;
        final int requestedContactId = contactId;
        final long loadStarted = PerfLog.start();
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (contactDetailService == null || tenantId == null || tenantId <= 0 || requestedContactId <= 0) {
            setError("Contact details are unavailable right now.");
            return;
        }

        PerfLog.log("contacts.detail", "load.start", "contactId=" + requestedContactId + " tenantId=" + tenantId + " generation=" + generation);
        setBusy(true);
        dbExec.submit(() -> {
            try {
                ContactDetailService.ContactDetailSnapshot snapshot = contactDetailService.loadSnapshot(requestedContactId, tenantId);
                ContactDetailRow row = snapshot.contact();
                ContactServicePort.ClassificationProfile profile=contactService==null?null:contactService.getClassificationProfile(requestedContactId,tenantId).orElse(null);
                List<ContactServicePort.Definition> types=contactService==null?List.of():contactService.getEffectiveContactTypes(tenantId);
                List<ContactServicePort.Definition> specialties=contactService==null?List.of():contactService.getEffectiveSpecialties(tenantId);
                List<ContactServicePort.CredentialDefinition> credentials=contactService==null?List.of():contactService.getEffectiveCredentialDefinitions(tenantId);
                List<RelatedCaseRow> loadedRelatedCases = snapshot.relatedCases();
                Platform.runLater(() -> {
                    if (disposed||generation != detailLoadGeneration || contactId != requestedContactId) {
                        PerfLog.logDone("contacts.detail", "phase=discard generation=" + generation + " reason=stale", loadStarted);
                        return;
                    }
                    setBusy(false);
                    if (row == null) {
                        currentContact = null;
                        relatedCases = List.of();
                        renderRelatedCases();
                        refreshDeleteAction();
                        setError("This contact could not be found for the current tenant.");
                        return;
                    }
                    currentContact = row;
                    classificationProfile=profile;effectiveTypes=types;effectiveSpecialties=specialties;effectiveCredentials=credentials;
                    relatedCases = loadedRelatedCases == null ? List.of() : loadedRelatedCases;
                    long renderStarted = PerfLog.start();
                    renderFromCurrent();
                    renderRelatedCases();
                    renderClassifications();
                    loadSharedLinks();
                    setEditMode(false);
                    clearError();
                    PerfLog.logDone("contacts.detail.render", "contactId=" + contactId + " relatedCases=" + relatedCases.size() + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
                    PerfLog.logDone("contacts.detail", "phase=apply generation=" + generation + " contactId=" + contactId + " relatedCases=" + relatedCases.size(), loadStarted);
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    if (disposed||generation != detailLoadGeneration || contactId != requestedContactId) {
                        return;
                    }
                    setBusy(false);
                    if(currentContact==null){relatedCases=List.of();renderRelatedCases();refreshDeleteAction();}
                    setError("Unable to load this contact.");
                });
            }
        });
    }

    private void onEdit() {
        if (!canEditContact()) {
            setError("You do not have permission to edit this contact.");
            return;
        }
        if (currentContact == null) {
            setError("Contact details are unavailable.");
            return;
        }
        if(classificationProfile==null){setError("Classifications are still loading. Refresh and try again.");return;}
        showProfileEditor();
    }

    private void renderClassifications(){
        if(classificationProfile!=null&&nameValue!=null){var n=classificationProfile.structuredName();String structured=structuredPreview(n.prefix(),n.firstName(),n.middleName(),n.lastName(),n.preferredName(),n.suffix());nameValue.setText(structured.isBlank()?"—":structured);}
        renderDefinitionChips(contactTypeChips,classificationProfile==null?List.of():classificationProfile.contactTypes(),"No assigned contact types");
        renderDefinitionChips(specialtyChips,classificationProfile==null?List.of():classificationProfile.specialties(),"No assigned specialties");
        if(credentialChips==null)return;credentialChips.getChildren().clear();
        List<ContactServicePort.AssignedCredential> values=classificationProfile==null?List.of():classificationProfile.credentials();
        if(values.isEmpty()){credentialChips.getChildren().add(emptyChip("No assigned credentials"));return;}
        values.stream().sorted(Comparator.comparingInt(ContactServicePort.AssignedCredential::displayOrder).thenComparingLong(ContactServicePort.AssignedCredential::assignmentId)).forEach(a->{Label l=chip(a.definition().abbreviation(),a.definition().color(),a.historical());l.setAccessibleText(a.definition().name()+(a.historical()?" historical":""));l.setTooltip(new Tooltip(a.definition().name()));credentialChips.getChildren().add(l);});
    }
    private void renderDefinitionChips(FlowPane pane,List<ContactServicePort.AssignedDefinition> values,String empty){if(pane==null)return;pane.getChildren().clear();if(values.isEmpty()){pane.getChildren().add(emptyChip(empty));return;}values.stream().sorted(Comparator.comparingInt((ContactServicePort.AssignedDefinition a)->a.definition().sortOrder())).forEach(a->pane.getChildren().add(chip(a.definition().name(),a.definition().color(),a.historical())));}
    private static Label emptyChip(String text){Label l=new Label(text);l.getStyleClass().add("contact-empty-value");return l;}
    private static Label chip(String text,String color,boolean historical){Label l=new Label(text+(historical?" · Historical":""));l.getStyleClass().add("contact-classification-chip");String c=color!=null&&color.matches("#[0-9A-Fa-f]{6}")?color:"#6C757D";int rgb=Integer.parseInt(c.substring(1),16);int r=rgb>>16,g=(rgb>>8)&255,b=rgb&255;String border=(r*299+g*587+b*114)/1000>150?"#425466":"#DCE5EE";l.setStyle("-fx-background-color:"+c+"22;-fx-border-color:"+border+";");return l;}

    private void showProfileEditor(){
        Dialog<Void> dialog=new Dialog<>();AppDialogs.applySecondaryDialogShell(dialog,"Edit Contact");dialog.initOwner(dialogOwner(editButton));
        ButtonType save=new ButtonType("Save",ButtonData.OK_DONE),reload=new ButtonType("Reload",ButtonData.OTHER);dialog.getDialogPane().getButtonTypes().addAll(save,reload,ButtonType.CANCEL);
        var p=classificationProfile;var sn=p.structuredName();
        TextField display=new TextField(p.legacyDisplayName()),prefix=new TextField(safe(sn.prefix())),first=new TextField(safe(sn.firstName())),middle=new TextField(safe(sn.middleName())),last=new TextField(safe(sn.lastName())),preferred=new TextField(safe(sn.preferredName())),suffix=new TextField(safe(sn.suffix()));
        Label preview=new Label();Runnable previewer=()->preview.setText("Structured-name preview: "+structuredPreview(prefix.getText(),first.getText(),middle.getText(),last.getText(),preferred.getText(),suffix.getText()));
        for(TextField f:List.of(prefix,first,middle,last,preferred,suffix))f.textProperty().addListener((o,a,b)->previewer.run());previewer.run();
        javafx.scene.layout.GridPane names=new javafx.scene.layout.GridPane();names.setHgap(10);names.setVgap(8);String[] labels={"Display Name","Prefix","First Name","Middle Name","Last Name","Preferred Name","Suffix"};TextField[] fields={display,prefix,first,middle,last,preferred,suffix};for(int i=0;i<fields.length;i++){names.add(new Label(labels[i]),0,i);names.add(fields[i],1,i);}names.add(preview,0,7,2,1);
        SelectionEditor<ContactServicePort.Definition> types=new SelectionEditor<>(effectiveTypes,p.contactTypes(),ContactServicePort.Definition::id,ContactServicePort.Definition::name,ContactServicePort.Definition::color);
        SelectionEditor<ContactServicePort.Definition> specs=new SelectionEditor<>(effectiveSpecialties,p.specialties(),ContactServicePort.Definition::id,ContactServicePort.Definition::name,ContactServicePort.Definition::color);
        CredentialEditor creds=new CredentialEditor(effectiveCredentials,p.credentials());
        javafx.scene.control.TabPane tabs=new javafx.scene.control.TabPane(tab("Name",names),tab("Contact Types",types.box),tab("Specialties",specs.box),tab("Credentials",creds.box));tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);dialog.getDialogPane().setContent(tabs);dialog.getDialogPane().setPrefSize(720,600);
        Node saveNode=dialog.getDialogPane().lookupButton(save);saveNode.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();if(saveInFlight)return;ContactServicePort.UpdateContactProfileCommand cmd=new ContactServicePort.UpdateContactProfileCommand(contactId,currentContact.shaleClientId(),appState.getUserId(),display.getText(),new ContactServicePort.StructuredName(prefix.getText(),first.getText(),middle.getText(),last.getText(),preferred.getText(),suffix.getText()),p.contactUpdatedAt(),types.intent(),specs.intent(),creds.intent());saveInFlight=true;saveNode.setDisable(true);dbExec.submit(()->{try{var result=contactService.updateContactProfile(cmd);Platform.runLater(()->{if(disposed)return;saveInFlight=false;classificationProfile=result.profile();renderClassifications();contactDetailService.invalidateContact(contactId,currentContact.shaleClientId());dialog.setResult(null);dialog.close();loadContact();});}catch(RuntimeException ex){Platform.runLater(()->{if(disposed)return;saveInFlight=false;boolean stale=ex.getMessage()!=null&&ex.getMessage().toLowerCase(Locale.ROOT).contains("reload");saveNode.setDisable(stale);setError(stale?"This Contact changed elsewhere. Your values are retained; choose Reload before saving again.":"Save failed and was rolled back. Your values are retained.");});}});});
        dialog.getDialogPane().lookupButton(reload).addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();dialog.close();loadContact();});dialog.showAndWait();
    }
    private static javafx.scene.control.Tab tab(String text,Node n){return new javafx.scene.control.Tab(text,new javafx.scene.control.ScrollPane(n));}
    private static String structuredPreview(String...v){return java.util.Arrays.stream(v).map(ContactViewController::safeText).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));}

    private static final class SelectionEditor<T>{final VBox box=new VBox(7);final List<Entry> entries=new java.util.ArrayList<>();record Entry(long assignment,int definition,byte[] rowVer,CheckBox selected){}
        SelectionEditor(List<T> effective,List<ContactServicePort.AssignedDefinition> assigned,java.util.function.ToIntFunction<T> id,java.util.function.Function<T,String> name,java.util.function.Function<T,String> color){Map<Integer,ContactServicePort.AssignedDefinition> current=assigned.stream().collect(java.util.stream.Collectors.toMap(a->a.definition().id(),a->a));for(T d:effective){int did=id.applyAsInt(d);var a=current.remove(did);CheckBox cb=new CheckBox(name.apply(d));applyDefinitionColor(cb,color.apply(d));cb.setSelected(a!=null);box.getChildren().add(cb);entries.add(new Entry(a==null?0:a.assignmentId(),did,a==null?null:a.rowVer(),cb));}for(var a:current.values()){CheckBox cb=new CheckBox(a.definition().name()+" · Historical");applyDefinitionColor(cb,a.definition().color());cb.setSelected(true);box.getChildren().add(cb);entries.add(new Entry(a.assignmentId(),a.definition().id(),a.rowVer(),cb));}}
        List<ContactServicePort.IntendedAssignment> intent(){return entries.stream().map(e->new ContactServicePort.IntendedAssignment(e.assignment,e.definition,e.selected.isSelected(),e.rowVer)).toList();}
        static void applyDefinitionColor(CheckBox cb,String color){String c=color!=null&&color.matches("#[0-9A-Fa-f]{6}")?color:"#6C757D";cb.setStyle("-fx-border-color:"+c+";-fx-border-width:0 0 0 4;-fx-padding:5;");}}
    private static final class CredentialEditor{final VBox box=new VBox(7);final List<CEntry> entries=new java.util.ArrayList<>();record CEntry(long assignment,int definition,byte[] rowVer,CheckBox selected,String label){}
        CredentialEditor(List<ContactServicePort.CredentialDefinition> effective,List<ContactServicePort.AssignedCredential> assigned){Map<Integer,ContactServicePort.AssignedCredential> current=assigned.stream().collect(java.util.stream.Collectors.toMap(a->a.definition().id(),a->a));for(var d:effective){var a=current.remove(d.id());add(a==null?0:a.assignmentId(),d.id(),a==null?null:a.rowVer(),a!=null,d.abbreviation()+" — "+d.name(),d.color());}for(var a:current.values())add(a.assignmentId(),a.definition().id(),a.rowVer(),true,a.definition().abbreviation()+" — "+a.definition().name()+" · Historical",a.definition().color());}
        void add(long aid,int did,byte[]rv,boolean selected,String label,String color){CheckBox cb=new CheckBox(label);SelectionEditor.applyDefinitionColor(cb,color);cb.setSelected(selected);Button up=new Button("Move Up"),down=new Button("Move Down");CEntry e=new CEntry(aid,did,rv,cb,label);entries.add(e);HBox row=new HBox(6,cb,up,down);box.getChildren().add(row);up.setOnAction(x->move(row,-1));down.setOnAction(x->move(row,1));}
        void move(HBox row,int delta){int i=box.getChildren().indexOf(row),j=i+delta;if(j<0||j>=box.getChildren().size())return;box.getChildren().remove(i);box.getChildren().add(j,row);CEntry e=entries.remove(i);entries.add(j,e);}
        List<ContactServicePort.IntendedAssignment> intent(){return entries.stream().map(e->new ContactServicePort.IntendedAssignment(e.assignment,e.definition,e.selected.isSelected(),e.rowVer)).toList();}}

    private void onCancel() {
        if (currentContact != null) {
            writeEditorsFromCurrent();
            renderFromCurrent();
        }
        setEditMode(false);
        clearError();
    }

    private void showContactTextFieldDialog(String title, String label, String currentValue, boolean required, Button ownerButton, Consumer<String> onSave) {
        Dialog<String> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.initOwner(dialogOwner(ownerButton));
        ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField field = new TextField(safe(currentValue));
        ControlStyles.formControl(field);
        Label error = new Label();
        error.getStyleClass().add("dialog-error-text");
        error.setVisible(false);
        error.setManaged(false);
        dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + displayCurrentValue(currentValue)), field, error));

        Node save = dialog.getDialogPane().lookupButton(saveType);
        ControlStyles.apply((javafx.scene.control.ButtonBase) save, ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        save.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (required && safe(field.getText()).trim().isBlank()) {
                error.setText(label + " is required.");
                error.setVisible(true);
                error.setManaged(true);
                e.consume();
            }
        });
        installUnsavedContactDialogConfirmation(dialog, () -> !Objects.equals(safeText(currentValue), safeText(field.getText())));
        dialog.setResultConverter(button -> button == saveType ? field.getText() : null);
        dialog.showAndWait().ifPresent(onSave);
    }

    private void showContactTextAreaDialog(String title, String label, String currentValue, Button ownerButton, Consumer<String> onSave) {
        Dialog<String> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.initOwner(dialogOwner(ownerButton));
        ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextArea area = new TextArea(safe(currentValue));
        ControlStyles.formControl(area);
        area.setPrefRowCount(8);
        area.setWrapText(true);
        dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current value shown below."), area));
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(saveType), ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        installUnsavedContactDialogConfirmation(dialog, () -> !Objects.equals(safeText(currentValue), safeText(area.getText())));
        dialog.setResultConverter(button -> button == saveType ? area.getText() : null);
        dialog.showAndWait().ifPresent(onSave);
    }

    private void showContactDateDialog(String title, String label, LocalDate currentValue, Button ownerButton, Consumer<LocalDate> onSave) {
        Dialog<LocalDate> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.initOwner(dialogOwner(ownerButton));
        ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        DatePicker picker = new DatePicker(currentValue);
        ControlStyles.formControl(picker);
        dialog.getDialogPane().setContent(new VBox(8, new Label(label), new Label("Current: " + formatDate(currentValue)), picker));
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(saveType), ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        installUnsavedContactDialogConfirmation(dialog, () -> !Objects.equals(currentValue, picker.getValue()));
        dialog.setResultConverter(button -> button == saveType ? picker.getValue() : null);
        dialog.showAndWait().ifPresent(onSave);
    }

    private void showContactBooleanDialog(String title, String label, boolean currentValue, Button ownerButton, Consumer<Boolean> onSave) {
        Dialog<Boolean> dialog = new Dialog<>();
        AppDialogs.applySecondaryDialogShell(dialog, title);
        dialog.initOwner(dialogOwner(ownerButton));
        ButtonType saveType = new ButtonType("Save", ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        CheckBox box = new CheckBox(label);
        ControlStyles.formControl(box);
        box.setSelected(currentValue);
        dialog.getDialogPane().setContent(new VBox(8, new Label("Current: " + booleanLabel(currentValue)), box));
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(saveType), ControlStyles.Purpose.PRIMARY);
        ControlStyles.apply((javafx.scene.control.ButtonBase) dialog.getDialogPane().lookupButton(ButtonType.CANCEL), ControlStyles.Purpose.SECONDARY);
        installUnsavedContactDialogConfirmation(dialog, () -> currentValue != box.isSelected());
        dialog.setResultConverter(button -> button == saveType ? box.isSelected() : null);
        dialog.showAndWait().ifPresent(onSave);
    }

    private void installUnsavedContactDialogConfirmation(Dialog<?> dialog, java.util.function.BooleanSupplier hasChanges) {
        Node cancel = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancel == null) {
            return;
        }
        cancel.addEventFilter(javafx.event.ActionEvent.ACTION, e -> {
            if (hasChanges == null || !hasChanges.getAsBoolean()) {
                return;
            }
            boolean confirmed = AppDialogs.showConfirmation(
                    dialog.getOwner(),
                    "Discard Changes?",
                    "Discard unsaved changes?",
                    "Canceling will discard the changes in this field.",
                    "Discard Changes",
                    AppDialogs.DialogActionKind.DANGER);
            if (!confirmed) {
                e.consume();
            }
        });
    }

    private void saveSingleContactField(ContactField field, String value) {
        saveContactProfile(createUpdateRequest(field, safeText(value), null, null));
    }

    private void saveSingleContactField(ContactField field, LocalDate value) {
        saveContactProfile(createUpdateRequest(field, null, value, null));
    }

    private void saveSingleContactField(ContactField field, Boolean value) {
        saveContactProfile(createUpdateRequest(field, null, null, Boolean.TRUE.equals(value)));
    }

    private ContactProfileUpdateRequest createUpdateRequest(ContactField field, String textValue, LocalDate dateValue, Boolean booleanValue) {
        if (currentContact == null) {
            return null;
        }
        String name = currentContact.name();
        String firstName = currentContact.firstName();
        String lastName = currentContact.lastName();
        String email = currentContact.email();
        String phone = currentContact.phone();
        String addressHome = currentContact.addressHome();
        LocalDate dateOfBirth = currentContact.dateOfBirth();
        String condition = currentContact.condition();
        boolean deceased = currentContact.deceased();

        switch (field) {
            case NAME -> name = textValue;
            case FIRST_NAME -> firstName = textValue;
            case LAST_NAME -> lastName = textValue;
            case EMAIL -> email = textValue;
            case PHONE -> phone = textValue;
            case ADDRESS_HOME -> addressHome = textValue;
            case DATE_OF_BIRTH -> dateOfBirth = dateValue;
            case CONDITION -> condition = textValue;
            case DECEASED -> deceased = Boolean.TRUE.equals(booleanValue);
        }

        return new ContactProfileUpdateRequest(
                currentContact.id(),
                currentContact.shaleClientId(),
                appState == null ? null : appState.getUserId(),
                name,
                firstName,
                lastName,
                email,
                phone,
                addressHome,
                dateOfBirth,
                condition,
                deceased,
                currentContact.client());
    }

    private void onSave() {
        if (!canEditContact()) {
            setError("You do not have permission to save this contact.");
            return;
        }
        if (currentContact == null || contactDetailService == null) {
            setError("Contact details are unavailable.");
            return;
        }

        ContactProfileUpdateRequest request = new ContactProfileUpdateRequest(
                currentContact.id(),
                currentContact.shaleClientId(),
                appState == null ? null : appState.getUserId(),
                safeText(nameEditor == null ? null : nameEditor.getText()),
                safeText(firstNameEditor == null ? null : firstNameEditor.getText()),
                safeText(lastNameEditor == null ? null : lastNameEditor.getText()),
                safeText(emailEditor == null ? null : emailEditor.getText()),
                safeText(phoneEditor == null ? null : phoneEditor.getText()),
                safeText(addressHomeEditor == null ? null : addressHomeEditor.getText()),
                dateOfBirthEditor == null ? null : dateOfBirthEditor.getValue(),
                safeText(conditionEditor == null ? null : conditionEditor.getText()),
                deceasedEditor != null && deceasedEditor.isSelected(),
                currentContact.client());
        saveContactProfile(request);
    }

    private void saveContactProfile(ContactProfileUpdateRequest request) {
        if (!canEditContact()) {
            setError("You do not have permission to save this contact.");
            return;
        }
        if (request == null || contactDetailService == null) {
            setError("Contact details are unavailable.");
            return;
        }

        long saveStarted = PerfLog.start();
        PerfLog.log("contacts.save", "start", "contactId=" + request.contactId() + " tenantId=" + request.shaleClientId());
        setBusy(true);
        dbExec.submit(() -> {
            try {
                boolean updated = contactDetailService.updateBasicProfile(request);
                if (!updated) {
                    Platform.runLater(() -> {
                        setBusy(false);
                        setError("Contact could not be saved.");
                    });
                    return;
                }

                ContactDetailService.ContactDetailSnapshot reloadedSnapshot = contactDetailService.loadSnapshot(request.contactId(), request.shaleClientId());
                ContactDetailRow reloaded = reloadedSnapshot.contact();
                List<RelatedCaseRow> reloadedRelatedCases = reloadedSnapshot.relatedCases();
                Platform.runLater(() -> {
                    setBusy(false);
                    if (reloaded == null) {
                        setError("Contact could not be reloaded after save.");
                        return;
                    }
                    currentContact = reloaded;
                    relatedCases = reloadedRelatedCases == null ? List.of() : reloadedRelatedCases;
                    renderFromCurrent();
                    renderRelatedCases();
                    loadSharedLinks();
                    setEditMode(false);
                    clearError();
                    PerfLog.logDone("contacts.save", "phase=apply contactId=" + currentContact.id() + " relatedCases=" + relatedCases.size(), saveStarted);
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    setError("Failed to save contact.");
                });
            }
        });
    }

    private void onDeleteContact() {
        if (contactDetailService == null || currentContact == null) {
            setError("Contact details are unavailable.");
            return;
        }
        if (!isAdminUser()) {
            setError("Only admin users can delete contacts.");
            return;
        }
        if (!confirmDeleteContact()) {
            return;
        }

        setBusy(true);
        dbExec.submit(() -> {
            try {
                boolean deleted = contactDetailService.softDeleteContact(currentContact.id(), currentContact.shaleClientId());
                Platform.runLater(() -> {
                    setBusy(false);
                    if (!deleted) {
                        showDeleteFailure("Contact could not be deleted.");
                        return;
                    }
                    clearError();
                    navigateAfterDelete();
                });
            } catch (RuntimeException ex) {
                Platform.runLater(() -> {
                    setBusy(false);
                    showDeleteFailure("Failed to delete contact.");
                });
            }
        });
    }

    private boolean confirmDeleteContact() {
        Window owner = dialogOwner(deleteContactButton);
        if (owner == null) {
            owner = dialogOwner(editButton);
        }
        String contactName = preferredContactName(currentContact);
        String message = contactName == null
                ? "This will remove it from active views."
                : contactName + " will be removed from active views.";
        return AppDialogs.showConfirmation(
                owner,
                "Delete Contact",
                "Delete this contact?",
                message,
                "Delete Contact",
                AppDialogs.DialogActionKind.DANGER);
    }

    private void showDeleteFailure(String message) {
        setError(message);
        AppDialogs.showError(dialogOwner(deleteContactButton), "Delete Contact", message);
    }

    private Window dialogOwner(Button button) {
        if (button != null && button.getScene() != null) {
            return button.getScene().getWindow();
        }
        return null;
    }

    private void navigateAfterDelete() {
        if (onContactDeleted != null) {
            onContactDeleted.run();
        }
    }

    private void renderFromCurrent() {
        if (currentContact == null) {
            return;
        }

        if (contactTitleLabel != null) {
            contactTitleLabel.setText(fallback(currentContact.displayName(), "Contact"));
        }
        if (contactSubtitleLabel != null) {
            contactSubtitleLabel.setText("Contact #" + currentContact.id());
        }
        if (lastUpdatedLabel != null) {
            lastUpdatedLabel.setText("Last updated: " + ContactDao.formatTimestamp(currentContact.updatedAt()));
        }

        if (displayNameValue != null) {
            displayNameValue.setText(fallback(currentContact.displayName()));
        }
        if (nameValue != null) {
            nameValue.setText(fallback(currentContact.name()));
        }
        if (firstNameValue != null) {
            firstNameValue.setText(fallback(currentContact.firstName()));
        }
        if (lastNameValue != null) {
            lastNameValue.setText(fallback(currentContact.lastName()));
        }
        if (emailValue != null) {
            emailValue.setText(fallback(currentContact.email()));
        }
        if (phoneValue != null) {
            phoneValue.setText(fallback(currentContact.phone()));
        }
        if (addressHomeValue != null) {
            addressHomeValue.setText(fallback(currentContact.addressHome()));
        }
        if (dateOfBirthValue != null) {
            dateOfBirthValue.setText(formatDate(currentContact.dateOfBirth()));
        }
        if (conditionValue != null) {
            conditionValue.setText(fallback(currentContact.condition()));
        }
        if (deceasedValue != null) {
            deceasedValue.setText(booleanLabel(currentContact.deceased()));
        }

        writeEditorsFromCurrent();
    }

    private void writeEditorsFromCurrent() {
        if (currentContact == null) {
            return;
        }
        if (nameEditor != null) {
            nameEditor.setText(safe(currentContact.name()));
        }
        if (firstNameEditor != null) {
            firstNameEditor.setText(safe(currentContact.firstName()));
        }
        if (lastNameEditor != null) {
            lastNameEditor.setText(safe(currentContact.lastName()));
        }
        if (emailEditor != null) {
            emailEditor.setText(safe(currentContact.email()));
        }
        if (phoneEditor != null) {
            phoneEditor.setText(safe(currentContact.phone()));
        }
        if (addressHomeEditor != null) {
            addressHomeEditor.setText(safe(currentContact.addressHome()));
        }
        if (dateOfBirthEditor != null) {
            dateOfBirthEditor.setValue(currentContact.dateOfBirth());
        }
        if (conditionEditor != null) {
            conditionEditor.setText(safe(currentContact.condition()));
        }
        if (deceasedEditor != null) {
            deceasedEditor.setSelected(currentContact.deceased());
        }
    }


    private void resetSharedLinksState() {
        sharedLinks = List.of();
        sharedLinksLoaded = false;
        renderSharedLinksLoading();
    }

    private void loadSharedLinks() {
        if (disposed) {
            return;
        }
        final int generation = ++sharedLinksLoadGeneration;
        final int requestedContactId = contactId;
        Integer tenantId = appState == null ? null : appState.getShaleClientId();
        if (caseService == null || tenantId == null || tenantId <= 0 || requestedContactId <= 0) {
            LOG.info(() -> "operation=contacts.sharedLinks.skip tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation);
            sharedLinks = List.of();
            sharedLinksLoaded = true;
            renderSharedLinksEmpty();
            return;
        }
        long loadStarted = PerfLog.start();
        LOG.info(() -> "operation=contacts.sharedLinks.load tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation);
        renderSharedLinksLoading();
        dbExec.submit(() -> {
            try {
                List<ContactSharedCaseLinkDto> loaded = caseService.listCaseLinksSharedWithContact(requestedContactId, tenantId);
                List<ContactSharedCaseLinkDto> safe = loaded == null ? List.of() : List.copyOf(loaded);
                Platform.runLater(() -> {
                    if (generation != sharedLinksLoadGeneration || contactId != requestedContactId) {
                        LOG.info(() -> "operation=contacts.sharedLinks.stale tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation + " daoResultCount=" + safe.size() + " elapsedMs=" + (PerfLog.elapsedMs(loadStarted)));
                        PerfLog.log("contacts.sharedLinks", "discard", "contactId=" + requestedContactId + " tenantId=" + tenantId + " generation=" + generation + " rows=" + safe.size());
                        return;
                    }
                    sharedLinks = safe;
                    sharedLinksLoaded = true;
                    renderSharedLinks();
                    LOG.info(() -> "operation=contacts.sharedLinks.success tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation + " daoResultCount=" + safe.size() + " mappedResultCount=" + sharedLinks.size() + " caseGroupCount=" + sharedLinks.stream().map(ContactSharedCaseLinkDto::caseId).distinct().count() + " elapsedMs=" + (PerfLog.elapsedMs(loadStarted)));
                });
            } catch (RuntimeException ex) {
                LOG.log(Level.WARNING, "operation=contacts.sharedLinks.failure tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation + " elapsedMs=" + (PerfLog.elapsedMs(loadStarted)), ex);
                Platform.runLater(() -> {
                    if (generation != sharedLinksLoadGeneration || contactId != requestedContactId) {
                        LOG.info(() -> "operation=contacts.sharedLinks.failureStale tenantId=" + tenantId + " contactId=" + requestedContactId + " generation=" + generation);
                        return;
                    }
                    sharedLinks = List.of();
                    sharedLinksLoaded = false;
                    renderSharedLinksFailure();
                });
            }
        });
    }

    private void renderSharedLinksLoading() { showSharedLinksStatus("Loading shared links…"); }
    private void renderSharedLinksEmpty() { showSharedLinksStatus("No active links are currently shared with this contact."); }
    private void renderSharedLinksFailure() { showSharedLinksStatus("Unable to load links shared with this contact."); }

    private void showSharedLinksStatus(String message) {
        if (sharedLinksContainer != null) sharedLinksContainer.getChildren().clear();
        if (sharedLinksStatusLabel != null) {
            sharedLinksStatusLabel.setText(message == null ? "" : message);
            setVisibleManaged(sharedLinksStatusLabel, message != null && !message.isBlank());
        }
    }

    private void renderSharedLinks() {
        if (!Platform.isFxApplicationThread()) { Platform.runLater(this::renderSharedLinks); return; }
        if (sharedLinksContainer == null) return;
        sharedLinksContainer.getChildren().clear();
        if (sharedLinksStatusLabel != null) setVisibleManaged(sharedLinksStatusLabel, false);
        if (sharedLinks == null || sharedLinks.isEmpty()) { renderSharedLinksEmpty(); return; }
        var groups = sharedLinks.stream()
                .sorted(Comparator.comparing((ContactSharedCaseLinkDto r) -> caseNameSortKey(r.caseDisplayName()), Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparingLong(ContactSharedCaseLinkDto::caseId)
                        .thenComparing((ContactSharedCaseLinkDto r) -> !r.caseLink().primary())
                        .thenComparing(r -> caseNameSortKey(r.caseLink().linkTypeName()), Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparingInt(r -> r.caseLink().sortOrder())
                        .thenComparing(r -> caseNameSortKey(r.caseLink().displayName()), Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparingLong(r -> r.caseLink().caseLinkId()))
                .collect(java.util.stream.Collectors.groupingBy(ContactSharedCaseLinkDto::caseId, java.util.LinkedHashMap::new, java.util.stream.Collectors.toList()));
        for (List<ContactSharedCaseLinkDto> rows : groups.values()) {
            if (rows.isEmpty()) continue;
            ContactSharedCaseLinkDto first = rows.get(0);
            VBox group = new VBox(8);
            group.getStyleClass().add("contact-shared-links-case-group");
            HBox heading = new HBox(8);
            Label title = new Label(fallback(first.caseDisplayName()) + " (" + rows.size() + (rows.size() == 1 ? " link" : " links") + ")");
            title.setWrapText(true);
            title.getStyleClass().add("case-overview-row-value");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            Button openCase = new Button("Open Case");
            ControlStyles.apply(openCase, ControlStyles.Purpose.NAVIGATION, ControlStyles.Size.SMALL);
            openCase.setOnAction(e -> { if (onOpenCase != null) onOpenCase.accept((int) first.caseId()); });
            heading.getChildren().addAll(title, spacer, openCase);
            group.getChildren().add(heading);
            for (ContactSharedCaseLinkDto row : rows) {
                CaseLinkDto link = row.caseLink();
                Node card = caseLinkCardFactory.createReadOnly(link, CaseLinkCardFactory.Variant.COMPACT,
                        new CaseLinkCardFactory.Actions(() -> openSharedLink(link), null, null, null), onOpenContact);
                if (card instanceof Region region) region.setMaxWidth(Double.MAX_VALUE);
                group.getChildren().add(card);
            }
            sharedLinksContainer.getChildren().add(group);
        }
    }

    private void openSharedLink(CaseLinkDto link) {
        try { externalBrowserHelper.openHttpOrHttps(link.url()); }
        catch (RuntimeException ex) { AppDialogs.showError(dialogOwner(editButton), "Open Link", ex.getMessage() == null ? "Unable to open link." : ex.getMessage()); }
    }

    private void renderRelatedCases() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::renderRelatedCases);
            return;
        }

        long renderStarted = PerfLog.start();
        if (relatedCasesContainer == null) {
            return;
        }

        if (caseCardFactory == null) {
            caseCardFactory = new CaseCardFactory(onOpenCase);
        }

        List<Node> cards = relatedCases.stream()
                .sorted(Comparator.comparing(
                        (RelatedCaseRow row) -> caseNameSortKey(row == null ? null : row.summary().caseName()),
                        Comparator.nullsLast(String::compareToIgnoreCase)))
                .map(this::createRelatedCaseCard)
                .toList();

        relatedCasesContainer.getChildren().setAll(cards);

        boolean empty = cards.isEmpty();
        if (relatedCasesEmptyLabel != null) {
            relatedCasesEmptyLabel.setVisible(empty);
            relatedCasesEmptyLabel.setManaged(empty);
            if (!empty) {
                relatedCasesEmptyLabel.toBack();
            }
        }
        PerfLog.logDone("contacts.relatedCases.render", "contactId=" + contactId + " cards=" + cards.size() + " fxThread=" + Platform.isFxApplicationThread(), renderStarted);
    }

    private Node createRelatedCaseCard(RelatedCaseRow row) {
        Node card = caseCardFactory.create(toRelatedCaseCardModel(row), CaseCardFactory.Variant.FULL);
        if (card instanceof Region region) {
            region.setMaxWidth(Double.MAX_VALUE);
            region.setPrefWidth(380);
            region.setMaxWidth(420);
        }
        Label relationshipMeta = new Label(formatRelationshipMeta(row.partyRoleName(), row.side(), row.primary()));
        relationshipMeta.getStyleClass().add("muted");
        relationshipMeta.setWrapText(true);
        VBox container = new VBox(4, card, relationshipMeta);
        VBox.setVgrow(container, javafx.scene.layout.Priority.NEVER);
        return container;
    }

    static CaseCardModel toRelatedCaseCardModel(RelatedCaseRow row) {
        return new CaseCardModel(
                row.summary().caseId(),
                row.summary().caseName(),
                row.intakeDate(),
                row.statuteOfLimitationsDate(),
                row.tortClaimsNoticeDeadline(),
                row.summary().responsibleAttorneyName(),
                row.summary().responsibleAttorneyColor(),
                row.nonEngagementLetterSent(),
                row.summary().primaryStatusName(),
                row.summary().primaryStatusColor(),
                row.practiceAreaColor());
    }


    private static String caseNameSortKey(String name) {
        String trimmed = safe(name).trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.toLowerCase(Locale.ROOT);
    }

    private static String formatRelationshipMeta(String roleName, String side, boolean primary) {
        String role = safe(roleName).isBlank() ? "Relationship" : safe(roleName).trim();
        String sideLabel = safe(side).isBlank() ? "unclassified" : safe(side).trim();
        return primary ? role + " • " + sideLabel + " • primary" : role + " • " + sideLabel;
    }

    private void setEditMode(boolean enabled) {
        this.editMode = enabled && canEditContact();

        setVisibleManaged(editButton, canEditContact() && !editMode && currentContact != null);
        setVisibleManaged(saveButton, canEditContact() && editMode);
        setVisibleManaged(cancelButton, canEditContact() && editMode);
        refreshDeleteAction();

        toggleField(nameValue, nameEditor, editMode);
        toggleField(firstNameValue, firstNameEditor, editMode);
        toggleField(lastNameValue, lastNameEditor, editMode);
        toggleField(emailValue, emailEditor, editMode);
        toggleField(phoneValue, phoneEditor, editMode);
        toggleField(addressHomeValue, addressHomeEditor, editMode);
        toggleField(dateOfBirthValue, dateOfBirthEditor, editMode);
        toggleField(conditionValue, conditionEditor, editMode);
        toggleField(deceasedValue, deceasedEditor, editMode);
        setInlineEditButtonsDisabled(!canEditContact() || currentContact == null);
    }

    private void setBusy(boolean busy) {
        if (editButton != null) {
            editButton.setDisable(busy);
        }
        if (saveButton != null) {
            saveButton.setDisable(busy);
        }
        if (cancelButton != null) {
            cancelButton.setDisable(busy);
        }
        if (deleteContactButton != null) {
            deleteContactButton.setDisable(busy);
        }
        setInlineEditButtonsDisabled(busy || !canEditContact() || currentContact == null);
    }

    private void setInlineEditButtonsDisabled(boolean disabled) {
        for (Button button : java.util.stream.Stream.of(editDisplayNameButton, editNameButton, editFirstNameButton, editLastNameButton,
                editEmailButton, editPhoneButton, editAddressHomeButton, editDateOfBirthButton, editConditionButton,
                editDeceasedButton).filter(Objects::nonNull).toList()) {
            if (button != null) {
                button.setDisable(disabled);
            }
        }
    }

    private void refreshDeleteAction() {
        boolean showDelete = isAdminUser() && !editMode && currentContact != null;
        setVisibleManaged(deleteContactButton, showDelete);
    }

    private boolean canEditContact() {
        Integer userId = appState == null ? null : appState.getUserId();
        return userId != null && userId > 0;
    }

    private boolean isAdminUser() {
        return appState != null && appState.isAdmin();
    }

    private void clearError() {
        setVisibleManaged(errorLabel, false);
        if (errorLabel != null) {
            errorLabel.setText("");
        }
    }

    private void setError(String message) {
        if (errorLabel == null) {
            return;
        }
        errorLabel.setText(message == null ? "" : message);
        setVisibleManaged(errorLabel, message != null && !message.isBlank());
    }

    private static String fallback(String value) {
        return fallback(value, "—");
    }

    private static String fallback(String value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? defaultValue : trimmed;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeText(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String booleanLabel(boolean value) {
        return value ? "Yes" : "No";
    }

    private static String preferredContactName(ContactDetailRow row) {
        if (row == null) {
            return null;
        }
        String displayName = safeText(row.displayName());
        if (displayName != null) {
            return displayName;
        }
        String combinedName = safeText((safe(row.firstName()) + " " + safe(row.lastName())).trim());
        if (combinedName != null) {
            return combinedName;
        }
        return safeText(row.name());
    }

    private static String formatDate(LocalDate value) {
        return value == null ? "—" : value.toString();
    }

    private static String displayCurrentValue(String value) {
        String safe = safeText(value);
        return safe == null ? "—" : safe;
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private static void toggleField(Node readOnlyNode, Node editorNode, boolean editing) {
        if (editorNode instanceof TextInputControl textInput) {
            setVisibleManaged(readOnlyNode, false);
            setVisibleManaged(editorNode, true);
            ReadOnlyTextDisplaySupport.apply(textInput, editing);
            return;
        }
        setVisibleManaged(readOnlyNode, !editing);
        setVisibleManaged(editorNode, editing);
    }

    private enum ContactField {
        NAME,
        FIRST_NAME,
        LAST_NAME,
        EMAIL,
        PHONE,
        ADDRESS_HOME,
        DATE_OF_BIRTH,
        CONDITION,
        DECEASED
    }
}
