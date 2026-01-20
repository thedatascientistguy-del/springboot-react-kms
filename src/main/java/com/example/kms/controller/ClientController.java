package com.example.kms.controller;

import com.example.kms.dto.ClientLoginRequest;
import com.example.kms.dto.ClientLoginResponse;
import com.example.kms.dto.ClientRegisterRequest;
import com.example.kms.dto.ServerStoreRequest;
import com.example.kms.dto.ServerStoreResponse;
import com.example.kms.model.Client;
import com.example.kms.model.DataType;
import com.example.kms.model.EncryptedData;
import com.example.kms.service.ClientService;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/clients")
public class ClientController {

    private final ClientService clientService;

    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    // ✅ Register new client
    @PostMapping("/register")
    public ResponseEntity<Client> registerClient(@Valid @RequestBody ClientRegisterRequest req) throws Exception {
        Client saved = clientService.registerClient(req);
        return ResponseEntity.ok(saved);
    }

    // ✅ Client login
    @PostMapping("/login")
    public ResponseEntity<ClientLoginResponse> login(@Valid @RequestBody ClientLoginRequest req) {
        Client client = clientService.loginClient(req);

        ClientLoginResponse resp = new ClientLoginResponse();
        resp.setId(client.getId());  // Long → String
        resp.setName(client.getName());
        resp.setEmail(client.getEmail());
        resp.setPhone(client.getPhone());
        resp.setServerPublicKey(client.getServerPublicKey());
        resp.setPublicKey(client.getPublicKey());

        return ResponseEntity.ok(resp);
    }

    // ✅ Store plaintext record (server-side encryption only)
    @PostMapping("/{email}/store-server")
    public ResponseEntity<ServerStoreResponse> storeServerSide(
            @PathVariable("email") String email,
            @Valid @RequestBody ServerStoreRequest req) throws Exception {
        ServerStoreResponse resp = clientService.storePlaintextServerSide(email, req);
        return ResponseEntity.ok(resp);
    }

    // ✅ Fetch records, with optional type filter
    @GetMapping("/{email}/records")
    public ResponseEntity<List<ServerStoreResponse>> getRecords(
            @PathVariable("email") String email,
            @RequestParam(value = "type", required = false) String type) {

        List<EncryptedData> list;

        if (type != null) {
            DataType dataType = DataType.valueOf(type.toUpperCase());
            list = clientService.fetchEncryptedRecordsByType(email, dataType);
        } else {
            list = clientService.fetchEncryptedRecords(email);
        }

        List<ServerStoreResponse> resp = list.stream().map(ed -> {
            ServerStoreResponse r = new ServerStoreResponse();
            r.setEncryptedDataId(ed.getId());
            r.setDekWrappedForClient(ed.getDekWrapped());
            r.setDekWrappedForRecovery(ed.getDekWrappedForRecovery());
            r.setIv(ed.getIv());
            r.setSalt(ed.getSalt());
            r.setServerPublicKey(ed.getClient().getServerPublicKey());
            return r;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(resp);
    }
}
