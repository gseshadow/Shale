package com.shale.ui.component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.css.PseudoClass;
import com.shale.ui.util.ContactExternalActions;
import com.shale.ui.util.ColorUtil;
import com.shale.data.dao.OrganizationDao.OrganizationCardType;

public class OrganizationCard extends HBox {
	public static final double COMPACT_CARD_HEIGHT = 112;
	private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
	private final Label nameLabel = new Label();
	private final Label typeLabel = new Label();
	private final Label phoneLabel = new Label();
	private final Label emailLabel = new Label();
	private final Label websiteLabel = new Label();
	private final Label addressLabel = new Label();
	private final Label notesLabel = new Label();
	private final Label initialsLabel = new Label();
	private final StackPane avatarHolder = new StackPane();

	private Integer organizationId;
	private Consumer<Integer> onOpen;
	private boolean suppressPlaceholderLines;
	private List<OrganizationCardType> types=List.of();
	private ContactExternalActions externalActions=new ContactExternalActions();
	private Runnable phoneAction,emailAction,addressAction,websiteAction;
	private boolean removed;
	private Runnable restoreAction;

	public OrganizationCard() {
		buildUiMiniDefaults();
		wireEvents();
	}

	public void setOrganizationId(Integer organizationId) {
		this.organizationId = organizationId;
	}

	public void setOnOpen(Consumer<Integer> onOpen) {
		this.onOpen = onOpen;
	}

	public void setName(String name) {
		String resolved=fallback(name);nameLabel.setText(resolved);nameLabel.setTooltip(new Tooltip(resolved));
		initialsLabel.setText(initials(resolved));
		updateAccessibleText();
	}

	public void setOrganizationType(Integer organizationTypeId, String organizationTypeName) {
		String resolvedName = organizationTypeName == null ? "" : organizationTypeName.trim();
		if (!resolvedName.isEmpty()) {
			typeLabel.setText("Type: " + resolvedName);
			types=List.of(new OrganizationCardType(0,organizationTypeId==null?0:organizationTypeId,resolvedName,"#6C757D",true,0));
			updateAccessibleText();
			return;
		}

		typeLabel.setText(organizationTypeId == null ? "Type: Unknown" : "Type: " + organizationTypeId);
	}

	public void setPhone(String phone) {
		phoneLabel.setText("Phone: " + fallback(phone));
	}
	public void setStructuredPhone(String display,String normalized,String extension){
		String value=display;
		if(value!=null&&extension!=null&&!extension.isBlank()&&!value.toLowerCase().matches(".*(?:ext\\.?|x)\\s*"+java.util.regex.Pattern.quote(extension.trim())+".*"))
			value=value+" ext. "+extension.trim();
		setPhone(value);
		phoneAction=null;if(normalized!=null)try{var target=ContactExternalActions.telephone(normalized,extension);phoneAction=()->externalActions.open(target);}catch(IllegalArgumentException ignored){}
	}

	public void setEmail(String email) {
		emailLabel.setText("Email: " + fallback(email));
		emailAction=email!=null&&email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")?()->externalActions.open(ContactExternalActions.email(email)):null;
	}

	public void setWebsite(String website) {
		websiteLabel.setText("Web: " + fallback(website));
		websiteAction=null;try{var target=ContactExternalActions.website(website);websiteAction=()->externalActions.open(target);}catch(IllegalArgumentException ignored){}
	}
	public void setTypes(List<OrganizationCardType> values){types=List.copyOf(values);updateAccessibleText();}
	public void setExternalActions(ContactExternalActions actions){externalActions=java.util.Objects.requireNonNull(actions);}
	public void setRemoved(boolean removed,Runnable restoreAction){this.removed=removed;this.restoreAction=restoreAction;getStyleClass().remove("organization-card-removed");if(removed){getStyleClass().add("organization-card-removed");setCursor(Cursor.DEFAULT);}else setCursor(Cursor.HAND);updateAccessibleText();}
	public void setSelected(boolean selected){pseudoClassStateChanged(SELECTED,selected);}

