package com.hs.tool;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

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

}
