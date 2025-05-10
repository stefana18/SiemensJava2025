package com.siemens.internship;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import com.siemens.internship.service.ItemService;
import com.siemens.internship.validation.ItemValidator;
import jakarta.validation.ConstraintViolation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

import org.springframework.transaction.TransactionSystemException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@AutoConfigureMockMvc
class InternshipApplicationTests {

	@Autowired
	private ItemService itemService;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void testValidItemValidation() {
		Item validItem = new Item(null, "test", "desc", "PROCESSED", "stefana@test.com");
		Set<ConstraintViolation<Item>> errors = ItemValidator.validate(validItem);
		assertTrue(errors.isEmpty());
	}

	@Test
	void testInvalidEmailValidation() {
		Item invalidItem = new Item(null, "Test", "desc", "NOT_PROCESSED", "invalid-email");
		Set<ConstraintViolation<Item>> violations = ItemValidator.validate(invalidItem);
		assertFalse(violations.isEmpty());
	}

	@Test
	void testSaveSuccess() {
		Item item = new Item(null, "Test name", "desc", "NOT_PROCESSED", "test@email.com");
		Item saved = itemService.save(item);
		assertNotNull(saved.getId());
		assertEquals("Test name", saved.getName());
	}

	@Test
	void testSaveFailure() {
		Item invalid = new Item(null, "", "desc", "PROCESSED", "bad@format");
		assertThrows(TransactionSystemException.class, () -> itemService.save(invalid));
	}

	@Test
	void testFindById() {
		Item item = new Item(null, "To Find", "desc", "PROCESSED", "email@test.com");
		Item saved = itemService.save(item);
		Optional<Item> result = itemService.findById(saved.getId());
		assertTrue(result.isPresent());
		assertEquals("To Find", result.get().getName());
	}

	@Test
	void testDeleteById() {
		Item item = new Item(null, "To Delete", "desc", "NOT_PROCESSED", "email@test.com");
		Item saved = itemService.save(item);
		itemService.deleteById(saved.getId());
		Optional<Item> deleted = itemService.findById(saved.getId());
		assertTrue(deleted.isEmpty());
	}

	@Test
	void testFindAllIncludesSavedItem() {
		Item item = new Item(null, "Find All", "desc", "PROCESSED", "email@test.com");
		itemService.save(item);
		List<Item> allItems = itemService.findAll();
		assertTrue(allItems.stream().anyMatch(i -> i.getEmail().equals("email@test.com")));
	}

	@Test
	void testProcessItemsAsyncProcessesAllItems() throws Exception {
		// Save some test items
		Item item1 = itemService.save(new Item(null, "Item1", "desc1", "NOT_PROCESSED", "i1@test.com"));
		Item item2 = itemService.save(new Item(null, "Item2", "desc2", "NOT_PROCESSED", "i2@test.com"));

		// Trigger processing
		CompletableFuture<List<Item>> future = itemService.processItemsAsync();
		List<Item> processed = future.get();

		// Validate both items are in the result and status updated
		assertNotNull(processed);
		assertFalse(processed.isEmpty());
		assertTrue(processed.stream().anyMatch(i -> i.getId().equals(item1.getId()) && "PROCESSED".equals(i.getStatus())));
		assertTrue(processed.stream().anyMatch(i -> i.getId().equals(item2.getId()) && "PROCESSED".equals(i.getStatus())));
	}

	@Test
	void testProcessItemsAsyncHandlesMissingItem() throws Exception {
		Item item = itemService.save(new Item(null, "Gone", "desc", "Pending", "gone@test.com"));
		Long id = item.getId();
		itemService.deleteById(id);

		CompletableFuture<List<Item>> future = itemService.processItemsAsync();
		List<Item> processed = future.get();
		assertTrue(processed.stream().noneMatch(i -> i.getId().equals(id)));
	}

	@Test
	void testGetAllItemsAPI() throws Exception {
		mockMvc.perform(get("/api/items"))
				.andExpect(status().isOk());
	}

	@Test
	void testCreateItemAPI() throws Exception {
		Item item = new Item(null, "New", "desc", "PROCESSED", "api@test.com");
		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email", is("api@test.com")));
	}

	@Test
	void testCreateItemAPI_withInvalidData() throws Exception {
		Item item = new Item(null, "", "desc", "Pending", "wrong-email");
		mockMvc.perform(post("/api/items")
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isBadRequest());
	}

	@Test
	void testGetItemByIdAPI() throws Exception {
		Item item = itemService.save(new Item(null, "FromAPI", "desc", "NOT_PROCESSED", "get@test.com"));
		mockMvc.perform(get("/api/items/" + item.getId()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("FromAPI")));
	}

	@Test
	void testDeleteItemAPI() throws Exception {
		Item item = itemService.save(new Item(null, "ToDeleteAPI", "desc", "NOT_PROCESSED", "delete@test.com"));
		mockMvc.perform(delete("/api/items/" + item.getId()))
				.andExpect(status().isNoContent());
	}

	@Test
	void testUpdateItemAPI() throws Exception {
		Item item = itemService.save(new Item(null, "BeforeUpdate", "desc", "Pending", "update@test.com"));
		item.setName("AfterUpdate");
		mockMvc.perform(put("/api/items/" + item.getId())
						.contentType(MediaType.APPLICATION_JSON)
						.content(objectMapper.writeValueAsString(item)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name", is("AfterUpdate")));
	}

	@Test
	void testProcessEndpointReturnsProcessedItems() throws Exception {
		Item item = itemService.save(new Item(null, "ToProcess", "desc", "Pending", "to@process.com"));
		mockMvc.perform(get("/api/items/process"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[*].status", everyItem(is("PROCESSED"))));
	}
}