	public void setAddress(String address1, String address2, String city, String state, String postalCode, String country) {
		List<String> parts = new ArrayList<>();
		String street = joinNonBlank(", ", address1, address2);
		String region = joinNonBlank(", ", city, state);
		String postalAndCountry = joinNonBlank(" ", postalCode, country);

		if (!street.isBlank()) {
			parts.add(street);
		}
		if (!region.isBlank()) {
			parts.add(region);
		}
		if (!postalAndCountry.isBlank()) {
			parts.add(postalAndCountry);
		}

		String oneLineAddress = parts.isEmpty() ? "—" : String.join(" • ", parts);
		addressLabel.setText("Address: " + oneLineAddress);
		addressAction=parts.isEmpty()?null:()->externalActions.open(ContactExternalActions.maps(String.join(", ",parts)));
	}
	public void setAddress(String address){addressLabel.setText("Address: "+fallback(address));addressAction=address==null||address.isBlank()?null:()->externalActions.open(ContactExternalActions.maps(address));}

	public void setNotesSnippet(String notes) {
		String trimmed = notes == null ? "" : notes.trim();
		if (trimmed.isEmpty()) {
			notesLabel.setText("");
			return;
		}

		String compact = trimmed.replaceAll("\\s+", " ");
		int max = 96;
		if (compact.length() > max) {
			compact = compact.substring(0, max - 1).trim() + "…";
		}
		notesLabel.setText("Notes: " + compact);
	}

	public void setBackgroundCssColor(String css) {
		setPrimaryTypeColor(css);
	}

	/** Applies only authoritative primary-definition color; null/invalid values retain the theme fallback. */
	public void setPrimaryTypeColor(String storedColor) {
		String normalized=ColorUtil.normalizeStoredColor(storedColor);
		if(normalized==null){setStyle("");return;}
		String color="#"+normalized.substring(0,6);
		setStyle("-shale-organization-type-accent: "+color
				+"; -shale-organization-type-wash-strong: "+ColorUtil.toCssRgba(color,.20)
				+"; -shale-organization-type-wash-medium: "+ColorUtil.toCssRgba(color,.13)
				+"; -shale-organization-type-wash-light: "+ColorUtil.toCssRgba(color,.07)+";");
	}

	public void setSuppressPlaceholderLines(boolean suppressPlaceholderLines) {
		this.suppressPlaceholderLines = suppressPlaceholderLines;
	}

