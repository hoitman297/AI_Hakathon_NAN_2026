package com.gameproject.backend.web;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gameproject.backend.dto.AvailableFruitResponse;
import com.gameproject.backend.dto.CropSummaryResponse;
import com.gameproject.backend.dto.FarmPlotResponse;
import com.gameproject.backend.dto.ForageRequest;
import com.gameproject.backend.dto.ForageResponse;
import com.gameproject.backend.dto.HarvestRequest;
import com.gameproject.backend.dto.PlantRequest;
import com.gameproject.backend.dto.SessionResponse;
import com.gameproject.backend.service.FarmService;
import com.gameproject.backend.service.ForageService;
import com.gameproject.backend.service.SessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sessions/{sessionId}")
@RequiredArgsConstructor
public class FarmController {

    private final FarmService farmService;
    private final ForageService forageService;
    private final SessionService sessionService;

    @GetMapping("/farm/crops")
    public ResponseEntity<List<CropSummaryResponse>> crops(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(farmService.listCrops());
    }

    @GetMapping("/farm/plots")
    public ResponseEntity<List<FarmPlotResponse>> plots(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(farmService.listPlots(sessionId));
    }

    @PostMapping("/farm/seeds/buy")
    public ResponseEntity<SessionResponse> buySeed(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody PlantRequest request) {
        farmService.buySeed(sessionId, request.cropId());
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/farm/plant")
    public ResponseEntity<SessionResponse> plant(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody PlantRequest request) {
        farmService.plant(sessionId, request.cropId());
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @PostMapping("/farm/harvest")
    public ResponseEntity<SessionResponse> harvest(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody HarvestRequest request) {
        farmService.harvest(sessionId, request.farmPlotId());
        return ResponseEntity.ok(sessionService.getSession(sessionId));
    }

    @GetMapping("/forage/species")
    public ResponseEntity<List<AvailableFruitResponse>> allSpecies(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(forageService.listAllSpecies());
    }

    @GetMapping("/forage/today")
    public ResponseEntity<List<AvailableFruitResponse>> availableToday(@PathVariable("sessionId") Long sessionId) {
        return ResponseEntity.ok(forageService.listAvailableToday(sessionId));
    }

    @PostMapping("/forage")
    public ResponseEntity<ForageResponse> forage(@PathVariable("sessionId") Long sessionId, @Valid @RequestBody ForageRequest request) {
        return ResponseEntity.ok(forageService.forage(sessionId, request.fruitId()));
    }
}
