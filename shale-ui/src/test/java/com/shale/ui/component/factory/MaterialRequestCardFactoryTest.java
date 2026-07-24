package com.shale.ui.component.factory;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class MaterialRequestCardFactoryTest {
    private static final Path FACTORY = Path.of("src/main/java/com/shale/ui/component/factory/MaterialRequestCardFactory.java");
    private static final Path TASK_CARD = Path.of("src/main/java/com/shale/ui/component/TaskCard.java");
    private static final Path DAO = Path.of("../shale-data/src/main/java/com/shale/data/dao/MaterialRequestDao.java");

    @Test
    void listCardUsesRoundedUrgencySurfaceInsteadOfMaterialTypeWash() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("CARD_RADIUS = DueProximityStyles.CARD_RADIUS"));
        assertTrue(source.contains("-fx-background-radius: " + "\" + CARD_RADIUS"));
        assertTrue(source.contains("-fx-border-radius: " + "\" + CARD_RADIUS"));
        assertTrue(source.contains("-fx-background-radius: " + "\" + CARD_RADIUS + \" 0 0 \" + CARD_RADIUS"),
                "Accent rail must carry matching left-side rounded corners instead of a square rail over the card.");
        assertTrue(source.contains("DueProximityStyles.presentation(request.expectedResponseDate(), null, false)"));
        assertTrue(source.contains("materialTypeRailColor = ColorUtil.toCssBackgroundColor(request.materialTypeColor())"));
        assertTrue(source.contains("rail.setStyle(\"-fx-background-color: \" + materialTypeRailColor"));
        assertTrue(source.contains("urgency.washCss()"));
        assertTrue(source.contains("setOnMouseEntered"));
        assertTrue(source.contains("DueProximityStyles.presentation(request.expectedResponseDate(), null, true)"),
                "Hover must recompute the urgency wash instead of installing a square hover fill.");
        assertTrue(source.contains("installRoundedClip(card)"));
        assertTrue(source.contains("clip.setArcWidth(CARD_RADIUS_PX * 2.0)"));
        assertFalse(source.contains("card.getStyleClass().add(\"shale-entity-card-clickable\")"),
                "The shared clickable hover selector supplies a square background when used without the base card class.");
        assertTrue(source.contains("materialTypePill(request.materialTypeName(), request.materialTypeColor())"));
        assertFalse(source.contains("ColorUtil.toCssRgba(request.materialTypeColor(), 0.08)"));
        assertFalse(source.contains("String accent = ColorUtil.toCssBackgroundColor(request.materialTypeColor())"));
        assertFalse(source.contains("rail.setStyle(\"-fx-background-color: \" + urgency"));
    }

    @Test
    void dueProximityThresholdsAreSharedWithTaskCardsAndClockDeterministic() throws Exception {
        String task = Files.readString(TASK_CARD);
        assertTrue(task.contains("DueProximityStyles.accentColor(dueAt, completedAt)"));
        Clock fixed = Clock.fixed(Instant.parse("2026-07-23T12:00:00Z"), ZoneId.of("UTC"));
        LocalDateTime now = LocalDateTime.now(fixed);
        assertEquals(DueProximityStyles.OVERDUE_COLOR, DueProximityStyles.accentColor(now.minusSeconds(1), null, fixed));
        assertEquals(DueProximityStyles.DUE_WITHIN_ONE_DAY_COLOR, DueProximityStyles.accentColor(now.plusDays(1), null, fixed));
        assertEquals(DueProximityStyles.DUE_WITHIN_ONE_WEEK_COLOR, DueProximityStyles.accentColor(now.plusWeeks(1), null, fixed));
        assertEquals(DueProximityStyles.DUE_WITHIN_TWO_WEEKS_COLOR, DueProximityStyles.accentColor(now.plusWeeks(2), null, fixed));
        assertNull(DueProximityStyles.accentColor(now.plusWeeks(2).plusSeconds(1), null, fixed));
        assertNull(DueProximityStyles.accentColor(null, null, fixed));
        assertEquals(DueProximityStyles.NEUTRAL_RAIL_COLOR, DueProximityStyles.presentation(null, null, false, fixed).dueColorCss());
        assertTrue(DueProximityStyles.presentation(now.minusSeconds(1), null, false, fixed).washCss().startsWith("linear-gradient(to right"));
        assertTrue(DueProximityStyles.presentation(null, null, false, fixed).washCss().startsWith("linear-gradient(to right"));
        String neutralWash = DueProximityStyles.presentation(now.plusWeeks(4), null, false, fixed).washCss();
        assertTrue(neutralWash.contains("rgba(203,213,225,0.220) 0%"), neutralWash);
        assertTrue(neutralWash.contains("rgba(203,213,225,0.130) 24%"), neutralWash);
        assertTrue(neutralWash.contains(DueProximityStyles.DEFAULT_SURFACE + " 62%"), neutralWash);
        assertFalse(DueProximityStyles.presentation(now.plusWeeks(4), null, false, fixed).urgent());
    }

    @Test
    void listCardUsesSharedMiniEntityFactoriesAndOmitsMissingValues() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("ContactCardFactory.Variant.MINI"));
        assertTrue(source.contains("OrganizationCardFactory.Variant.MINI"));
        assertTrue(source.contains("UserCardFactory.Variant.MINI"));
        assertTrue(source.contains("r.requestedFromContactId() != null"));
        assertTrue(source.contains("r.requestedFromOrganizationId() != null"));
        assertTrue(source.contains("if (has(r.requestedFromText())) return valueLabel(r.requestedFromText())"));
        assertTrue(source.contains("addEntityFact(facts, \"Requested From\", requestedFromNode(request))"));
        assertTrue(source.contains("addEntityFact(facts, \"Requested By\", userNode(request.requestedByUserId(), request.requestedByDisplayName(), request.requestedByUserColor()))"));
        assertTrue(source.contains("addEntityFact(facts, \"Assigned To\", userNode(request.assignedToUserId(), request.assignedToDisplayName(), request.assignedToUserColor()))"));
        assertTrue(source.contains("if (node == null) return;"));
        assertFalse(source.contains("MaterialRequestService"));
        assertFalse(source.contains("MaterialRequestDao"));
    }

    @Test
    void miniCardsRemainCompactLeftAlignedAndKeepCanonicalColors() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("fact.setFillWidth(false)"));
        assertTrue(source.contains("facts.setAlignment(Pos.TOP_LEFT)"));
        assertTrue(source.contains("fact.setAlignment(Pos.TOP_LEFT)"));
        assertTrue(source.contains("fact.setFillWidth(false)"));
        assertTrue(source.contains("keepMiniCardCompact(node)"));
        assertTrue(source.contains("region.setMaxWidth(Region.USE_PREF_SIZE)"));
        assertTrue(source.contains("HBox.setHgrow(node, Priority.NEVER)"));
        assertTrue(source.contains("GridPane.setFillWidth(node, false)"));
        assertTrue(source.contains("new UserCardFactory.UserCardModel(userId, displayName, color, null)"));
        assertTrue(source.contains("request.requestedByUserColor()"));
        assertTrue(source.contains("request.assignedToUserColor()"));
        assertFalse(source.contains("setStyle(\"-fx-background-color: transparent;\")"));
    }

    @Test
    void listCardUsesNaturalContentHeightWithoutVerticalSpacerOrFixedCardHeight() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("card.setMaxHeight(Region.USE_PREF_SIZE)"),
                "The list card must opt out of parent vertical fill while still using its computed preferred height.");
        assertFalse(source.contains("card.setPrefHeight("),
                "Do not pin request cards to a brittle preferred height.");
        assertFalse(source.contains("card.setMinHeight("),
                "Do not keep an unnecessary fixed minimum card height.");
        assertFalse(source.contains("body.setPrefHeight("));
        assertFalse(source.contains("body.setMinHeight("));
        assertFalse(source.contains("body.setMaxHeight("));
        assertFalse(source.contains("VBox.setVgrow(facts, Priority.ALWAYS)"),
                "Entity facts must not consume spare vertical space to push the date row down.");
        assertFalse(source.contains("VBox.setVgrow(facts, Priority.ALWAYS)"),
                "Date facts must follow the entity facts rather than be anchored to the bottom.");
        assertFalse(source.contains("new Region(); VBox.setVgrow"),
                "No structural vertical spacer should be inserted in the card body.");
        assertTrue(source.contains("VBox body = new VBox(7)"),
                "The final gap between the entity section and date row is the card body's 7px design-system spacing.");
        assertTrue(source.contains("body.setPadding(new Insets(10, 12, 10, 12))"),
                "Padding should remain modest instead of hiding a fixed height problem.");
        assertTrue(source.contains("body.getChildren().add(facts)"),
                "The date row must remain present and visible after entity facts.");
    }
    @Test
    void factsUseSingleCompactResponsiveFlowWithoutResizeRebuilding() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("FlowPane facts = new FlowPane(18, 7)"));
        assertTrue(source.contains("material-request-card__facts"));
        assertTrue(source.indexOf("addEntityFact(facts, \"Requested From\"")
                < source.indexOf("addTextFact(facts, \"Requested\""),
                "Entity facts should keep logical order ahead of date facts.");
        assertTrue(source.contains("fact.setMinWidth(Region.USE_PREF_SIZE)"));
        assertTrue(source.contains("fact.setMaxWidth(Region.USE_PREF_SIZE)"));
        assertTrue(source.contains("facts.setColumnHalignment(javafx.geometry.HPos.LEFT)"));
        assertFalse(source.contains("widthProperty().addListener"),
                "FlowPane should perform responsive wrapping without card width listeners that rebuild content.");
        assertFalse(source.contains("getChildren().clear()"),
                "Responsive reflow should not repeatedly reconstruct fact nodes.");
        assertFalse(source.contains("setPrefHeight("));
    }


    @Test
    void roundedClipTracksComputedCardDimensionsWithoutObsoleteFixedSize() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("clip.widthProperty().bind(card.widthProperty())"));
        assertTrue(source.contains("clip.heightProperty().bind(card.heightProperty())"));
        assertFalse(source.contains("clip.setWidth("));
        assertFalse(source.contains("clip.setHeight("));
    }


    @Test
    void requestsListAddsExternalInsetsWithoutWrappingCardsOrDoublingSpacing() throws Exception {
        String controller = Files.readString(Path.of("src/main/java/com/shale/ui/controller/CaseMaterialsTabController.java"));
        assertTrue(controller.contains("REQUEST_LIST_INSETS = new Insets(8)"),
                "Requests list should own the external breathing room around first, last, and intermediate cards.");
        assertTrue(controller.contains("list=new VBox(10); list.setPadding(REQUEST_LIST_INSETS)"),
                "Card separation should come from the list VBox spacing plus one transparent list inset, not per-card doubled margins.");
        assertTrue(controller.contains("requestCardFactory.create(r, MaterialRequestCardFactory.Variant.LIST)"),
                "The production request card should remain the only card surface added to the list.");
        assertFalse(controller.contains("new StackPane(requestCardFactory.create"),
                "Do not add an opaque or nested card wrapper around request cards.");
        assertFalse(controller.contains("VBox.setMargin(requestCardFactory.create"),
                "List padding avoids doubled gaps between multiple request cards.");
    }

    @Test
    void summaryQueryRemainsSingleTenantScopedJoinWithoutPerCardLoads() throws Exception {
        String dao = Files.readString(DAO);
        assertTrue(dao.contains("rbu.Color AS RequestedByUserColor"));
        assertTrue(dao.contains("au.Color AS AssignedToUserColor"));
        assertTrue(dao.contains("JOIN dbo.Users rbu ON rbu.Id=mr.RequestedByUserId AND rbu.ShaleClientId=mr.ShaleClientId"));
        assertTrue(dao.contains("LEFT JOIN dbo.Users au ON au.Id=mr.AssignedToUserId AND au.ShaleClientId=mr.ShaleClientId"));
        assertTrue(dao.contains("LEFT JOIN dbo.Contacts ct ON ct.Id=mr.RequestedFromContactId AND ct.ShaleClientId=mr.ShaleClientId"));
        assertTrue(dao.contains("LEFT JOIN dbo.Organizations org ON org.Id=mr.RequestedFromOrganizationId AND org.ShaleClientId=mr.ShaleClientId"));
        assertTrue(dao.contains("WHERE mr.ShaleClientId=? AND mr.CaseId=? AND mr.IsDeleted=0"));
        assertFalse(dao.contains("listMaterialRequests(long caseId, int tenant) {\n        for"));
    }

    @Test
    void regressionFieldsAndNoSaveImplementationRemain() throws Exception {
        String source = Files.readString(FACTORY);
        assertTrue(source.contains("title.setWrapText(true)"));
        assertTrue(source.contains("StatusIndicatorFactory.createStatusPill(nvl(name, \"Material\"), color, StatusIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(source.contains("StatusIndicatorFactory.createStatusPill(nvl(status, \"Unknown\"), nvl(configuredColor, NEUTRAL_STATUS_COLOR), StatusIndicatorFactory.PillSize.COMPACT)"));
        assertTrue(source.contains("Requested"));
        assertTrue(source.contains("Due"));
        assertTrue(source.contains("Next Follow-up"));
        assertTrue(source.contains("Overdue since"));
        assertTrue(source.contains("Follow-up due"));
        assertFalse(source.contains("save"));
        assertFalse(source.contains("insert"));
        assertFalse(source.contains("update"));
    }
}
