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
import com.shale.ui.util.ContactExternalActions;
import com.shale.ui.util.PerfLog;
import com.shale.ui.state.AppState;

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
import javafx.scene.control.Tooltip;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.ScrollPane;
import javafx.geometry.Pos;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.stage.Window;

public final class ContactViewController {

    private static final Logger LOG = Logger.getLogger(ContactViewController.class.getName());

    @FXML private Label contactTitleLabel;
    @FXML private Label contactSubtitleLabel;
    @FXML private Label lastUpdatedLabel;
    @FXML private Label errorLabel;
    @FXML private Button editButton;
    @FXML private Button deleteContactButton;
    @FXML private VBox relatedCasesContainer;
    @FXML private Label relatedCasesEmptyLabel;
    @FXML private VBox sharedLinksContainer;
    @FXML private Label sharedLinksStatusLabel;
    @FXML private FlowPane contactTypeChips;
    @FXML private FlowPane specialtyChips;
    @FXML private FlowPane credentialChips;
    @FXML private BorderPane rootPane;
    @FXML private GridPane profileGrid;
    @FXML private VBox relatedSidebar;
    @FXML private TilePane phoneCards;
    @FXML private TilePane emailCards;
    @FXML private VBox addressCards;
    @FXML private Label structuredFullNameValue;
    @FXML private Label preferredNameLabel;
    @FXML private Label preferredNameValue;
    @FXML private Label dateOfBirthValue;
    @FXML private Label conditionValue;
    @FXML private Label deceasedValue;

    private int contactId;
    private ContactDetailService contactDetailService;
    private AppState appState;
    private ContactDetailRow currentContact;
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
    private ContactExternalActions contactExternalActions = new ContactExternalActions();
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
        if (deleteContactButton != null) {
            ControlStyles.apply(deleteContactButton, ControlStyles.Purpose.DANGER);
            deleteContactButton.setOnAction(e -> onDeleteContact());
            setVisibleManaged(deleteContactButton, false);
        }

