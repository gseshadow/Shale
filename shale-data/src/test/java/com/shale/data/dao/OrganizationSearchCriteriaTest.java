package com.shale.data.dao;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
final class OrganizationSearchCriteriaTest {
 @Test void normalizesAndDefensivelyCopiesFilters(){var ids=new ArrayList<>(List.of(9,3,9));var c=new OrganizationDao.OrganizationSearchCriteria(7,"  Acme  ",ids,null,null,100,100);ids.clear();assertEquals("Acme",c.searchText());assertEquals(List.of(9,3),c.organizationTypeIds());assertEquals(OrganizationDao.DirectorySort.NAME,c.sortField());assertThrows(UnsupportedOperationException.class,()->c.organizationTypeIds().add(5));}
 @Test void blankSearchAndPagingValidation(){assertEquals("",new OrganizationDao.OrganizationSearchCriteria(7," \t ",null,null,null,0,1).searchText());assertThrows(IllegalArgumentException.class,()->new OrganizationDao.OrganizationSearchCriteria(0,"",List.of(),null,null,0,10));assertThrows(IllegalArgumentException.class,()->new OrganizationDao.OrganizationSearchCriteria(7,"",List.of(),null,null,-1,10));assertThrows(IllegalArgumentException.class,()->new OrganizationDao.OrganizationSearchCriteria(7,"",List.of(),null,null,0,101));}
 @Test void phoneFormattingIsEquivalent(){assertEquals("5051234567",ContactDao.normalizePhoneDigits("505 123 4567"));assertEquals("5051234567",ContactDao.normalizePhoneDigits("(505) 123-4567"));assertEquals("",ContactDao.normalizePhoneDigits("()+ -"));}
}
