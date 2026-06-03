package com.shale.data.service.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.shale.core.dto.CaseDetailDto;
import com.shale.core.dto.CaseOverviewDto;
import com.shale.core.dto.CaseUpdateDto;
import com.shale.core.service.CaseServicePort.AddCaseNoteCommand;
import com.shale.core.service.CaseServicePort.UpdateCaseDetailsCommand;
import com.shale.data.dao.CaseDao;

class CaseServiceAdapterTest {

	@Test
	void listCaseUpdatesDelegatesToGateway() {
		CaseUpdateDto update = new CaseUpdateDto(11, 99, "note", LocalDateTime.now(), null, 5, "Author");
		FakeCaseGateway gateway = new FakeCaseGateway(List.of(update));
		CaseServiceAdapter adapter = new CaseServiceAdapter(gateway);

		List<CaseUpdateDto> actual = adapter.listCaseUpdates(99, 42);

		assertEquals(99, gateway.lastCaseUpdatesCaseId);
		assertEquals(List.of(update), actual);
	}

	@Test
	void unsupportedWriteMethodsKeepClearTodoText() {
		CaseServiceAdapter adapter = new CaseServiceAdapter(new FakeCaseGateway(List.of()));

		UnsupportedOperationException noteError = assertThrows(UnsupportedOperationException.class,
				() -> adapter.addCaseNote(new AddCaseNoteCommand(99, 42, 5, "note")));
		UnsupportedOperationException updateError = assertThrows(UnsupportedOperationException.class,
				() -> adapter.updateCaseDetails(new UpdateCaseDetailsCommand(99, 42, 5, "name", "description", 1, 2)));

		assertTrue(noteError.getMessage().contains("TODO: CaseServiceAdapter.addCaseNote"));
		assertTrue(updateError.getMessage().contains("TODO: CaseServiceAdapter.updateCaseDetails"));
	}

	private static final class FakeCaseGateway implements CaseServiceAdapter.CaseGateway {
		private final List<CaseUpdateDto> caseUpdates;
		private long lastCaseUpdatesCaseId;

		private FakeCaseGateway(List<CaseUpdateDto> caseUpdates) {
			this.caseUpdates = caseUpdates;
		}

		@Override
		public CaseDetailDto getDetail(long caseId) {
			return null;
		}

		@Override
		public CaseOverviewDto getOverview(long caseId) {
			return null;
		}

		@Override
		public List<CaseDao.CaseRow> searchCasesByName(String query) {
			return List.of();
		}

		@Override
		public List<CaseUpdateDto> listCaseUpdates(long caseId) {
			lastCaseUpdatesCaseId = caseId;
			return caseUpdates;
		}
	}
}
