package com.siemens.internship.service;

import com.siemens.internship.model.Item;
import com.siemens.internship.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Service
public class ItemService {
    @Autowired
    private ItemRepository itemRepository;
    private static final ExecutorService executor = Executors.newFixedThreadPool(10);

    public List<Item> findAll() {
        return itemRepository.findAll();
    }

    public Optional<Item> findById(Long id) {
        return itemRepository.findById(id);
    }

    public Item save(Item item) {
        return itemRepository.save(item);
    }

    public void deleteById(Long id) {
        itemRepository.deleteById(id);
    }


    /**
     * Your Tasks
     * Identify all concurrency and asynchronous programming issues in the code
     * Fix the implementation to ensure:
     * All items are properly processed before the CompletableFuture completes
     * Thread safety for all shared state
     * Proper error handling and propagation
     * Efficient use of system resources
     * Correct use of Spring's @Async annotation
     * Add appropriate comments explaining your changes and why they fix the issues
     * Write a brief explanation of what was wrong with the original implementation
     *
     * Hints
     * Consider how CompletableFuture composition can help coordinate multiple async operations
     * Think about appropriate thread-safe collections
     * Examine how errors are handled and propagated
     * Consider the interaction between Spring's @Async and CompletableFuture
     */

    /**
     * Asynchronously processes all items in the system
     * This method retrieves all item Ids from the database,
     * - For each id, it creates an asynchronous task to retrieve the item, mark it as processed and save it back to the database
     * - Waits for all the tasks to be complete
     * - Collects and returns all successfully processed items
     * @return CompletableFuture containing list of processed items
     */
    @Async
    public CompletableFuture<List<Item>> processItemsAsync() {

        List<Long> itemIds = itemRepository.findAllIds();

        // Create a CompletableFuture for each item-processing task
        List<CompletableFuture<Item>> futures = itemIds.stream()
                .map(id -> CompletableFuture.supplyAsync(() -> {
                    try {
                        // Optional: simulate delay to mock processing time
                        Thread.sleep(100);

                        // Retrieve and update an item
                        Optional<Item> optionalItem = itemRepository.findById(id);
                        if (optionalItem.isPresent()) {
                            Item item = optionalItem.get();
                            item.setStatus("PROCESSED");
                            return itemRepository.save(item);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt(); // Restore interrupted flag
                        System.err.println(e.getMessage());
                    } catch (Exception e) {
                        System.err.println(e.getMessage());
                    }
                    return null;
                }, executor)).toList();

        // Wait for all async operations to complete
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenApply(voided ->
                        // Filter out nulls and collect only successfully processed items
                futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).collect(Collectors.toList()));
    }

    /**
     * What was wrong with the first implementation:
     * - The method returned a mutable list(processedItems) immediately, before any item was actually processed
     * - The use of shared mutable state(processedItems and processedCount) was unsafe in a multithreaded context
     * - Asynchronous tasks were created but not tracked, so it was impossible to know when they had all completed
     */

}

