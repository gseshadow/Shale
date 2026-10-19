package com.shale.ui.controller;

import static org.junit.jupiter.api.Assertions.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/** Protects declared field grouping and ordering, without JavaFX skin/pixel assumptions. */
class NewIntakePhoneLayoutTest {
    @Test void phoneControlsStayTogetherBeforeEmailForBothRoles()throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setNamespaceAware(true);
        try(var input=getClass().getResourceAsStream("/fxml/new-intake.fxml")){
            var document=factory.newDocumentBuilder().parse(input);
            var nodes=document.getElementsByTagName("*");
            for(String role:new String[]{"client","caller"}){
                Element phone=null,email=null;
                for(int i=0;i<nodes.getLength();i++){
                    var node=(Element)nodes.item(i);String id=node.getAttributeNS("http://javafx.com/fxml","id");
                    if(id.equals(role+"PhoneField"))phone=node;if(id.equals(role+"EmailField"))email=node;
                }
                assertNotNull(phone);assertNotNull(email);
                var group=(Element)phone.getParentNode();assertEquals("VBox",group.getTagName());
                int row=Integer.parseInt(group.getAttribute("GridPane.rowIndex"));
                assertTrue(row<Integer.parseInt(email.getAttribute("GridPane.rowIndex")),"phone group must precede Email");
                assertSame(group.getParentNode(),email.getParentNode());
                java.util.List<String> ids=new java.util.ArrayList<>();boolean guidance=false;
                for(Node child=group.getFirstChild();child!=null;child=child.getNextSibling())if(child instanceof Element element){
                    String id=element.getAttributeNS("http://javafx.com/fxml","id");if(!id.isBlank())ids.add(id);
                    if(element.getAttribute("text").contains("7-digit local")){guidance=true;assertEquals("true",element.getAttribute("wrapText"));assertEquals("shale-metadata-muted",element.getAttribute("styleClass"));}
                }
                assertEquals(java.util.List.of(role+"PhoneField",role+"PhoneExtensionField",role+"PhoneUnavailableCheckBox",role+"PhoneUnavailableReasonBox",role+"PhoneFeedbackLabel"),ids);
                assertTrue(guidance,"local and international guidance belongs in the same group");
            }
        }
    }
}