        initialized = true;
        if(rootPane!=null)rootPane.sceneProperty().addListener((o,oldScene,newScene)->{if(oldScene!=null&&newScene==null)dispose();});
        if(profileGrid!=null)profileGrid.widthProperty().addListener((o,a,width)->applyResponsiveLayout(width.doubleValue()));
        if(phoneCards!=null)phoneCards.widthProperty().addListener((o,a,width)->configureCardTiles(phoneCards));
        if(emailCards!=null)emailCards.widthProperty().addListener((o,a,width)->configureCardTiles(emailCards));
        refreshContactActions();
        renderRelatedCases();
        resetSharedLinksState();
        Platform.runLater(() -> { loadContact(); loadSharedLinks(); });
    }

    public void dispose(){if(disposed)return;disposed=true;detailLoadGeneration++;sharedLinksLoadGeneration++;if(runtimeBridge!=null)runtimeBridge.unsubscribeEntityUpdated(sharedLinksLiveHandler);dbExec.shutdownNow();}
    private void applyResponsiveLayout(double width) {
        if (relatedSidebar == null || profileGrid.getColumnConstraints().size() < 2) return;
        boolean narrow = width < 900;
        profileGrid.getColumnConstraints().get(0).setPercentWidth(narrow ? 100 : 63);
        profileGrid.getColumnConstraints().get(1).setPercentWidth(narrow ? 0 : 37);
        GridPane.setColumnIndex(relatedSidebar, narrow ? 0 : 1);
        GridPane.setRowIndex(relatedSidebar, narrow ? 2 : 0);
        GridPane.setRowSpan(relatedSidebar, narrow ? 1 : 2);
        Platform.runLater(this::updateContactMethodColumns);
    }

    private void updateContactMethodColumns() {
        configureCardTiles(phoneCards);
        configureCardTiles(emailCards);
    }

    private static void configureCardTiles(TilePane pane) {
        if (pane == null) return;
        double width = pane.getWidth();
        boolean twoColumns = width >= 600;
        pane.setPrefColumns(twoColumns ? 2 : 1);
        pane.setPrefTileWidth(twoColumns ? Math.max(250, (width - pane.getHgap()) / 2) : Math.max(250, width));
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
                    renderContactPoints();
                    loadSharedLinks();
                    refreshContactActions();
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
        renderDefinitionChips(contactTypeChips,classificationProfile==null?List.of():classificationProfile.contactTypes(),"No assigned contact types");
        renderDefinitionChips(specialtyChips,classificationProfile==null?List.of():classificationProfile.specialties(),"No assigned specialties");
        if(credentialChips==null)return;credentialChips.getChildren().clear();
        List<ContactServicePort.AssignedCredential> values=classificationProfile==null?List.of():classificationProfile.credentials();
        if(values.isEmpty()){credentialChips.getChildren().add(emptyChip("No assigned credentials"));return;}
        values.stream().sorted(Comparator.comparingInt(ContactServicePort.AssignedCredential::displayOrder).thenComparingLong(ContactServicePort.AssignedCredential::assignmentId)).forEach(a->{Label l=chip(a.definition().abbreviation(),a.definition().color(),a.historical());l.setAccessibleText(a.definition().name()+(a.historical()?" historical":""));l.setTooltip(new Tooltip(a.definition().name()));credentialChips.getChildren().add(l);});
    }

    private void renderContactPoints(){
        if(phoneCards==null||emailCards==null||addressCards==null)return;
        phoneCards.getChildren().clear();emailCards.getChildren().clear();addressCards.getChildren().clear();
        if(classificationProfile==null)return;
        classificationProfile.phoneNumbers().stream().filter(x->!x.deleted()).sorted(Comparator.comparing(ContactServicePort.ContactPhoneNumber::primary).reversed().thenComparingInt(ContactServicePort.ContactPhoneNumber::sortOrder)).forEach(x->phoneCards.getChildren().add(viewPointCard(x.displayNumber(),x.kind(),x.primary(),"Call",()->openExternal("call",()->contactExternalActions.open(ContactExternalActions.telephone(x.displayNumber()))))));
        classificationProfile.emailAddresses().stream().filter(x->!x.deleted()).sorted(Comparator.comparing(ContactServicePort.ContactEmailAddress::primary).reversed().thenComparingInt(ContactServicePort.ContactEmailAddress::sortOrder)).forEach(x->emailCards.getChildren().add(viewPointCard(x.emailAddress(),x.kind(),x.primary(),"Email",()->openExternal("email",()->contactExternalActions.open(ContactExternalActions.email(x.emailAddress()))))));
        classificationProfile.addresses().stream().filter(x->!x.deleted()).sorted(Comparator.comparing(ContactServicePort.ContactAddress::primary).reversed().thenComparingInt(ContactServicePort.ContactAddress::sortOrder)).forEach(x->{String value=addressText(x);addressCards.getChildren().add(viewPointCard(value,x.kind()+(legacyOnly(x)?" · Legacy":""),x.primary(),"Open in Maps",()->openExternal("maps",()->externalBrowserHelper.openHttpOrHttps(ContactExternalActions.maps(value).toString()))));});
        if(phoneCards.getChildren().isEmpty())phoneCards.getChildren().add(emptyChip("No phone numbers"));
        if(emailCards.getChildren().isEmpty())emailCards.getChildren().add(emptyChip("No email addresses"));
        if(addressCards.getChildren().isEmpty())addressCards.getChildren().add(emptyChip("No addresses"));
        updateContactMethodColumns();
    }
    private Node viewPointCard(String value,String kind,boolean primary,String action,Runnable run){Label v=new Label(fallback(value));v.setWrapText(true);v.getStyleClass().add("contact-point-value");Label k=badge(kind,false),p=badge("Primary",true);Button b=new Button(action);ControlStyles.apply(b,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);b.setOnAction(e->run.run());HBox top=new HBox(6,k);if(primary)top.getChildren().add(p);Region spacer=new Region();HBox.setHgrow(spacer,Priority.ALWAYS);top.getChildren().addAll(spacer,b);VBox card=new VBox(7,top,v);card.getStyleClass().add("contact-point-card");return card;}
    private static Label badge(String text,boolean primary){Label l=new Label(text);l.getStyleClass().add("contact-point-badge");if(primary)l.getStyleClass().add("contact-point-primary");return l;}
    private void openExternal(String category,Runnable action){try{action.run();LOG.info(()->"operation=contact.external-action contactId="+contactId+" category="+category+" result=success");}catch(RuntimeException ex){LOG.info(()->"operation=contact.external-action contactId="+contactId+" category="+category+" result=unavailable");setError("No application is available for that action.");}}
    private static boolean legacyOnly(ContactServicePort.ContactAddress x){return !safe(x.legacyAddressText()).isBlank()&&java.util.stream.Stream.of(x.addressLine1(),x.addressLine2(),x.city(),x.stateOrProvince(),x.postalCode(),x.countryCode()).allMatch(v->safe(v).isBlank());}
    private static String addressText(ContactServicePort.ContactAddress x){if(legacyOnly(x))return x.legacyAddressText();return java.util.stream.Stream.of(x.addressLine1(),x.addressLine2(),x.city(),x.stateOrProvince(),x.postalCode(),x.countryCode()).map(ContactViewController::safeText).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));}
    private void renderDefinitionChips(FlowPane pane,List<ContactServicePort.AssignedDefinition> values,String empty){if(pane==null)return;pane.getChildren().clear();if(values.isEmpty()){pane.getChildren().add(emptyChip(empty));return;}values.stream().sorted(Comparator.comparingInt((ContactServicePort.AssignedDefinition a)->a.definition().sortOrder())).forEach(a->pane.getChildren().add(chip(a.definition().name(),a.definition().color(),a.historical())));}
    private static Label emptyChip(String text){Label l=new Label(text);l.getStyleClass().add("contact-empty-state");return l;}
    private static Label chip(String text,String color,boolean historical){Label l=new Label(text+(historical?" · Historical":""));l.getStyleClass().add("contact-classification-chip");String c=color!=null&&color.matches("#[0-9A-Fa-f]{6}")?color:"#6C757D";int rgb=Integer.parseInt(c.substring(1),16);int r=rgb>>16,g=(rgb>>8)&255,b=rgb&255;String border=(r*299+g*587+b*114)/1000>150?"#425466":"#DCE5EE";l.setStyle("-fx-background-color:"+c+"22;-fx-border-color:"+border+";");return l;}

    private void showProfileEditor(){
        Dialog<Void> dialog=new Dialog<>();AppDialogs.applySecondaryDialogShell(dialog,"Edit Contact");dialog.initOwner(dialogOwner(editButton));
        ButtonType save=new ButtonType("Save",ButtonData.OK_DONE),reload=new ButtonType("Reload",ButtonData.OTHER);dialog.getDialogPane().getButtonTypes().addAll(save,reload,ButtonType.CANCEL);
        var p=classificationProfile;var sn=p.structuredName();
        TextField display=new TextField(safe(p.legacyDisplayName())),prefix=new TextField(safe(sn.prefix())),first=new TextField(safe(sn.firstName())),middle=new TextField(safe(sn.middleName())),last=new TextField(safe(sn.lastName())),preferred=new TextField(safe(sn.preferredName())),suffix=new TextField(safe(sn.suffix()));
        DatePicker birth=new DatePicker(p.dateOfBirth());TextArea condition=new TextArea(safe(p.condition()));condition.setWrapText(true);condition.setPrefRowCount(4);CheckBox deceased=new CheckBox("This contact is deceased");deceased.setSelected(p.deceased());
        Label preview=new Label();preview.setWrapText(true);preview.getStyleClass().add("contact-editor-name-preview");
        for(javafx.scene.control.Control field:List.of(display,prefix,first,middle,last,preferred,suffix,birth,condition,deceased)){ControlStyles.formControl(field);field.setMaxWidth(Double.MAX_VALUE);}
        GridPane details=new GridPane();details.setHgap(12);details.setVgap(9);details.getStyleClass().add("contact-editor-name-form");ColumnConstraints left=new ColumnConstraints();left.setPercentWidth(50);left.setHgrow(Priority.ALWAYS);ColumnConstraints right=new ColumnConstraints();right.setPercentWidth(50);right.setHgrow(Priority.ALWAYS);details.getColumnConstraints().addAll(left,right);
        details.add(formField("Display Name",display),0,0,2,1);details.add(formField("Prefix",prefix),0,1);details.add(formField("First Name",first),1,1);details.add(formField("Middle Name",middle),0,2);details.add(formField("Last Name",last),1,2);details.add(formField("Preferred Name",preferred),0,3);details.add(formField("Suffix",suffix),1,3);details.add(preview,0,4,2,1);details.add(formField("Date of Birth",birth),0,5);details.add(formField("Condition",condition),0,6,2,1);details.add(deceased,0,7,2,1);
        PhoneEditor phones=new PhoneEditor(p.phoneNumbers());EmailEditor emails=new EmailEditor(p.emailAddresses());AddressEditor addresses=new AddressEditor(p.addresses());
        SelectionEditor<ContactServicePort.Definition> types=new SelectionEditor<>(effectiveTypes,p.contactTypes(),ContactServicePort.Definition::id,ContactServicePort.Definition::name,ContactServicePort.Definition::color);SelectionEditor<ContactServicePort.Definition> specs=new SelectionEditor<>(effectiveSpecialties,p.specialties(),ContactServicePort.Definition::id,ContactServicePort.Definition::name,ContactServicePort.Definition::color);final CredentialEditor[] credentialEditor=new CredentialEditor[1];Runnable previewer=()->{List<ContactServicePort.AssignedCredential> selected=credentialEditor[0]==null?p.credentials():credentialEditor[0].previewCredentials();preview.setText("Displayed as: "+com.shale.core.service.ContactNamePresentation.effectiveDisplayName(display.getText(),selected)+"\nStructured-name preview: "+com.shale.core.service.ContactNamePresentation.structuredFullName(new ContactServicePort.StructuredName(prefix.getText(),first.getText(),middle.getText(),last.getText(),preferred.getText(),suffix.getText()),selected));};CredentialEditor creds=new CredentialEditor(effectiveCredentials,p.credentials(),previewer);credentialEditor[0]=creds;
        for(TextField f:List.of(display,prefix,first,middle,last,preferred,suffix))f.textProperty().addListener((o,a,b)->previewer.run());previewer.run();
        VBox classifications=new VBox(10,heading("Contact Types"),types.box,heading("Specialties"),specs.box,heading("Credentials"),creds.box);
        StackPane content=new StackPane();content.getStyleClass().add("contact-editor-content");List<Node> sections=List.of(sectionScroll(details),sectionScroll(phones.box),sectionScroll(emails.box),sectionScroll(addresses.box),sectionScroll(classifications));content.getChildren().addAll(sections);sections.forEach(n->{n.setVisible(false);n.setManaged(false);});
        ToggleGroup group=new ToggleGroup();HBox navigation=new HBox(4);navigation.getStyleClass().add("contact-editor-navigation");String[] names={"Details","Phones","Emails","Addresses","Classifications"};for(int i=0;i<names.length;i++){final int index=i;ToggleButton button=new ToggleButton(names[i]);button.setToggleGroup(group);button.getStyleClass().add("contact-editor-navigation-button");button.setMaxWidth(Double.MAX_VALUE);HBox.setHgrow(button,Priority.ALWAYS);button.setOnAction(e->showEditorSection(sections,index));navigation.getChildren().add(button);}((ToggleButton)navigation.getChildren().get(0)).setSelected(true);showEditorSection(sections,0);group.selectedToggleProperty().addListener((o,old,n)->{if(n==null&&old!=null)old.setSelected(true);});
        Label status=new Label();status.setWrapText(true);status.getStyleClass().add("dialog-error-text");status.setVisible(false);status.setManaged(false);VBox shell=new VBox(10,navigation,status,content);shell.getStyleClass().add("contact-editor-surface");VBox.setVgrow(content,Priority.ALWAYS);dialog.getDialogPane().setContent(shell);dialog.getDialogPane().getStyleClass().add("contact-editor-dialog");dialog.getDialogPane().setPrefSize(900,680);
        Button saveButton=(Button)dialog.getDialogPane().lookupButton(save);ControlStyles.apply(saveButton,ControlStyles.Purpose.PRIMARY);ControlStyles.apply((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL),ControlStyles.Purpose.SECONDARY);ControlStyles.apply((Button)dialog.getDialogPane().lookupButton(reload),ControlStyles.Purpose.SECONDARY);
        saveButton.addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();if(saveInFlight)return;try{if(birth.getValue()!=null&&birth.getValue().isAfter(LocalDate.now()))throw new IllegalArgumentException("Date of Birth cannot be in the future.");phones.validate();emails.validate();addresses.validate();ContactServicePort.UpdateContactProfileCommand cmd=new ContactServicePort.UpdateContactProfileCommand(contactId,currentContact.shaleClientId(),appState.getUserId(),display.getText(),new ContactServicePort.StructuredName(prefix.getText(),first.getText(),middle.getText(),last.getText(),preferred.getText(),suffix.getText()),birth.getValue(),condition.getText(),deceased.isSelected(),p.contactUpdatedAt(),types.intent(),specs.intent(),creds.intent(),phones.intent(),emails.intent(),addresses.intent());saveInFlight=true;saveButton.setDisable(true);status.setText("Saving complete Contact…");status.setVisible(true);status.setManaged(true);long started=PerfLog.start();LOG.info(()->"operation=contact.aggregate-save.start tenantId="+cmd.shaleClientId()+" contactId="+cmd.contactId()+" actorId="+cmd.actorUserId());dbExec.submit(()->{try{var result=contactService.updateContactProfile(cmd);Platform.runLater(()->{if(disposed)return;saveInFlight=false;classificationProfile=result.profile();renderClassifications();renderContactPoints();contactDetailService.invalidateContact(contactId,currentContact.shaleClientId());dialog.close();LOG.info(()->"operation=contact.aggregate-save.success tenantId="+cmd.shaleClientId()+" contactId="+cmd.contactId()+" elapsedMs="+PerfLog.elapsedMs(started));loadContact();});}catch(RuntimeException ex){logAggregateSaveFailure(cmd,ex);Platform.runLater(()->{if(disposed)return;saveInFlight=false;boolean stale=ex.getMessage()!=null&&ex.getMessage().toLowerCase(Locale.ROOT).contains("reload");saveButton.setDisable(stale);status.setText(stale?"Another update occurred. Your values are retained; choose Reload before saving.":"Save failed and was rolled back. Your values are retained.");status.setVisible(true);status.setManaged(true);});}});}catch(IllegalArgumentException ex){status.setText(ex.getMessage());status.setVisible(true);status.setManaged(true);}});
        dialog.getDialogPane().lookupButton(reload).addEventFilter(javafx.event.ActionEvent.ACTION,e->{e.consume();dialog.close();loadContact();});dialog.showAndWait();
    }
    private static Label heading(String text){Label l=new Label(text);l.getStyleClass().add("contact-editor-section-heading");return l;}
    private static void logAggregateSaveFailure(ContactServicePort.UpdateContactProfileCommand command,RuntimeException failure){
        LOG.log(Level.WARNING,String.format(Locale.ROOT,
                "operation=contact.aggregate-save tenantId=%d contactId=%d actorId=%d exceptionClass=%s",
                command.shaleClientId(),command.contactId(),command.actorUserId(),failure.getClass().getName()),failure);
    }
    private static VBox formField(String label,javafx.scene.control.Control field){Label caption=new Label(label);caption.setLabelFor(field);caption.getStyleClass().add("contact-editor-field-label");VBox box=new VBox(4,caption,field);box.setFillWidth(true);GridPane.setHgrow(box,Priority.ALWAYS);return box;}
    private static ScrollPane sectionScroll(Node node){ScrollPane scroll=new ScrollPane(node);scroll.setFitToWidth(true);scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);scroll.getStyleClass().add("contact-editor-section-scroll");return scroll;}
    private static void showEditorSection(List<Node> sections,int selected){for(int i=0;i<sections.size();i++){boolean active=i==selected;sections.get(i).setVisible(active);sections.get(i).setManaged(active);}}
    private static String structuredPreview(String...v){return java.util.Arrays.stream(v).map(ContactViewController::safeText).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(" "));}

    private abstract static class PointEditor<T>{
        final VBox box=new VBox(8);final List<T> items=new java.util.ArrayList<>();final CheckBox showRemoved=new CheckBox("Show Removed");
        PointEditor(String add){Button b=new Button(add);ControlStyles.apply(b,ControlStyles.Purpose.PRIMARY,ControlStyles.Size.SMALL);b.setOnAction(e->add());showRemoved.selectedProperty().addListener((o,a,v)->render());HBox bar=new HBox(10,b,showRemoved);bar.getStyleClass().add("contact-editor-toolbar");box.getChildren().add(bar);}
        abstract void add();abstract boolean deleted(T x);abstract void setDeleted(T x,boolean v);abstract boolean primary(T x);abstract void setPrimary(T x,boolean v);abstract int order(T x);abstract void setOrder(T x,int v);abstract Node card(T x);abstract void validate();
        void render(){while(box.getChildren().size()>1)box.getChildren().remove(1);normalize();for(T x:items)if(!deleted(x)||showRemoved.isSelected())box.getChildren().add(card(x));if(box.getChildren().size()==1)box.getChildren().add(emptyChip("No entries"));}
        void remove(T x){setDeleted(x,!deleted(x));if(deleted(x))setPrimary(x,false);render();}
        void makePrimary(T x){for(T y:items)if(!deleted(y))setPrimary(y,y==x);render();}
        void move(T x,int d){List<T>a=items.stream().filter(y->!deleted(y)).sorted(Comparator.comparingInt(this::order)).toList();int i=a.indexOf(x),j=i+d;if(i<0||j<0||j>=a.size())return;T y=a.get(j);int oi=order(x);setOrder(x,order(y));setOrder(y,oi);render();}
        void normalize(){int i=0;for(T x:items.stream().filter(y->!deleted(y)).sorted(Comparator.comparingInt(this::order)).toList())setOrder(x,i++);}
        HBox actions(T x,Runnable edit){Button e=small("Edit",edit),primary=small("Make Primary",()->makePrimary(x)),up=small("Move Up",()->move(x,-1)),down=small("Move Down",()->move(x,1)),remove=small(deleted(x)?"Restore":"Remove",()->remove(x));primary.setVisible(!deleted(x)&&!primary(x));primary.setManaged(primary.isVisible());up.setDisable(deleted(x)||order(x)==0);down.setDisable(deleted(x)||order(x)>=items.stream().filter(y->!deleted(y)).count()-1);return new HBox(6,e,primary,up,down,remove);}
        static Button small(String t,Runnable r){Button b=new Button(t);ControlStyles.apply(b,t.equals("Remove")?ControlStyles.Purpose.DANGER:ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);b.setOnAction(e->r.run());return b;}
        VBox shell(T x,String value,String kind,Runnable edit){Label v=new Label(value);v.setWrapText(true);v.getStyleClass().add("contact-point-value");HBox badges=new HBox(6,badge(kind,false));if(primary(x))badges.getChildren().add(badge("Primary",true));if(deleted(x))badges.getChildren().add(badge("Removed",false));VBox c=new VBox(7,badges,v,actions(x,edit));c.getStyleClass().add("contact-point-card");if(deleted(x))c.getStyleClass().add("removed");return c;}
    }
    private static final class PM{Long id;byte[]rv;String kind,number,extension;boolean primary,deleted;int order;PM(Long i,byte[]r,String k,String n,String e,boolean p,boolean d,int o){id=i;rv=r;kind=k;number=n;extension=e;primary=p;deleted=d;order=o;}}
    private static final class PhoneEditor extends PointEditor<PM>{PhoneEditor(List<ContactServicePort.ContactPhoneNumber> xs){super("Add Phone");xs.forEach(x->items.add(new PM(x.id(),x.rowVer(),x.kind(),x.displayNumber(),x.extension(),x.primary(),x.deleted(),x.sortOrder())));render();}void add(){PM x=new PM(null,null,"MOBILE","",null,false,false,(int)items.stream().filter(y->!y.deleted).count());if(edit(x)){items.add(x);render();}}boolean edit(PM x){TextField n=new TextField(x.number),ext=new TextField(safe(x.extension));javafx.scene.control.ComboBox<String> k=new javafx.scene.control.ComboBox<>();k.getItems().addAll("MOBILE","HOME","WORK","FAX","OTHER");k.setValue(x.kind);return editDialog("Phone",List.of(formField("Display Number",n),formField("Extension",ext),formField("Kind",k)),()->{x.number=n.getText();x.extension=ext.getText();x.kind=k.getValue();});}boolean deleted(PM x){return x.deleted;}void setDeleted(PM x,boolean v){x.deleted=v;}boolean primary(PM x){return x.primary;}void setPrimary(PM x,boolean v){x.primary=v;}int order(PM x){return x.order;}void setOrder(PM x,int v){x.order=v;}Node card(PM x){return shell(x,x.number,x.kind,()->{if(edit(x))render();});}void validate(){for(PM x:items)if(x.number==null||x.number.isBlank()||x.number.trim().length()>255)throw new IllegalArgumentException("Every phone requires a display number of at most 255 characters.");}List<ContactServicePort.IntendedPhoneNumber> intent(){normalize();return items.stream().map(x->new ContactServicePort.IntendedPhoneNumber(x.id,x.rv,x.kind,x.number,x.extension,x.primary,x.deleted,x.order)).toList();}}
    private static final class EM{Long id;byte[]rv;String kind,email;boolean primary,deleted;int order;EM(Long i,byte[]r,String k,String e,boolean p,boolean d,int o){id=i;rv=r;kind=k;email=e;primary=p;deleted=d;order=o;}}
    private static final class EmailEditor extends PointEditor<EM>{EmailEditor(List<ContactServicePort.ContactEmailAddress> xs){super("Add Email");xs.forEach(x->items.add(new EM(x.id(),x.rowVer(),x.kind(),x.emailAddress(),x.primary(),x.deleted(),x.sortOrder())));render();}void add(){EM x=new EM(null,null,"PERSONAL","",false,false,(int)items.stream().filter(y->!y.deleted).count());if(edit(x)){items.add(x);render();}}boolean edit(EM x){TextField e=new TextField(x.email);javafx.scene.control.ComboBox<String> k=new javafx.scene.control.ComboBox<>();k.getItems().addAll("PERSONAL","WORK","OTHER");k.setValue(x.kind);return editDialog("Email",List.of(formField("Email Address",e),formField("Kind",k)),()->{x.email=e.getText();x.kind=k.getValue();});}boolean deleted(EM x){return x.deleted;}void setDeleted(EM x,boolean v){x.deleted=v;}boolean primary(EM x){return x.primary;}void setPrimary(EM x,boolean v){x.primary=v;}int order(EM x){return x.order;}void setOrder(EM x,int v){x.order=v;}Node card(EM x){return shell(x,x.email,x.kind,()->{if(edit(x))render();});}void validate(){for(EM x:items)if(x.email==null||x.email.isBlank()||x.email.length()>320||!x.email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Every email requires a practical address of at most 320 characters.");}List<ContactServicePort.IntendedEmailAddress> intent(){normalize();return items.stream().map(x->new ContactServicePort.IntendedEmailAddress(x.id,x.rv,x.kind,x.email,x.primary,x.deleted,x.order)).toList();}}
    private static final class AM{Long id;byte[]rv;String kind,l1,l2,city,state,postal,country,legacy;boolean edited,primary,deleted;int order;AM(ContactServicePort.ContactAddress x){id=x.id();rv=x.rowVer();kind=x.kind();l1=x.addressLine1();l2=x.addressLine2();city=x.city();state=x.stateOrProvince();postal=x.postalCode();country=x.countryCode();legacy=x.legacyAddressText();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}AM(int o){kind="HOME";order=o;}}
    private static final class AddressEditor extends PointEditor<AM>{AddressEditor(List<ContactServicePort.ContactAddress> xs){super("Add Address");xs.forEach(x->items.add(new AM(x)));render();}void add(){AM x=new AM((int)items.stream().filter(y->!y.deleted).count());if(edit(x)){items.add(x);render();}}boolean edit(AM x){TextField l1=new TextField(safe(x.l1)),l2=new TextField(safe(x.l2)),city=new TextField(safe(x.city)),state=new TextField(safe(x.state)),postal=new TextField(safe(x.postal)),country=new TextField(safe(x.country));javafx.scene.control.ComboBox<String> k=new javafx.scene.control.ComboBox<>();k.getItems().addAll("HOME","WORK","OTHER");k.setValue(x.kind);VBox fields=new VBox(7);if(x.id!=null&&x.legacy!=null&&!x.legacy.isBlank()&&!x.edited){Label note=new Label("Legacy-only address: "+x.legacy+"\nSaving structured fields converts this row. The original is preserved until then.");note.setWrapText(true);note.getStyleClass().add("contact-editor-name-preview");fields.getChildren().add(note);}fields.getChildren().addAll(formField("Address Line 1",l1),formField("Address Line 2",l2),formField("City",city),formField("State/Province",state),formField("Postal Code",postal),formField("Country Code",country),formField("Kind",k));return editDialog("Address",List.of(fields),()->{x.l1=l1.getText();x.l2=l2.getText();x.city=city.getText();x.state=state.getText();x.postal=postal.getText();x.country=country.getText();x.kind=k.getValue();x.edited=true;});}boolean deleted(AM x){return x.deleted;}void setDeleted(AM x,boolean v){x.deleted=v;}boolean primary(AM x){return x.primary;}void setPrimary(AM x,boolean v){x.primary=v;}int order(AM x){return x.order;}void setOrder(AM x,int v){x.order=v;}Node card(AM x){String value=x.legacy!=null&&!x.legacy.isBlank()&&!x.edited?x.legacy:java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country).map(ContactViewController::safeText).filter(Objects::nonNull).collect(java.util.stream.Collectors.joining(", "));VBox c=shell(x,value,x.kind,()->{if(edit(x))render();});if(x.legacy!=null&&!x.legacy.isBlank()&&!x.edited)((HBox)c.getChildren().get(0)).getChildren().add(badge("Legacy only",false));return c;}void validate(){for(AM x:items){if(java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy).allMatch(v->safe(v).isBlank()))throw new IllegalArgumentException("Every address requires address content.");if(!safe(x.country).isBlank()&&!x.country.trim().matches("[A-Za-z]{2}"))throw new IllegalArgumentException("Country Code must be two letters.");}}List<ContactServicePort.IntendedAddress> intent(){normalize();return items.stream().map(x->new ContactServicePort.IntendedAddress(x.id,x.rv,x.kind,x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy,x.edited,x.primary,x.deleted,x.order)).toList();}}
    private static boolean editDialog(String title,List<Node> fields,Runnable apply){Dialog<ButtonType>d=new Dialog<>();AppDialogs.applySecondaryDialogShell(d,"Edit "+title);d.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);VBox b=new VBox(8);b.getChildren().addAll(fields);d.getDialogPane().setContent(b);return d.showAndWait().filter(ButtonType.OK::equals).map(x->{apply.run();return true;}).orElse(false);}
    private static final class SelectionEditor<T>{final VBox box=new VBox(7);final List<Entry> entries=new java.util.ArrayList<>();record Entry(long assignment,int definition,byte[] rowVer,CheckBox selected){}
        SelectionEditor(List<T> effective,List<ContactServicePort.AssignedDefinition> assigned,java.util.function.ToIntFunction<T> id,java.util.function.Function<T,String> name,java.util.function.Function<T,String> color){box.getStyleClass().add("contact-editor-choice-list");Map<Integer,ContactServicePort.AssignedDefinition> current=assigned.stream().collect(java.util.stream.Collectors.toMap(a->a.definition().id(),a->a));for(T d:effective){int did=id.applyAsInt(d);var a=current.remove(did);CheckBox cb=new CheckBox(name.apply(d));applyDefinitionColor(cb,color.apply(d));cb.setSelected(a!=null);box.getChildren().add(choiceRow(cb,color.apply(d)));entries.add(new Entry(a==null?0:a.assignmentId(),did,a==null?null:a.rowVer(),cb));}for(var a:current.values()){CheckBox cb=new CheckBox(a.definition().name()+" · Historical");applyDefinitionColor(cb,a.definition().color());cb.getStyleClass().add("contact-editor-choice-historical");cb.setSelected(true);box.getChildren().add(choiceRow(cb,a.definition().color()));entries.add(new Entry(a.assignmentId(),a.definition().id(),a.rowVer(),cb));}}
        List<ContactServicePort.IntendedAssignment> intent(){return entries.stream().map(e->new ContactServicePort.IntendedAssignment(e.assignment,e.definition,e.selected.isSelected(),e.rowVer)).toList();}
        static void applyDefinitionColor(CheckBox cb,String color){cb.getStyleClass().add("contact-editor-choice-checkbox");}
        static HBox choiceRow(CheckBox cb,String color){Region swatch=new Region();swatch.getStyleClass().add("contact-editor-color-swatch");String c=color!=null&&color.matches("#[0-9A-Fa-f]{6}")?color:"#6C757D";int rgb=Integer.parseInt(c.substring(1),16);int r=rgb>>16,g=(rgb>>8)&255,b=rgb&255;String border=(r*299+g*587+b*114)/1000>150?"#425466":"#DCE5EE";swatch.setStyle("-fx-background-color:"+c+";-fx-border-color:"+border+";");HBox row=new HBox(10,swatch,cb);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("contact-editor-choice-row");HBox.setHgrow(cb,Priority.ALWAYS);cb.setMaxWidth(Double.MAX_VALUE);return row;}}
    private static final class CredentialEditor{final VBox box=new VBox(7);final List<CEntry> entries=new java.util.ArrayList<>();final Runnable changed;record CEntry(long assignment,ContactServicePort.CredentialDefinition credential,byte[] rowVer,boolean historical,CheckBox selected,String label,HBox row,Button up,Button down){}
        CredentialEditor(List<ContactServicePort.CredentialDefinition> effective,List<ContactServicePort.AssignedCredential> assigned,Runnable changed){this.changed=changed;box.getStyleClass().add("contact-editor-credential-list");Map<Integer,ContactServicePort.AssignedCredential> current=assigned.stream().collect(java.util.stream.Collectors.toMap(a->a.definition().id(),a->a));for(var d:effective){var a=current.remove(d.id());add(a==null?0:a.assignmentId(),d,a==null?null:a.rowVer(),false,a!=null,d.abbreviation()+" — "+d.name(),d.color());}for(var a:current.values())add(a.assignmentId(),a.definition(),a.rowVer(),true,true,a.definition().abbreviation()+" — "+a.definition().name()+" · Historical",a.definition().color());}
        void add(long aid,ContactServicePort.CredentialDefinition credential,byte[]rv,boolean historical,boolean selected,String label,String color){CheckBox cb=new CheckBox(label);SelectionEditor.applyDefinitionColor(cb,color);cb.setSelected(selected);Button up=new Button("Move Up"),down=new Button("Move Down");ControlStyles.apply(up,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);ControlStyles.apply(down,ControlStyles.Purpose.SECONDARY,ControlStyles.Size.SMALL);HBox choice=SelectionEditor.choiceRow(cb,color);HBox row=new HBox(8,choice,up,down);row.setAlignment(Pos.CENTER_LEFT);row.getStyleClass().add("contact-editor-credential-row");HBox.setHgrow(choice,Priority.ALWAYS);CEntry e=new CEntry(aid,credential,rv,historical,cb,label,row,up,down);entries.add(e);box.getChildren().add(row);up.setOnAction(x->move(e,-1));down.setOnAction(x->move(e,1));cb.selectedProperty().addListener((o,a,b)->{updateButtons();changed.run();});updateButtons();}
        void move(CEntry entry,int delta){List<CEntry> selected=entries.stream().filter(e->e.selected.isSelected()).toList();int selectedIndex=selected.indexOf(entry),targetIndex=selectedIndex+delta;if(selectedIndex<0||targetIndex<0||targetIndex>=selected.size())return;CEntry target=selected.get(targetIndex);int i=entries.indexOf(entry),j=entries.indexOf(target);java.util.Collections.swap(entries,i,j);box.getChildren().set(i,target.row);box.getChildren().set(j,entry.row);updateButtons();changed.run();}
        void updateButtons(){List<CEntry> selected=entries.stream().filter(e->e.selected.isSelected()).toList();for(CEntry e:entries){int index=selected.indexOf(e);e.up.setDisable(index<=0);e.down.setDisable(index<0||index==selected.size()-1);}}
        List<ContactServicePort.AssignedCredential> previewCredentials(){int[] order={0};return entries.stream().filter(e->e.selected.isSelected()).map(e->new ContactServicePort.AssignedCredential(e.assignment,e.credential,order[0]++,e.historical,e.rowVer)).toList();}
        List<ContactServicePort.IntendedAssignment> intent(){return entries.stream().map(e->new ContactServicePort.IntendedAssignment(e.assignment,e.credential.id(),e.selected.isSelected(),e.rowVer)).toList();}}

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
        if (currentContact == null) return;
        ContactServicePort.ClassificationProfile profile = classificationProfile;
        String effectiveDisplayName = profile == null ? currentContact.displayName()
                : com.shale.core.service.ContactNamePresentation.effectiveDisplayName(
                        profile.legacyDisplayName(), profile.credentials());
        if (contactTitleLabel != null) contactTitleLabel.setText(fallback(effectiveDisplayName, "Contact"));
        if (contactSubtitleLabel != null) contactSubtitleLabel.setText("Contact #" + currentContact.id());
        if (lastUpdatedLabel != null) lastUpdatedLabel.setText("Last updated: " + ContactDao.formatTimestamp(currentContact.updatedAt()));

        String fullName = profile == null ? structuredPreview(currentContact.firstName(), currentContact.lastName())
                : com.shale.core.service.ContactNamePresentation.structuredFullName(
                        profile.structuredName(), profile.credentials());
        if (structuredFullNameValue != null) structuredFullNameValue.setText(fallback(fullName));

        String preferred = profile == null ? null : safeText(profile.structuredName().preferredName());
        boolean showPreferred = preferred != null && !preferred.equalsIgnoreCase(fullName)
                && !preferred.equalsIgnoreCase(safe(currentContact.displayName()).trim());
        setVisibleManaged(preferredNameLabel, showPreferred);
        setVisibleManaged(preferredNameValue, showPreferred);
        if (preferredNameValue != null) preferredNameValue.setText(showPreferred ? preferred : "");

        LocalDate birth = profile == null ? currentContact.dateOfBirth() : profile.dateOfBirth();
        String condition = profile == null ? currentContact.condition() : profile.condition();
        boolean deceased = profile == null ? currentContact.deceased() : profile.deceased();
        if (dateOfBirthValue != null) dateOfBirthValue.setText(formatDate(birth));
        if (conditionValue != null) conditionValue.setText(fallback(condition));
        if (deceasedValue != null) deceasedValue.setText(booleanLabel(deceased));
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

    private void refreshContactActions() {
        setVisibleManaged(editButton, canEditContact() && currentContact != null);
        refreshDeleteAction();
    }

    private void setBusy(boolean busy) {
        if (editButton != null) {
            editButton.setDisable(busy);
        }
        if (deleteContactButton != null) {
            deleteContactButton.setDisable(busy);
        }
    }

    private void refreshDeleteAction() {
        boolean showDelete = isAdminUser() && currentContact != null;
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

    private static void setVisibleManaged(Node node, boolean visible) {
        if (node == null) {
            return;
        }
        node.setVisible(visible);
        node.setManaged(visible);
    }


}
