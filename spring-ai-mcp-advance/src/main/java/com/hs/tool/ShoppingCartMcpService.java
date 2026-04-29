package com.hs.tool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.hs.dto.SuggestItemsModels.ItemSuggestion;
import com.hs.dto.SuggestItemsModels.SuggestItemsResponse;
import com.hs.entity.CartItem;
import com.hs.repository.CartItemRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShoppingCartMcpService {

	private final CartItemRepository cartItemRepository;

	private static final Map<String, Double> PRODUCTS = Map.of(
			"iPhone", 79999.0,
			"MacBook Air", 129999.0,
			"Boat Airdopes", 1999.0);

	@Transactional
	public String addToCart(String productName, int quantity) {
		if (!PRODUCTS.containsKey(productName)) {
			log.warn("Product not found: {}. Available: {}", productName, PRODUCTS.keySet());
			return "Product not found. Available: " + PRODUCTS.keySet();
		}

		double unitPrice = PRODUCTS.get(productName);
		CartItem cartItem = cartItemRepository.findByProductId(productName);

		if (cartItem == null) {
			cartItem = new CartItem();
			cartItem.setProductId(productName);
			cartItem.setProductName(productName);
			cartItem.setQuantity(quantity);
			log.info("New cart item created: {} x{} @ {}/unit", productName, quantity, unitPrice);
		} else {
			log.info("Updating existing cart item: {} (qty {} -> {})", productName, cartItem.getQuantity(), cartItem.getQuantity() + quantity);
			cartItem.setQuantity(cartItem.getQuantity() + quantity);
		}

		cartItem.setPrice(cartItem.getQuantity() * unitPrice);
		cartItemRepository.save(cartItem);
		return quantity + " " + productName + " added to cart. Total price: " + cartItem.getPrice();
	}

	@Transactional
	public String removeCart(String productName) {
		log.info("Removing product from cart: {}", productName);
		cartItemRepository.deleteByProductId(productName);
		return productName + " removed from cart.";
	}

	public List<CartItem> getCarts() {
		List<CartItem> items = cartItemRepository.findAll();
		log.info("Cart has {} item(s)", items.size());
		return items;
	}

	public double getCartTotal() {
		double total = cartItemRepository.findAll().stream()
				.mapToDouble(CartItem::getPrice)
				.sum();
		log.info("Cart total calculated: {}", total);
		return total;
	}

	public String ping() {
		return "pong";
	}

	/**
	 * Rank catalog items for the given query. Prefers products not already in the cart.
	 * {@code limit} is clamped to 1–20.
	 */
	public SuggestItemsResponse suggestItems(String query, int limit) {
		int safeLimit = Math.clamp(limit, 1, 20);
		String q = query == null ? "" : query.trim();
		String qLower = q.toLowerCase();

		Set<String> inCartIds = cartItemRepository.findAll().stream()
				.map(CartItem::getProductId)
				.collect(Collectors.toSet());

		List<String> names = new ArrayList<>(PRODUCTS.keySet());
		List<String> matched = names.stream()
				.filter(n -> qLower.isEmpty() || n.toLowerCase().contains(qLower))
				.sorted(suggestionComparator(qLower, inCartIds))
				.limit(safeLimit)
				.toList();

		String note = null;
		if (!qLower.isEmpty() && matched.isEmpty()) {
			matched = names.stream()
					.sorted(suggestionComparator("", inCartIds))
					.limit(safeLimit)
					.toList();
			note = "No product name contained that text; showing general suggestions from the catalog.";
		}

		List<ItemSuggestion> suggestions = matched.stream()
				.map(name -> toSuggestion(name, inCartIds, qLower))
				.toList();

		log.info("[suggestItems] query='{}' limit={} -> {} hit(s)", q, safeLimit, suggestions.size());
		return new SuggestItemsResponse(suggestions, q, safeLimit, note);
	}

	private static Comparator<String> suggestionComparator(String qLower, Set<String> inCartIds) {
		return Comparator
				.comparing((String name) -> matchRank(name, qLower))
				.thenComparing(name -> inCartIds.contains(name))
				.thenComparing(name -> name.toLowerCase());
	}

	private static int matchRank(String name, String qLower) {
		if (qLower.isEmpty()) {
			return 0;
		}
		int idx = name.toLowerCase().indexOf(qLower);
		return idx < 0 ? Integer.MAX_VALUE : idx;
	}

	private ItemSuggestion toSuggestion(String name, Set<String> inCartIds, String qLower) {
		double unitPrice = PRODUCTS.get(name);
		boolean inCart = inCartIds.contains(name);
		String reason;
		if (qLower.isEmpty()) {
			reason = inCart ? "Already in cart; user may want to add more." : "Not in cart yet; good candidate to suggest.";
		} else if (name.toLowerCase().contains(qLower)) {
			reason = inCart ? "Matches search and is already in cart." : "Matches search and is not in cart.";
		} else {
			reason = "Catalog option (broad suggestion).";
		}
		return new ItemSuggestion(name, unitPrice, inCart, reason);
	}

}
