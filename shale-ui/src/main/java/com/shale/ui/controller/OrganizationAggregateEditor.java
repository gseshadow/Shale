package com.shale.ui.controller;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import com.shale.core.model.Organization;
import com.shale.core.service.OrganizationServicePort;
import com.shale.ui.component.EnhancedTextArea;
import com.shale.ui.component.OrganizationTypeAssignmentPane;
import com.shale.ui.component.dialog.AppDialogs;
import com.shale.ui.controller.support.OrganizationTypeAssignmentStage;
import com.shale.ui.util.ControlStyles;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** Shared staged Organization editor body used by both create and edit lifecycles. */
public final class OrganizationAggregateEditor extends VBox {
    private final TextField name = field();
    private final EnhancedTextArea notes = new EnhancedTextArea();
    private final OrganizationTypeAssignmentPane assignments = new OrganizationTypeAssignmentPane();
    private final String baselineName;
    private final String baselineNotes;
    private final PhoneEditor phones;
    private final EmailEditor emails;
    private final AddressEditor addresses;
    private final WebsiteEditor websites;

    public static OrganizationAggregateEditor forCreate(OrganizationTypeAssignmentStage assignments) {
        return new OrganizationAggregateEditor("", "", assignments, List.of(), List.of(), List.of(), List.of());
    }

    public static OrganizationAggregateEditor forEdit(Organization organization, OrganizationTypeAssignmentStage assignments,
            OrganizationServicePort.OrganizationStructuredContactProfile contacts) {
        return new OrganizationAggregateEditor(safe(organization.getName()), safe(organization.getNotes()), assignments,
                contacts.phones(), contacts.emails(), contacts.addresses(), contacts.websites());
    }

    private OrganizationAggregateEditor(String initialName, String initialNotes, OrganizationTypeAssignmentStage stage,
            List<OrganizationServicePort.OrganizationPhoneNumber> phoneRows,
            List<OrganizationServicePort.OrganizationEmailAddress> emailRows,
            List<OrganizationServicePort.OrganizationAddress> addressRows,
            List<OrganizationServicePort.OrganizationWebsite> websiteRows) {
        setSpacing(14);
        setPadding(new javafx.geometry.Insets(4));
        baselineName = initialName;
        baselineNotes = initialNotes;
        name.setText(initialName);
        notes.setText(initialNotes);
        notes.setEditorTitle("Organization Notes");
        notes.setPrefRowCount(5);
        notes.setMaxWidth(Double.MAX_VALUE);
        this.assignments.setStage(Objects.requireNonNull(stage));
        phones = new PhoneEditor(phoneRows);
        emails = new EmailEditor(emailRows);
        addresses = new AddressEditor(addressRows);
        websites = new WebsiteEditor(websiteRows);
        getChildren().setAll(section("Organization Details", detailsGrid()),
                section("Contact Information", new VBox(14,
                        subsection("Phone Numbers", phones.box), subsection("Email Addresses", emails.box),
                        subsection("Addresses", addresses.box), subsection("Websites", websites.box))),
                section("Organization Types", this.assignments), section("Notes", notes));
    }

