package br.com.adapter.in.web.controller;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Endpoint público e sem dados, para o Render (e o app) saberem se a API está no ar. */
@RestController
class SaudeController {

    @GetMapping("/api/v1/saude")
    Map<String, String> saude() {
        return Map.of("status", "ok");
    }
}
