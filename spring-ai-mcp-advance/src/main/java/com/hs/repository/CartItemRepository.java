package com.hs.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.hs.entity.CartItem;

public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

	CartItem findByProductId(String productId);

	void deleteByProductId(String productId);

}