	public void applyMini() {
		getChildren().clear();
		setClip(null);
		resetNameLabelVariantStyles();
		nameLabel.getStyleClass().addAll("organization-card-name", "organization-card-name-mini");

		setPrefWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
		setMaxWidth(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
		setPadding(new Insets(4, 10, 4, 10));
		setSpacing(6);

		nameLabel.setStyle(null);
		setVariantClasses("organization-card-mini", "shale-entity-card-inline", "shale-entity-card-embedded");
		getChildren().add(nameLabel);
	}

	public void applyCompact() {
		getChildren().clear();
		setClip(null);

		setAlignment(Pos.TOP_LEFT);
		setPadding(new Insets(10, 12, 10, 12));
		setSpacing(12);
		setMinHeight(COMPACT_CARD_HEIGHT);
		setPrefHeight(COMPACT_CARD_HEIGHT);
		setMaxHeight(COMPACT_CARD_HEIGHT);

		Node avatar = buildAvatar(18);

		resetNameLabelVariantStyles();
		nameLabel.getStyleClass().addAll("organization-card-name","organization-card-name-compact");
		nameLabel.setStyle(null);
		nameLabel.setMaxWidth(Double.MAX_VALUE);

		VBox text = new VBox(4, nameLabel);
		text.getStyleClass().add("organization-card-content");HBox.setHgrow(text,javafx.scene.layout.Priority.ALWAYS);
		text.getChildren().add(classificationRegion());
		Node summary = firstCompactSummary();
		if (summary != null) text.getChildren().add(summary);
		setVariantClasses("organization-card-compact", "shale-entity-card-compact");
		getChildren().addAll(avatar, text);
	}

	public void applyFull() {
		getChildren().clear();
		phoneAction=emailAction=addressAction=websiteAction=null;

		setAlignment(Pos.TOP_LEFT);
		setMinWidth(320);
		setPrefWidth(340);
		setMaxWidth(340);
		setMinHeight(CaseCard.FULL_CARD_HEIGHT);
		setPrefHeight(CaseCard.FULL_CARD_HEIGHT);
		setMaxHeight(CaseCard.FULL_CARD_HEIGHT);
		Rectangle boundsClip=new Rectangle();boundsClip.widthProperty().bind(widthProperty());boundsClip.heightProperty().bind(heightProperty());boundsClip.setArcWidth(28);boundsClip.setArcHeight(28);setClip(boundsClip);
		setPadding(new Insets(10, 12, 10, 12));
		setSpacing(10);

		Node avatar = buildAvatar(22);

		resetNameLabelVariantStyles();
		nameLabel.getStyleClass().addAll("organization-card-name","organization-card-name-full");
		nameLabel.setStyle(null);
		nameLabel.setWrapText(true);
		nameLabel.setMaxWidth(Double.MAX_VALUE);
		nameLabel.setMaxHeight(36);
		notesLabel.setWrapText(true);

		VBox text = new VBox(5, nameLabel, classificationRegion());
		text.getStyleClass().add("organization-card-content");HBox.setHgrow(text,javafx.scene.layout.Priority.ALWAYS);
		if(removed){Label badge=new Label("Removed");badge.getStyleClass().add("lifecycle-removed-badge");text.getChildren().add(1,badge);}
		if(!removed){GridPane summary=contactSummary();if(!summary.getChildren().isEmpty())text.getChildren().add(summary);}
		if(removed&&restoreAction!=null){Button restore=com.shale.ui.util.ActionButtonFactory.semantic("Restore Organization",e->restoreAction.run(),com.shale.ui.util.ControlStyles.Purpose.PRIMARY,com.shale.ui.util.ControlStyles.Size.SMALL);text.getChildren().add(restore);}

		setVariantClasses("organization-card-full", "shale-entity-card-full");
		getChildren().addAll(avatar, text);
	}

	public Node asNode() {
		return this;
	}

	private void resetNameLabelVariantStyles() {
		nameLabel.getStyleClass().removeAll("organization-card-name", "organization-card-name-mini");
	}

	private void buildUiMiniDefaults() {
		getStyleClass().addAll("organization-card","shale-entity-card","shale-entity-card-clickable");
		notesLabel.getStyleClass().add("shale-metadata-muted");
		setCursor(Cursor.HAND);
		setFocusTraversable(true);
		applyMini();
	}

	private void wireEvents() {
		setOnMouseClicked(e -> {
			if (!removed && onOpen != null && organizationId != null) {
				onOpen.accept(organizationId);
			}
		});
		setOnKeyPressed(e->{if(!removed&&e.getTarget()==this&&onOpen!=null&&organizationId!=null&&(e.getCode()==KeyCode.ENTER||e.getCode()==KeyCode.SPACE)){onOpen.accept(organizationId);e.consume();}});
	}
	private Node typeChips(){return new ClassificationChipGroup(types.stream().sorted(java.util.Comparator.comparing(OrganizationCardType::primary).reversed().thenComparingInt(OrganizationCardType::sortOrder).thenComparingLong(OrganizationCardType::assignmentId)).map(t->new ClassificationChipGroup.Chip(t.label(),t.color(),"Organization Type",t.definitionId(),t.primary())).toList(),ClassificationChipGroup.Size.COMPACT);}
	private Node classificationRegion(){StackPane region=new StackPane(typeChips());region.getStyleClass().add("organization-card-classification-region");region.setAlignment(Pos.TOP_LEFT);return region;}
	private static Node methodCard(Label source,String kind,String actionText,Runnable action){String value=source.getText().replaceFirst("^[^:]+: ","");return new ContactMethodDisplayCard(value,kind,false,action==null?null:actionText,()->{try{action.run();}catch(RuntimeException ignored){}});}
	private GridPane contactSummary(){
		GridPane grid=new GridPane();grid.getStyleClass().add("organization-card-contact-summary");
		ColumnConstraints left=new ColumnConstraints();left.setPercentWidth(50);left.setHgrow(Priority.ALWAYS);
		ColumnConstraints right=new ColumnConstraints();right.setPercentWidth(50);right.setHgrow(Priority.ALWAYS);
		grid.getColumnConstraints().addAll(left,right);
		int index=0;
		for(var item:List.of(new SummaryValue(phoneLabel,"Phone"),new SummaryValue(emailLabel,"Email"),new SummaryValue(addressLabel,"Address"),new SummaryValue(websiteLabel,"Website"))){
			if(item.source().getText().endsWith("—"))continue;
			Node box=summaryBox(item.source(),item.kind());
			grid.add(box,index%2,index/2);GridPane.setHgrow(box,Priority.ALWAYS);index++;
			if(index==2)break;
		}
		return grid;
	}
	private Node firstCompactSummary() {
		for (var item : List.of(new SummaryValue(phoneLabel, "Phone"), new SummaryValue(emailLabel, "Email"),
				new SummaryValue(addressLabel, "Address"), new SummaryValue(websiteLabel, "Website"))) {
			if (!item.source().getText().endsWith("—")) return summaryBox(item.source(), item.kind());
		}
		return null;
	}
	private static Node summaryBox(Label source,String kind){
		String value=source.getText().replaceFirst("^[^:]+: ","");
		Label semantic=new Label(kind);semantic.getStyleClass().add("organization-card-summary-label");
		Label display=new Label(value);display.getStyleClass().add("organization-card-summary-value");display.setMinWidth(0);display.setMaxWidth(Double.MAX_VALUE);
		display.setTooltip(new Tooltip(value));display.setAccessibleText(kind+": "+value);
		display.setTextOverrun(OverrunStyle.ELLIPSIS);display.setWrapText(false);
		VBox box=new VBox(2,semantic,display);box.getStyleClass().add("organization-card-summary-box");box.setMinWidth(0);box.setMaxWidth(Double.MAX_VALUE);
		return box;
	}
	private record SummaryValue(Label source,String kind){}

	private Node buildAvatar(double radius) {
		Circle c = new Circle(radius);
		c.getStyleClass().add("organization-card-avatar");
		initialsLabel.getStyleClass().add("organization-card-avatar-initials");
		avatarHolder.getChildren().setAll(c,initialsLabel);
		return avatarHolder;
	}

	private void setVariantClasses(String... classes){getStyleClass().removeAll("organization-card-full","organization-card-compact","organization-card-mini","shale-entity-card-full","shale-entity-card-compact","shale-entity-card-inline","shale-entity-card-embedded");getStyleClass().addAll(classes);}

	private static String fallback(String value) {
		return value == null || value.isBlank() ? "—" : value;
	}
	private void updateAccessibleText(){String name=fallback(nameLabel.getText());String typeText=types.stream().map(OrganizationCardType::label).filter(java.util.Objects::nonNull).filter(v->!v.isBlank()).reduce((a,b)->a+", "+b).orElse("");setAccessibleText("Organization: "+name+(typeText.isBlank()?"":". Organization Types: "+typeText)+(removed?". Removed":""));}
	private static String initials(String value){String[] words=value.trim().split("\\s+");if(words.length==0||value.equals("—"))return "?";String first=words[0].substring(0,1);String last=words.length>1?words[words.length-1].substring(0,1):"";return (first+last).toUpperCase();}

	private static String joinNonBlank(String separator, String... values) {
		List<String> filtered = new ArrayList<>();
		for (String value : values) {
			if (value != null && !value.isBlank()) {
				filtered.add(value.trim());
			}
		}
		return String.join(separator, filtered);
	}
}
