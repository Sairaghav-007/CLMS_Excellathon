package com.example.clms;

import com.example.clms.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ClmsApplicationTests {

	@Autowired
	private UserRepository userRepository;

	@Test
	void contextLoads() {
		System.out.println("=== USER DATABASE DUMP ===");
		userRepository.findAll().forEach(user -> {
			System.out.printf("ID: %d | Email: %s | Role: %s | Hashed Password: %s | Active: %b%n",
					user.getId(), user.getEmail(), user.getRole(), user.getPassword(), user.isActive());
		});
		System.out.println("==========================");
	}

}