    public void setMessageHandler(java.util.function.Consumer<String> handler) { assignments.setMessageHandler(handler); }
    public OrganizationTypeAssignmentStage assignmentStage() { return assignments.getStage(); }
    public String validationError() {
        if (value(name).isBlank()) return "Name is required.";
        if (value(name).length() > 400) return "Name must be at most 400 characters.";
        if (!assignmentStage().isValid()) return "Exactly one eligible primary Organization Type is required.";
        try { phones.validate(); emails.validate(); addresses.validate(); websites.validate(); }
        catch (IllegalArgumentException failure) { return failure.getMessage(); }
        return null;
    }
    public OrganizationServicePort.OrganizationFields fields() {
        return new OrganizationServicePort.OrganizationFields(value(name), null, null, null, null,
                null, null, null, null, null, null, safe(notes.getText()));
    }
    public OrganizationServicePort.StructuredContactMutation contactMutation() {
        return new OrganizationServicePort.StructuredContactMutation(
                OrganizationServicePort.OwnedContactCollection.exact(phones.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(emails.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(addresses.intent()),
                OrganizationServicePort.OwnedContactCollection.exact(websites.intent()));
    }
    public boolean isDirty() {
        return !Objects.equals(value(name), baselineName) || !Objects.equals(safe(notes.getText()), baselineNotes)
                || assignmentStage().isDirty() || phones.isDirty() || emails.isDirty() || addresses.isDirty() || websites.isDirty();
    }
    private GridPane detailsGrid() { GridPane grid=new GridPane();grid.setHgap(14);grid.setVgap(9);grid.getColumnConstraints().addAll(new ColumnConstraints(130,150,180),grow());ControlStyles.formControl(name);add(grid,0,"Name",name);return grid; }
    private static ColumnConstraints grow(){ColumnConstraints c=new ColumnConstraints();c.setHgrow(Priority.ALWAYS);c.setFillWidth(true);return c;}
    private static TextField field(){TextField f=new TextField();f.setMaxWidth(Double.MAX_VALUE);return f;}
    private static VBox section(String title,Node body){Label heading=new Label(title);heading.getStyleClass().add("contact-editor-section-heading");return new VBox(9,heading,body);}
    private static VBox subsection(String title,Node body){return section(title,body);}
    private static void add(GridPane grid,int row,String text,Node field){Label label=new Label(text);label.setLabelFor(field);label.getStyleClass().add("contact-editor-field-label");grid.add(label,0,row);grid.add(field,1,row);GridPane.setHgrow(field,Priority.ALWAYS);}
    private static String safe(String value){return value==null?"":value.trim();}
    private static String value(TextField field){return safe(field.getText());}
    abstract static class PointEditor<T> {
        final VBox box = new VBox(8); final List<T> items = new ArrayList<>();
        final CheckBox showRemoved = new CheckBox("Show Removed");
        PointEditor(String add) { Button button = small(add, this::add, ControlStyles.Purpose.PRIMARY); showRemoved.selectedProperty().addListener((o,a,v)->render()); HBox toolbar=new HBox(10, button, showRemoved);toolbar.getStyleClass().add("contact-editor-toolbar");box.getChildren().add(toolbar); }
        abstract void add(); abstract boolean isNew(T x); abstract boolean deleted(T x); abstract void deleted(T x, boolean value); abstract boolean primary(T x); abstract void primary(T x, boolean value); abstract int order(T x); abstract void order(T x,int value); abstract Node card(T x); abstract void validate(); abstract String signature();
        String baselineSignature;
        void capture() { normalize(); baselineSignature=signature(); render(); }
        boolean isDirty(){ normalize(); return !Objects.equals(baselineSignature, signature()); }
        void render(){ while(box.getChildren().size()>1)box.getChildren().remove(1); normalize(); for(T x:items)if(!deleted(x)||showRemoved.isSelected())box.getChildren().add(card(x)); if(box.getChildren().size()==1)box.getChildren().add(empty("No entries")); }
        void toggle(T x){if(isNew(x)){items.remove(x);ensurePrimary();render();return;}deleted(x,!deleted(x));if(deleted(x))primary(x,false);ensurePrimary();render();}
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
    private static final class PhoneEditor extends PointEditor<P>{PhoneEditor(List<OrganizationServicePort.OrganizationPhoneNumber> xs){super("Add Phone");xs.forEach(x->items.add(new P(x)));capture();}void add(){P x=new P(new OrganizationServicePort.OrganizationPhoneNumber(0,0,0,OrganizationServicePort.OrganizationPhoneKind.MOBILE,"MOBILE","",null,null,false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(P x){TextField n=new TextField(safe(x.number)),e=new TextField(safe(x.extension));ComboBox<OrganizationServicePort.OrganizationPhoneKind> k=combo(OrganizationServicePort.OrganizationPhoneKind.values(),x.kind);return editDialog("Phone Number",List.of(formField("Display Number",n),formField("Extension (optional)",e),formField("Kind",k)),()->{x.number=n.getText();x.extension=value(e).isBlank()?null:value(e);x.kind=k.getValue();if(x.kind==OrganizationServicePort.OrganizationPhoneKind.FAX&&active().stream().anyMatch(y->y!=x&&y.kind!=OrganizationServicePort.OrganizationPhoneKind.FAX))x.primary=false;ensureVoicePrimary();});}void makePrimary(P x){if(x.kind==OrganizationServicePort.OrganizationPhoneKind.FAX&&active().stream().anyMatch(y->y.kind!=OrganizationServicePort.OrganizationPhoneKind.FAX))return;super.makePrimary(x);}void ensureVoicePrimary(){List<P>voice=active().stream().filter(x->x.kind!=OrganizationServicePort.OrganizationPhoneKind.FAX).toList();if(!voice.isEmpty()&&voice.stream().noneMatch(x->x.primary)){active().forEach(x->x.primary=false);voice.get(0).primary=true;}else ensurePrimary();}boolean isNew(P x){return x.id==null;}boolean deleted(P x){return x.deleted;}void deleted(P x,boolean v){x.deleted=v;}boolean primary(P x){return x.primary;}void primary(P x,boolean v){x.primary=v;}int order(P x){return x.order;}void order(P x,int v){x.order=v;}Node card(P x){return shell(x,x.number+(safe(x.extension).isBlank()?"":" ext. "+x.extension),x.kind.name(),()->{if(edit(x))render();});}void validate(){ensureVoicePrimary();Set<String>d=new HashSet<>();for(P x:active()){if(safe(x.number).isBlank()||safe(x.number).length()>255)throw new IllegalArgumentException("Phone Numbers: every active entry requires a number of at most 255 characters.");String key=safe(x.number).toLowerCase(Locale.ROOT)+"|"+safe(x.extension).toLowerCase(Locale.ROOT);if(!d.add(key))throw new IllegalArgumentException("Phone Numbers: duplicate active entries are not allowed.");if(x.kind==OrganizationServicePort.OrganizationPhoneKind.UNKNOWN)throw new IllegalArgumentException("Phone Numbers: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationPhone>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationPhone(x.id,x.rv,x.kind,x.number,x.extension,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.displayNumber()+"|"+x.extension()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class E {Long id;byte[]rv;OrganizationServicePort.OrganizationEmailKind kind;String value;boolean primary,deleted;int order;E(OrganizationServicePort.OrganizationEmailAddress x){id=x.id();rv=x.rowVer();kind=x.kind();value=x.emailAddress();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class EmailEditor extends PointEditor<E>{EmailEditor(List<OrganizationServicePort.OrganizationEmailAddress>xs){super("Add Email");xs.forEach(x->items.add(new E(x)));capture();}void add(){E x=new E(new OrganizationServicePort.OrganizationEmailAddress(0,0,0,OrganizationServicePort.OrganizationEmailKind.PERSONAL,"PERSONAL","","",false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(E x){TextField v=new TextField(safe(x.value));ComboBox<OrganizationServicePort.OrganizationEmailKind>k=combo(OrganizationServicePort.OrganizationEmailKind.values(),x.kind);return editDialog("Email Address",List.of(formField("Email Address",v),formField("Kind",k)),()->{x.value=v.getText();x.kind=k.getValue();});}boolean isNew(E x){return x.id==null;}boolean deleted(E x){return x.deleted;}void deleted(E x,boolean v){x.deleted=v;}boolean primary(E x){return x.primary;}void primary(E x,boolean v){x.primary=v;}int order(E x){return x.order;}void order(E x,int v){x.order=v;}Node card(E x){return shell(x,x.value,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();Set<String>d=new HashSet<>();for(E x:active()){String v=safe(x.value);if(v.length()>320||!v.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))throw new IllegalArgumentException("Email Addresses: enter a valid address for every active entry.");if(!d.add(v.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Email Addresses: duplicate active addresses are not allowed.");if(x.kind==OrganizationServicePort.OrganizationEmailKind.UNKNOWN)throw new IllegalArgumentException("Email Addresses: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationEmail>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationEmail(x.id,x.rv,x.kind,x.value,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.emailAddress()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class A {Long id;byte[]rv;OrganizationServicePort.OrganizationAddressKind kind;String l1,l2,city,state,postal,country,legacy;boolean primary,deleted;int order;A(OrganizationServicePort.OrganizationAddress x){id=x.id();rv=x.rowVer();kind=x.kind();l1=x.addressLine1();l2=x.addressLine2();city=x.city();state=x.stateOrProvince();postal=x.postalCode();country=x.country();legacy=x.legacyAddressText();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class AddressEditor extends PointEditor<A>{AddressEditor(List<OrganizationServicePort.OrganizationAddress>xs){super("Add Address");xs.forEach(x->items.add(new A(x)));capture();}void add(){A x=new A(new OrganizationServicePort.OrganizationAddress(0,0,0,OrganizationServicePort.OrganizationAddressKind.WORK,"WORK",null,null,null,null,null,null,null,false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(A x){TextField l1=new TextField(safe(x.l1)),l2=new TextField(safe(x.l2)),city=new TextField(safe(x.city)),state=new TextField(safe(x.state)),postal=new TextField(safe(x.postal)),country=new TextField(safe(x.country));ComboBox<OrganizationServicePort.OrganizationAddressKind>k=combo(OrganizationServicePort.OrganizationAddressKind.values(),x.kind);GridPane compact=new GridPane();compact.setHgap(8);compact.add(formField("State / Province",state),0,0);compact.add(formField("Postal Code",postal),1,0);return editDialog("Address",List.of(formField("Address Line 1",l1),formField("Address Line 2",l2),formField("City",city),compact,formField("Country",country),formField("Kind",k)),()->{x.l1=l1.getText();x.l2=l2.getText();x.city=city.getText();x.state=state.getText();x.postal=postal.getText();x.country=country.getText();x.kind=k.getValue();});}boolean isNew(A x){return x.id==null;}boolean deleted(A x){return x.deleted;}void deleted(A x,boolean v){x.deleted=v;}boolean primary(A x){return x.primary;}void primary(A x,boolean v){x.primary=v;}int order(A x){return x.order;}void order(A x,int v){x.order=v;}Node card(A x){String shown=java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country).map(OrganizationAggregateEditor::safe).filter(v->!v.isBlank()).collect(java.util.stream.Collectors.joining(", "));if(shown.isBlank())shown=x.legacy;return shell(x,shown,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();for(A x:active()){if(java.util.stream.Stream.of(x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy).allMatch(v->safe(v).isBlank()))throw new IllegalArgumentException("Addresses: every active entry requires at least one address value.");if(x.kind==OrganizationServicePort.OrganizationAddressKind.UNKNOWN)throw new IllegalArgumentException("Addresses: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationAddress>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationAddress(x.id,x.rv,x.kind,x.l1,x.l2,x.city,x.state,x.postal,x.country,x.legacy,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+Arrays.asList(x.addressLine1(),x.addressLine2(),x.city(),x.stateOrProvince(),x.postalCode(),x.country(),x.legacyAddressText())+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static final class W {Long id;byte[]rv;OrganizationServicePort.OrganizationWebsiteKind kind;String value;boolean primary,deleted;int order;W(OrganizationServicePort.OrganizationWebsite x){id=x.id();rv=x.rowVer();kind=x.kind();value=x.website();primary=x.primary();deleted=x.deleted();order=x.sortOrder();}}
    private static final class WebsiteEditor extends PointEditor<W>{WebsiteEditor(List<OrganizationServicePort.OrganizationWebsite>xs){super("Add Website");xs.forEach(x->items.add(new W(x)));capture();}void add(){W x=new W(new OrganizationServicePort.OrganizationWebsite(0,0,0,OrganizationServicePort.OrganizationWebsiteKind.MAIN,"MAIN","",false,active().size(),false,null,null));x.id=null;if(edit(x)){items.add(x);ensurePrimary();render();}}boolean edit(W x){TextField v=new TextField(safe(x.value));ComboBox<OrganizationServicePort.OrganizationWebsiteKind>k=combo(OrganizationServicePort.OrganizationWebsiteKind.values(),x.kind);return editDialog("Website",List.of(formField("Website",v),formField("Kind",k)),()->{x.value=v.getText();x.kind=k.getValue();});}boolean isNew(W x){return x.id==null;}boolean deleted(W x){return x.deleted;}void deleted(W x,boolean v){x.deleted=v;}boolean primary(W x){return x.primary;}void primary(W x,boolean v){x.primary=v;}int order(W x){return x.order;}void order(W x,int v){x.order=v;}Node card(W x){return shell(x,x.value,x.kind.name(),()->{if(edit(x))render();});}void validate(){ensurePrimary();Set<String>d=new HashSet<>();for(W x:active()){String v=safe(x.value);if(v.isBlank()||v.length()>2048)throw new IllegalArgumentException("Websites: every active entry requires a value.");if(v.matches("(?i)^[a-z][a-z0-9+.-]*:.*")&&!v.matches("(?i)^https?://.*"))throw new IllegalArgumentException("Websites: only HTTP(S) schemes are supported.");if(!d.add(v.toLowerCase(Locale.ROOT)))throw new IllegalArgumentException("Websites: duplicate active values are not allowed.");if(x.kind==OrganizationServicePort.OrganizationWebsiteKind.UNKNOWN)throw new IllegalArgumentException("Websites: an unknown historical kind must be changed before saving.");}}List<OrganizationServicePort.StagedOrganizationWebsite>intent(){normalize();return items.stream().map(x->new OrganizationServicePort.StagedOrganizationWebsite(x.id,x.rv,x.kind,x.value,x.primary,x.order,x.deleted)).toList();}String signature(){return intent().stream().map(x->x.id()+"|"+rv(x.expectedRowVer())+"|"+x.kind()+"|"+x.website()+"|"+x.primary()+"|"+x.sortOrder()+"|"+x.deleted()).toList().toString();}}
    private static <T> ComboBox<T> combo(T[] values,T selected){ComboBox<T> box=new ComboBox<>();box.getItems().addAll(values);box.setValue(selected);ControlStyles.formControl(box);return box;}
}
