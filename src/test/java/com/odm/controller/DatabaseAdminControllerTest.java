package com.odm.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.odm.db.DatabaseIndexManager;
import com.odm.dto.IndexStatus;
import com.odm.dto.IndexStatus.State;

@WebMvcTest(DatabaseAdminController.class)
class DatabaseAdminControllerTest {

	private static final String BASE = "/api/v1/admin/db/indexes";

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DatabaseIndexManager databaseIndexManager;

	@Test
	void getReturnsStatus() throws Exception {
		when(databaseIndexManager.status()).thenReturn(List.of(
				new IndexStatus("idx_products_lookup", "products", "source(100)", State.MISSING, null)));

		mockMvc.perform(get(BASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].name").value("idx_products_lookup"))
				.andExpect(jsonPath("$[0].state").value("MISSING"));
	}

	@Test
	void postCreatesMissingIndexes() throws Exception {
		when(databaseIndexManager.ensureIndexes()).thenReturn(List.of(
				new IndexStatus("idx_products_lookup", "products", "source(100)", State.CREATED, null)));

		mockMvc.perform(post(BASE))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].state").value("CREATED"));

		verify(databaseIndexManager).ensureIndexes();
	}

	@Test
	void databaseDownReturns503() throws Exception {
		when(databaseIndexManager.status()).thenThrow(new DataAccessResourceFailureException("down"));

		mockMvc.perform(get(BASE)).andExpect(status().isServiceUnavailable());
	}

}
