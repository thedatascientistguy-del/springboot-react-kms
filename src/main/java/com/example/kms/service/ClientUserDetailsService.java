package com.example.kms.service;

import com.example.kms.model.Client;
import com.example.kms.repository.ClientRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * Loads UserDetails by emailHash so the JWT principal is always the hash,
 * never the plaintext email.
 */
@Service
public class ClientUserDetailsService implements UserDetailsService {

    private final ClientRepository clientRepository;

    public ClientUserDetailsService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String emailHash) throws UsernameNotFoundException {
        Client client = clientRepository.findByEmailHash(emailHash)
                .orElseThrow(() -> new UsernameNotFoundException("Client not found for emailHash: " + emailHash));

        return User.builder()
                .username(client.getEmailHash())
                .password(client.getPassword())
                .authorities(Collections.emptyList())
                .build();
    }
}
