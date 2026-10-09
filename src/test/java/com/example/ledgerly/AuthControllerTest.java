package com.example.ledgerly;

import com.example.ledgerly.domain.entity.User;
import com.example.ledgerly.domain.model.Role;
import com.example.ledgerly.domain.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.save(new User("max", passwordEncoder.encode("Geheim123!"),
                "Max", "Muster", Role.EMPLOYEE, null));

        User locked = new User("lisa", passwordEncoder.encode("Geheim123!"),
                "Lisa", "Huber", Role.EMPLOYEE, null);
        locked.disable();
        userRepository.save(locked);
    }

    private ResultActions login(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"));
    }

    @Test
    void validLoginReturns200() throws Exception {
        login("max", "Geheim123!").andExpect(status().isOk());
    }

    @Test
    void passwordIsStoredAsBcryptHash() {
        String hash = userRepository.findByUsername("max").orElseThrow().getPasswordHash();
        assertTrue(hash.startsWith("$2"));
        assertNotEquals("Geheim123!", hash);
    }

    @Test
    void unknownUserWrongPasswordAndLockedAccountLookIdentical() throws Exception {
        String unknownUser = login("nobody", "Geheim123!")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String wrongPassword = login("max", "falsch")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String lockedAccount = login("lisa", "Geheim123!")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertEquals(unknownUser, wrongPassword);
        assertEquals(unknownUser, lockedAccount);
    }
}